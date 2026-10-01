package dev.icebear.yac.actions.undo

import dev.icebear.yac.YacEnvironment
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.invariantSeparatorsPathString

internal class DiskSnapshot private constructor(
    val files: Map<String, ByteArray?>,
    val directories: Set<String>,
    private val permissions: Map<String, Set<PosixFilePermission>>,
) {
    fun changesTo(after: DiskSnapshot): Map<String, FileChange> =
        (files.keys + after.files.keys).sorted()
            .associateWith { FileChange(files[it], after.files[it], permissions[it], after.permissions[it]) }
            .filterValues { !(it.before?.contentEquals(it.after) ?: (null == it.after)) }

    private class YacTree(val files: List<String>, val directories: Set<String>)

    companion object {
        fun capture(root: Path, files: Collection<String>, isYacTreeIncluded: Boolean, checkCanceled: () -> Unit = {}): DiskSnapshot {
            val tree = if (isYacTreeIncluded) walk(root, checkCanceled) else YacTree(emptyList(), emptySet())
            val paths = (listOf(YacEnvironment.GITIGNORE) + files + tree.files).distinct()
            val ancestors = paths.flatMap { ancestorsOf(it) }.distinct().filter { Files.isDirectory(root.resolve(it)) }
            val permissions = mutableMapOf<String, Set<PosixFilePermission>>()
            val contents = paths.associateWith { path ->
                checkCanceled()
                val file = root.resolve(path)
                val bytes = read(file)
                if (null != bytes) {
                    permissionsOf(file)?.let { permissions[path] = it }
                }

                bytes
            }

            return DiskSnapshot(contents, tree.directories + ancestors, permissions)
        }

        private fun ancestorsOf(path: String): List<String> =
            generateSequence(path.substringBeforeLast('/', "")) { it.substringBeforeLast('/', "") }.takeWhile { it.isNotEmpty() }.toList()

        private fun walk(root: Path, checkCanceled: () -> Unit): YacTree {
            val yac = root.resolve(YacEnvironment.YAC_DIRECTORY)
            if (!Files.isDirectory(yac)) {
                return YacTree(emptyList(), emptySet())
            }

            val start = yac.toRealPath()
            val cache = start.resolve(YacEnvironment.CACHE_DIRECTORY)
            val lock = start.resolve(YacEnvironment.LOCK_FILE)
            val files = mutableListOf<String>()
            val directories = mutableSetOf<String>()
            val visitor = object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    checkCanceled()
                    if (cache == dir) {
                        return FileVisitResult.SKIP_SUBTREE
                    }

                    directories.add(nameOf(start, dir))

                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    checkCanceled()
                    if (attrs.isRegularFile && lock != file && !file.fileName.toString().startsWith(YacEnvironment.TEMPORARY_PREFIX)) {
                        files.add(nameOf(start, file))
                    }

                    return FileVisitResult.CONTINUE
                }
            }
            Files.walkFileTree(start, visitor)

            return YacTree(files, directories)
        }

        private fun nameOf(start: Path, path: Path): String =
            start.relativize(path).invariantSeparatorsPathString.let { if (it.isEmpty()) YacEnvironment.YAC_DIRECTORY else YacEnvironment.yacPath(it) }

        private fun read(path: Path): ByteArray? = if (Files.isRegularFile(path)) Files.readAllBytes(path) else null

        private fun permissionsOf(path: Path): Set<PosixFilePermission>? = try {
            Files.getPosixFilePermissions(path)
        } catch (exception: UnsupportedOperationException) {
            null
        }
    }
}
