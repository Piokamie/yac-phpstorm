package dev.icebear.yac.actions.undo

import com.intellij.openapi.components.service
import com.intellij.openapi.command.undo.UnexpectedUndoException
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacTestCase
import org.junit.Assert
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class YacUndoableActionTest : YacTestCase() {
    private fun bytes(text: String?): ByteArray? = text?.toByteArray(Charsets.UTF_8)

    private fun replay(root: Path, directories: Set<String>, vararg changes: Pair<String, Pair<String?, String?>>) =
        ByteReplay(project, root, changes.associate { (path, pair) -> path to FileChange(bytes(pair.first), bytes(pair.second)) }, directories)

    private fun action(root: Path, vararg changes: Pair<String, Pair<String?, String?>>) = YacUndoableAction(project, replay(root, setOf(".yac"), *changes), emptyArray(), emptyList())

    private fun guard(root: Path, vararg changes: Pair<String, Pair<String?, String?>>) = YacRedoGuard(replay(root, emptySet(), *changes), emptyArray())

    private fun read(tree: TemporaryTree, path: String): String? = tree.path.resolve(path).let { if (Files.exists(it)) Files.readString(it) else null }

    fun testUndoWritesTheBeforeBytesAndRedoTheAfterBytes() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a2").file(".yac/deep/dir/n.php.yac", "new").file(".yac/keep.php.yac", "keep")
        val action = action(
            tree.path,
            ".yac/a.php.yac" to ("a1" to "a2"),
            ".yac/deep/dir/n.php.yac" to (null to "new"),
            ".yac/gone.php.yac" to ("gone" to null),
        )

        action.undo()

        assertEquals(
            listOf("a1", null, "gone", "keep", false),
            listOf(read(tree, ".yac/a.php.yac"), read(tree, ".yac/deep/dir/n.php.yac"), read(tree, ".yac/gone.php.yac"), read(tree, ".yac/keep.php.yac"), Files.exists(tree.path.resolve(".yac/deep"))),
        )

        action.redo()

        assertEquals(
            listOf("a2", "new", null, "keep", true),
            listOf(read(tree, ".yac/a.php.yac"), read(tree, ".yac/deep/dir/n.php.yac"), read(tree, ".yac/gone.php.yac"), read(tree, ".yac/keep.php.yac"), Files.exists(tree.path.resolve(".yac/deep"))),
        )
    }

    fun testWritesLeaveNoTemporaryFilesAndKeepThePermissionsOfTheReplacedFile() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a2").file("run.php", "after")
        Files.setPosixFilePermissions(tree.path.resolve("run.php"), PosixFilePermissions.fromString("rwxr-x---"))

        action(tree.path, ".yac/a.php.yac" to ("a1" to "a2"), "run.php" to ("before" to "after")).undo()

        assertEquals(listOf("a1", "before"), listOf(read(tree, ".yac/a.php.yac"), read(tree, "run.php")))
        assertEquals("rwxr-x---", PosixFilePermissions.toString(Files.getPosixFilePermissions(tree.path.resolve("run.php"))))
        assertEquals(listOf(".yac", "run.php"), Files.list(tree.path).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() })
        assertEquals(listOf("a.php.yac"), Files.list(tree.path.resolve(".yac")).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    fun testAWriteThroughASymbolicLinkReplacesTheTargetAndKeepsTheLink() {
        val tree = TemporaryTree.create().file("real/a.php.yac", "a2")
        Files.createDirectories(tree.path.resolve(".yac"))
        Files.createSymbolicLink(tree.path.resolve(".yac/a.php.yac"), tree.path.resolve("real/a.php.yac"))

        action(tree.path, ".yac/a.php.yac" to ("a1" to "a2")).undo()

        assertEquals(listOf(true, "a1"), listOf(Files.isSymbolicLink(tree.path.resolve(".yac/a.php.yac")), read(tree, "real/a.php.yac")))
    }

    fun testPruningStopsAtTheFirstNonEmptyDirectoryAndNeverRemovesTheRoot() {
        val withLock = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/.lock", "").file(".gitignore", "/.yac/\n")
        action(withLock.path, ".yac/a.php.yac" to (null to "a"), ".gitignore" to (null to "/.yac/\n")).undo()

        assertEquals(listOf(true, false, false), listOf(Files.exists(withLock.path.resolve(".yac/.lock")), Files.exists(withLock.path.resolve(".yac/a.php.yac")), Files.exists(withLock.path.resolve(".gitignore"))))

        val alone = TemporaryTree.create().file(".yac/sub/a.php.yac", "a")
        replay(alone.path, emptySet(), ".yac/sub/a.php.yac" to (null to "a")).undo()

        assertEquals(listOf(false, true), listOf(Files.exists(alone.path.resolve(".yac")), Files.isDirectory(alone.path)))
    }

    fun testADirectoryThatExistedBeforeTheActionIsNeverPruned() {
        val tree = TemporaryTree.create().file(".yac/sub/a.php.yac", "a").file("src/new/B.php", "b")

        replay(tree.path, setOf(".yac", ".yac/sub", "src"), ".yac/sub/a.php.yac" to (null to "a"), "src/new/B.php" to (null to "b")).undo()

        assertEquals(
            listOf(true, true, true, false),
            listOf(Files.isDirectory(tree.path.resolve(".yac")), Files.isDirectory(tree.path.resolve(".yac/sub")), Files.isDirectory(tree.path.resolve("src")), Files.exists(tree.path.resolve("src/new"))),
        )
    }

    fun testUndoOfAFirstYacDirectoryRemovesTheLeftoverLockAndEmptyCacheAndTheDirectory() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/.lock", "").directory(".yac/.cache").file(".gitignore", "/.yac/\n")
        val replay = replay(tree.path, emptySet(), ".yac/a.php.yac" to (null to "a"), ".gitignore" to (null to "/.yac/\n"))

        replay.undo()

        assertEquals(listOf(false, false), listOf(Files.exists(tree.path.resolve(".yac")), Files.exists(tree.path.resolve(".gitignore"))))

        replay.redo()

        assertEquals(listOf("a", "/.yac/\n"), listOf(read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testUndoOfAFirstYacDirectoryKeepsItWhenItHoldsAnythingElse() {
        val withCacheContents = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/.cache/index", "cache")
        replay(withCacheContents.path, emptySet(), ".yac/a.php.yac" to (null to "a")).undo()

        val withForeignFile = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/.lock", "").file(".yac/notes.txt", "mine")
        replay(withForeignFile.path, emptySet(), ".yac/a.php.yac" to (null to "a")).undo()

        assertEquals(
            listOf("cache", "mine", false, true),
            listOf(read(withCacheContents, ".yac/.cache/index"), read(withForeignFile, ".yac/notes.txt"), Files.exists(withForeignFile.path.resolve(".yac/.lock")), Files.isDirectory(withCacheContents.path.resolve(".yac"))),
        )
    }

    fun testUndoOfAnActionOnAnExistingYacDirectoryKeepsTheLockAndTheEmptyDirectory() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a").file(".yac/.lock", "")

        replay(tree.path, setOf(".yac"), ".yac/a.php.yac" to (null to "a")).undo()

        assertEquals(listOf(true, true), listOf(Files.isDirectory(tree.path.resolve(".yac")), Files.exists(tree.path.resolve(".yac/.lock"))))
    }

    fun testUndoRecreatesMissingDirectories() {
        val tree = TemporaryTree.create()

        action(tree.path, ".yac/sub/a.php.yac" to ("before" to null)).undo()

        assertEquals("before", read(tree, ".yac/sub/a.php.yac"))
    }

    fun testUndoRefusesAndWritesNothingWhenAFileChangedAfterTheAction() {
        val tree = TemporaryTree.create().file(".gitignore", "/.yac/\n").file(".yac/a.php.yac", "tampered").file(".yac/b.php.yac", "b2")
        val action = action(tree.path, ".gitignore" to (null to "/.yac/\n"), ".yac/a.php.yac" to ("a1" to "a2"), ".yac/b.php.yac" to ("b1" to "b2"))

        val exception = Assert.assertThrows(UnexpectedUndoException::class.java) { action.undo() }

        assertEquals("Cannot undo: .yac/a.php.yac changed after this YAC action. Restore it or discard the change, then try again.", exception.message)
        assertEquals(listOf("/.yac/\n", "tampered", "b2"), listOf(read(tree, ".gitignore"), read(tree, ".yac/a.php.yac"), read(tree, ".yac/b.php.yac")))
    }

    fun testUndoRefusesWhenAFileThatShouldBeAbsentExists() {
        val tree = TemporaryTree.create().file(".yac/gone.php.yac", "back")
        val action = action(tree.path, ".yac/gone.php.yac" to ("gone" to null))

        val exception = Assert.assertThrows(UnexpectedUndoException::class.java) { action.undo() }

        assertEquals("Cannot undo: .yac/gone.php.yac changed after this YAC action. Restore it or discard the change, then try again.", exception.message)
        assertEquals("back", read(tree, ".yac/gone.php.yac"))
    }

    fun testRedoRefusesWhenTheBeforeStateWasChanged() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a1").file(".yac/n.php.yac", "surprise")
        val action = action(tree.path, ".yac/a.php.yac" to ("a1" to "a2"), ".yac/n.php.yac" to (null to "new"))

        val exception = Assert.assertThrows(UnexpectedUndoException::class.java) { action.redo() }

        assertEquals("Cannot redo: .yac/n.php.yac changed after this YAC action. Restore it or discard the change, then try again.", exception.message)
        assertEquals(listOf("a1", "surprise"), listOf(read(tree, ".yac/a.php.yac"), read(tree, ".yac/n.php.yac")))
    }

    fun testTheRedoGuardChecksTheBeforeStateOnRedoWritesNothingAndDoesNothingOnUndo() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a1")
        val guard = guard(tree.path, ".yac/a.php.yac" to ("a1" to "a2"))

        guard.undo()
        guard.redo()

        assertEquals("a1", read(tree, ".yac/a.php.yac"))

        tree.file(".yac/a.php.yac", "tampered")
        val exception = Assert.assertThrows(UnexpectedUndoException::class.java) { guard.redo() }

        assertEquals("Cannot redo: .yac/a.php.yac changed after this YAC action. Restore it or discard the change, then try again.", exception.message)
        assertEquals("tampered", read(tree, ".yac/a.php.yac"))
    }

    fun testAFailedWriteIsReportedAsARefusalNamingThePath() {
        val tree = TemporaryTree.create().file(".yac/blocked/child", "x")

        val exception = Assert.assertThrows(UnexpectedUndoException::class.java) { action(tree.path, ".yac/blocked" to ("before" to null)).undo() }

        assertEquals(YacMessages.unwritable(".yac/blocked", YacMessages.UNDO), exception.message)
        assertEquals("Cannot undo: .yac/blocked could not be read or written.", exception.message)
        assertEquals("x", read(tree, ".yac/blocked/child"))
        assertEquals(listOf("child"), Files.list(tree.path.resolve(".yac/blocked")).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    fun testUndoRedoAndTheGuardAreRefusedWhileYacIsRunning() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a2")
        val action = action(tree.path, ".yac/a.php.yac" to ("a1" to "a2"))
        val guard = guard(tree.path, ".yac/a.php.yac" to ("a2" to "a3"))
        val state = project.service<YacRunState>()
        assertTrue(state.tryStart())

        try {
            val messages = listOf<() -> Unit>({ action.undo() }, { action.redo() }, { guard.redo() }).map { call ->
                Assert.assertThrows(UnexpectedUndoException::class.java) { call() }.message
            }

            val expected = "yac is still running; try again when it finishes."
            assertEquals(listOf(expected, expected, expected), messages)
            assertEquals("a2", read(tree, ".yac/a.php.yac"))
        } finally {
            state.finish()
        }

        action.undo()

        assertEquals("a1", read(tree, ".yac/a.php.yac"))
    }

    fun testUndoRecreatesADeletedFileWithItsOriginalPermissionsAndRedoRecreatesACreatedOneWithItsOwn() {
        val tree = TemporaryTree.create().file("run.php", "before").file("made.php", "x")
        Files.setPosixFilePermissions(tree.path.resolve("run.php"), PosixFilePermissions.fromString("rwxr-x---"))
        Files.setPosixFilePermissions(tree.path.resolve("made.php"), PosixFilePermissions.fromString("rw-r-----"))
        val before = DiskSnapshot.capture(tree.path, listOf("run.php", "made.php", "new.php"), false)
        Files.delete(tree.path.resolve("run.php"))
        Files.delete(tree.path.resolve("made.php"))
        tree.file("new.php", "created")
        Files.setPosixFilePermissions(tree.path.resolve("new.php"), PosixFilePermissions.fromString("rwx------"))
        val after = DiskSnapshot.capture(tree.path, listOf("run.php", "made.php", "new.php"), false)
        val replay = ByteReplay(project, tree.path, before.changesTo(after), emptySet())

        replay.undo()

        assertEquals(listOf("before", "x", null), listOf(read(tree, "run.php"), read(tree, "made.php"), read(tree, "new.php")))
        assertEquals(
            listOf("rwxr-x---", "rw-r-----"),
            listOf("run.php", "made.php").map { PosixFilePermissions.toString(Files.getPosixFilePermissions(tree.path.resolve(it))) },
        )

        replay.redo()

        assertEquals(listOf(null, null, "created"), listOf(read(tree, "run.php"), read(tree, "made.php"), read(tree, "new.php")))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(tree.path.resolve("new.php"))))
    }

    fun testAReplayWithoutChangesLeavesAnAbsentYacDirectoryAlone() {
        val tree = TemporaryTree.create().file(".yac/.lock", "")

        replay(tree.path, emptySet()).undo()

        assertEquals(listOf(true, true), listOf(Files.isDirectory(tree.path.resolve(".yac")), Files.exists(tree.path.resolve(".yac/.lock"))))
    }
}
