package dev.icebear.yac.actions.undo

import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID
import kotlin.io.path.invariantSeparatorsPathString

internal class ByteReplay(
    private val project: Project,
    private val root: Path,
    private val changes: Map<String, FileChange>,
    private val directories: Set<String>,
) {
    fun verifyRedo() = verify(YacMessages.REDO) { it.before }

    fun undo() {
        verify(YacMessages.UNDO) { it.after }
        write(YacMessages.UNDO, { it.before }, { it.beforePermissions })
        removeAbsentYacDirectory(YacMessages.UNDO)
        refresh()
    }

    fun redo() {
        verify(YacMessages.REDO) { it.before }
        write(YacMessages.REDO, { it.after }, { it.afterPermissions })
        refresh()
    }

    private fun verify(action: String, expected: (FileChange) -> ByteArray?) {
        if (project.service<YacRunState>().isRunning) {
            throw UnexpectedUndoException(YacMessages.STILL_RUNNING)
        }

        changes.entries.forEach { (path, change) ->
            val isExpected = try {
                matches(root.resolve(path), expected(change))
            } catch (exception: IOException) {
                throw UnexpectedUndoException(YacMessages.unwritable(path, action))
            }

            if (!isExpected) {
                throw UnexpectedUndoException(YacMessages.changedSince(path, action))
            }
        }
    }

    private fun write(action: String, target: (FileChange) -> ByteArray?, permissions: (FileChange) -> Set<PosixFilePermission>?) {
        changes.forEach { (path, change) ->
            try {
                write(root.resolve(path), target(change), permissions(change))
            } catch (exception: IOException) {
                throw UnexpectedUndoException(YacMessages.unwritable(path, action))
            }
        }
    }

    private fun matches(path: Path, bytes: ByteArray?): Boolean {
        val current = if (Files.isRegularFile(path)) Files.readAllBytes(path) else null

        return bytes?.contentEquals(current) ?: (null == current)
    }

    private fun write(path: Path, bytes: ByteArray?, recorded: Set<PosixFilePermission>?) {
        if (null == bytes) {
            Files.deleteIfExists(path)
            prune(path.parent)

            return
        }

        val target = if (Files.isSymbolicLink(path)) path.toRealPath() else path
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(YacEnvironment.TEMPORARY_PREFIX + UUID.randomUUID())
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            applyPermissions(target, temporary, recorded)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun applyPermissions(target: Path, temporary: Path, recorded: Set<PosixFilePermission>?) {
        try {
            val permissions = if (Files.exists(target)) Files.getPosixFilePermissions(target) else recorded
            permissions?.let { Files.setPosixFilePermissions(temporary, it) }
        } catch (exception: UnsupportedOperationException) {
            return
        }
    }

    private fun prune(start: Path?) {
        var directory = start
        while (null != directory && root != directory && directory.startsWith(root) && !isKept(directory) && isEmpty(directory)) {
            Files.delete(directory)
            directory = directory.parent
        }
    }

    private fun isKept(directory: Path): Boolean = directories.contains(root.relativize(directory).invariantSeparatorsPathString)

    private fun isEmpty(directory: Path): Boolean = Files.isDirectory(directory) && Files.list(directory).use { !it.findAny().isPresent }

    private fun removeAbsentYacDirectory(action: String) {
        if (changes.isEmpty() || directories.contains(YacEnvironment.YAC_DIRECTORY)) {
            return
        }

        val yac = root.resolve(YacEnvironment.YAC_DIRECTORY)
        try {
            Files.deleteIfExists(yac.resolve(YacEnvironment.LOCK_FILE))
            deleteIfEmpty(yac.resolve(YacEnvironment.CACHE_DIRECTORY))
            deleteIfEmpty(yac)
        } catch (exception: IOException) {
            throw UnexpectedUndoException(YacMessages.unwritable(YacEnvironment.YAC_DIRECTORY, action))
        }
    }

    private fun deleteIfEmpty(directory: Path) {
        if (isEmpty(directory)) {
            Files.delete(directory)
        }
    }

    private fun refresh() {
        val local = LocalFileSystem.getInstance()
        val files = changes.keys.mapNotNull { nearestKnown(local, root.resolve(it)) }.distinct().toTypedArray<VirtualFile>()
        if (files.isNotEmpty()) {
            VfsUtil.markDirty(true, true, *files)
            RefreshQueue.getInstance().refresh(true, true, null, *files)
        }
    }

    private fun nearestKnown(local: LocalFileSystem, path: Path): VirtualFile? =
        generateSequence(path) { it.parent }.firstNotNullOfOrNull { local.findFileByNioFile(it) }
}
