package dev.icebear.yac.actions.undo

import dev.icebear.yac.TemporaryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class DiskSnapshotTest {
    private fun text(bytes: ByteArray?): String? = bytes?.let { String(it, Charsets.UTF_8) }

    private fun changes(before: DiskSnapshot, after: DiskSnapshot): Map<String, Pair<String?, String?>> =
        before.changesTo(after).mapValues { (_, change) -> text(change.before) to text(change.after) }

    @Test
    fun capturesSidecarsGitignoreAndSourcesButNotTheLockOrTheCache() {
        val tree = TemporaryTree.create()
            .file(".yac/a.php.yac", "a")
            .file(".yac/sub/b.php.yac", "b")
            .file(".yac/.lock", "lock")
            .file(".yac/.cache/index.json", "cache")
            .file(".yac/.cache/deep/x", "cache")
            .file(".yac/.cached", "kept")
            .file(".gitignore", "/.yac/\n")
            .file("src/A.php", "<?php")
            .file("src/Other.php", "<?php other")

        val snapshot = DiskSnapshot.capture(tree.path, listOf("src/A.php", "src/Missing.php"), true)

        assertEquals(
            mapOf(
                ".gitignore" to "/.yac/\n",
                "src/A.php" to "<?php",
                "src/Missing.php" to null,
                ".yac/.cached" to "kept",
                ".yac/a.php.yac" to "a",
                ".yac/sub/b.php.yac" to "b",
            ),
            snapshot.files.mapValues { text(it.value) },
        )
    }

    @Test
    fun aNestedLockOrCacheNamedFileIsCapturedButYacTemporaryFilesAreNeverCaptured() {
        val tree = TemporaryTree.create()
            .file(".yac/.lock", "lock")
            .file(".yac/sub/.lock", "nested")
            .file(".yac/.yac-tmp-abc123", "temporary")
            .file(".yac/sub/.yac-tmp-def456", "temporary")
            .file(".yac/sub/.cache/x", "nested cache")

        assertEquals(
            mapOf(".gitignore" to null, ".yac/sub/.lock" to "nested", ".yac/sub/.cache/x" to "nested cache"),
            DiskSnapshot.capture(tree.path, emptyList(), true).files.mapValues { text(it.value) },
        )
    }

    @Test
    fun followsAYacDirectoryThatIsASymbolicLink() {
        val tree = TemporaryTree.create().file("elsewhere/a.php.yac", "a").file("elsewhere/sub/b.php.yac", "b").file("elsewhere/.cache/x", "cache").file("elsewhere/.lock", "lock")
        Files.createSymbolicLink(tree.path.resolve(".yac"), tree.path.resolve("elsewhere"))

        val snapshot = DiskSnapshot.capture(tree.path, emptyList(), true)

        assertEquals(mapOf(".gitignore" to null, ".yac/a.php.yac" to "a", ".yac/sub/b.php.yac" to "b"), snapshot.files.mapValues { text(it.value) })
        assertEquals(setOf(".yac", ".yac/sub"), snapshot.directories)
    }

    @Test
    fun recordsTheDirectoriesThatExistedOnlyBelowYacAndAboveTheCapturedPaths() {
        val tree = TemporaryTree.create().directory(".yac/empty").directory(".yac/.cache").file(".yac/sub/b.php.yac", "b").file("src/deep/A.php", "<?php").directory("unrelated")

        val snapshot = DiskSnapshot.capture(tree.path, listOf("src/deep/A.php", "lib/B.php"), true)

        assertEquals(setOf(".yac", ".yac/empty", ".yac/sub", "src", "src/deep"), snapshot.directories)
    }

    @Test
    fun withoutAYacDirectoryNoYacDirectoryIsRecorded() {
        val tree = TemporaryTree.create().directory("src")

        assertEquals(setOf("src"), DiskSnapshot.capture(tree.path, listOf("src/A.php"), true).directories)
    }

    @Test
    fun withoutTheYacTreeOnlyTheNamedFilesAndTheGitignoreAreCaptured() {
        val tree = TemporaryTree.create()
            .file(".yac/a.php.yac", "a")
            .file(".yac/b.php.yac", "b")
            .file(".yac/.gitignore", "inner")
            .file(".gitignore", "outer")
            .file("a.php", "<?php")

        val snapshot = DiskSnapshot.capture(tree.path, listOf("a.php", ".yac/a.php.yac", ".yac/.gitignore", ".yac/missing.yac"), false)

        assertEquals(
            mapOf(
                ".gitignore" to "outer",
                "a.php" to "<?php",
                ".yac/a.php.yac" to "a",
                ".yac/.gitignore" to "inner",
                ".yac/missing.yac" to null,
            ),
            snapshot.files.mapValues { text(it.value) },
        )
        assertEquals(setOf(".yac"), snapshot.directories)
    }

    @Test
    fun capturesAbsentFilesWithoutAYacDirectory() {
        val tree = TemporaryTree.create()

        assertEquals(mapOf(".gitignore" to null, "a.php" to null), DiskSnapshot.capture(tree.path, listOf("a.php"), true).files.mapValues { text(it.value) })
    }

    @Test
    fun aDirectoryInPlaceOfASourceCountsAsAbsent() {
        val tree = TemporaryTree.create().directory("src")

        assertEquals(mapOf(".gitignore" to null, "src" to null), DiskSnapshot.capture(tree.path, listOf("src"), true).files.mapValues { text(it.value) })
    }

    @Test
    fun changesAreOnlyFilesWhoseBytesDifferIncludingNewAndDeletedOnes() {
        val tree = TemporaryTree.create()
            .file(".yac/a.php.yac", "a1")
            .file(".yac/gone.php.yac", "gone")
            .file(".yac/same.php.yac", "same")
            .file("src/A.php", "old")
            .file("src/Same.php", "same")
        val before = DiskSnapshot.capture(tree.path, listOf("src/A.php", "src/Same.php", "src/Missing.php"), true)

        tree.file(".yac/a.php.yac", "a2")
            .file(".yac/new/n.php.yac", "new")
            .file(".yac/same.php.yac", "same")
            .file(".gitignore", "/.yac/\n")
            .file("src/A.php", "new")
            .file("src/Same.php", "same")
            .file("src/Missing.php", "created")
        Files.delete(tree.path.resolve(".yac/gone.php.yac"))
        val after = DiskSnapshot.capture(tree.path, listOf("src/A.php", "src/Same.php", "src/Missing.php"), true)

        assertEquals(
            mapOf(
                ".gitignore" to (null to "/.yac/\n"),
                ".yac/a.php.yac" to ("a1" to "a2"),
                ".yac/gone.php.yac" to ("gone" to null),
                ".yac/new/n.php.yac" to (null to "new"),
                "src/A.php" to ("old" to "new"),
                "src/Missing.php" to (null to "created"),
            ),
            changes(before, after),
        )
        assertEquals(
            listOf(".gitignore", ".yac/a.php.yac", ".yac/gone.php.yac", ".yac/new/n.php.yac", "src/A.php", "src/Missing.php"),
            before.changesTo(after).keys.toList(),
        )
    }

    @Test
    fun identicalSnapshotsHaveNoChanges() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a").file("a.php", "x")

        assertEquals(
            emptyMap<String, Pair<String?, String?>>(),
            changes(DiskSnapshot.capture(tree.path, listOf("a.php"), true), DiskSnapshot.capture(tree.path, listOf("a.php"), true)),
        )
    }

    @Test
    fun recordsThePermissionsOfExistingFilesOnBothSidesOfAChange() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/gone.php.yac", "gone")
        Files.setPosixFilePermissions(tree.path.resolve(".yac/a.php.yac"), PosixFilePermissions.fromString("rw-r-----"))
        Files.setPosixFilePermissions(tree.path.resolve(".yac/gone.php.yac"), PosixFilePermissions.fromString("rwx------"))
        val before = DiskSnapshot.capture(tree.path, emptyList(), true)
        tree.file(".yac/a.php.yac", "a2")
        Files.setPosixFilePermissions(tree.path.resolve(".yac/a.php.yac"), PosixFilePermissions.fromString("rw-------"))
        Files.delete(tree.path.resolve(".yac/gone.php.yac"))
        tree.file(".yac/new.php.yac", "new")
        Files.setPosixFilePermissions(tree.path.resolve(".yac/new.php.yac"), PosixFilePermissions.fromString("r--------"))

        val changes = before.changesTo(DiskSnapshot.capture(tree.path, emptyList(), true))

        assertEquals(
            mapOf(
                ".yac/a.php.yac" to ("rw-r-----" to "rw-------"),
                ".yac/gone.php.yac" to ("rwx------" to null),
                ".yac/new.php.yac" to (null to "r--------"),
            ),
            changes.mapValues { (_, change) -> change.beforePermissions?.let(PosixFilePermissions::toString) to change.afterPermissions?.let(PosixFilePermissions::toString) },
        )
    }

    @Test
    fun aCancellationFromTheCallbackStopsTheWalkOfTheYacTree() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/sub/b.php.yac", "b")
        var calls = 0

        val error = assertThrows(IllegalStateException::class.java) {
            DiskSnapshot.capture(tree.path, emptyList(), true) { if (3 == ++calls) throw IllegalStateException("cancelled") }
        }

        assertEquals(listOf("cancelled", 3), listOf(error.message, calls))
    }

    @Test
    fun aCancellationFromTheCallbackStopsTheReadingOfTheNamedFiles() {
        val tree = TemporaryTree.create().file("a.php", "a").file("b.php", "b")
        var calls = 0

        val error = assertThrows(IllegalStateException::class.java) {
            DiskSnapshot.capture(tree.path, listOf("a.php", "b.php"), false) { if (2 == ++calls) throw IllegalStateException("cancelled") }
        }

        assertEquals(listOf("cancelled", 2), listOf(error.message, calls))
    }
}
