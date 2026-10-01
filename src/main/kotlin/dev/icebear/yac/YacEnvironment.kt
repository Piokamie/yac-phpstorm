package dev.icebear.yac

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.icebear.yac.cli.PhpInterpreterPath
import dev.icebear.yac.cli.YacCli
import dev.icebear.yac.settings.YacSettings

class YacEnvironment private constructor(
    val root: VirtualFile,
    val cli: YacCli?,
    val isRemoteInterpreterSkipped: Boolean,
) {
    val yacDirectory: VirtualFile?
        get() = root.findChild(YAC_DIRECTORY)?.takeIf { it.isDirectory }

    val isInitialized: Boolean
        get() = null != yacDirectory

    fun sourceOf(file: VirtualFile): String? = if (root == file) CURRENT_DIRECTORY else VfsUtilCore.getRelativePath(file, root)

    companion object {
        const val YAC_DIRECTORY = ".yac"
        const val CACHE_DIRECTORY = ".cache"
        const val SIDECAR_EXTENSION = ".yac"
        const val CURRENT_DIRECTORY = "."
        const val LOCK_FILE = ".lock"
        const val GITIGNORE = ".gitignore"
        internal const val YAC_GITIGNORE = "$YAC_DIRECTORY/$GITIGNORE"
        const val TEMPORARY_PREFIX = ".yac-tmp-"
        private const val DEFAULT_PHP = "php"
        private const val GIT_MARKER = ".git"
        private const val VENDOR_YAC = "vendor/bin/yac"
        internal const val REPOSITORY_YAC = "bin/yac"

        fun of(project: Project, file: VirtualFile): YacEnvironment? {
            if (!file.isInLocalFileSystem) {
                return null
            }

            val root = rootOf(file) ?: return null
            val settings = project.service<YacSettings>()
            val yac = yacOf(file, root, settings.yacPath)
            val interpreter = if (settings.phpPath.isEmpty()) PhpInterpreterPath.of(project) else PhpInterpreterPath.NONE
            val cli = yac?.let { YacCli(php(settings.phpPath, interpreter.localPath), it.path, root.toNioPath()) }

            return YacEnvironment(root, cli, interpreter.isRemote)
        }

        internal fun yacPath(relative: String): String = "$YAC_DIRECTORY/$relative"

        internal fun sidecarOf(source: String): String = yacPath(source + SIDECAR_EXTENSION)

        internal fun rootOf(file: VirtualFile): VirtualFile? = directoriesUpFrom(file).firstOrNull { directory ->
            true == directory.findChild(YAC_DIRECTORY)?.isDirectory || null != directory.findChild(GIT_MARKER)
        }

        internal fun yacOf(file: VirtualFile, root: VirtualFile, configured: String): VirtualFile? {
            val found = if (configured.isNotEmpty()) {
                if (FileUtil.isAbsolute(configured)) LocalFileSystem.getInstance().findFileByPath(configured) else root.findFileByRelativePath(configured)
            } else {
                directoriesUpFrom(file).takeWhileInclusive { root != it }.firstNotNullOfOrNull { it.findFileByRelativePath(VENDOR_YAC) }
                    ?: root.findFileByRelativePath(REPOSITORY_YAC)
            }

            return found?.takeUnless { it.isDirectory }
        }

        internal fun php(configured: String, interpreterPath: String?): String = configured.ifEmpty { interpreterPath ?: DEFAULT_PHP }

        private fun directoriesUpFrom(file: VirtualFile): Sequence<VirtualFile> =
            generateSequence(if (file.isDirectory) file else file.parent) { it.parent }

        private fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> = sequence {
            for (item in this@takeWhileInclusive) {
                yield(item)
                if (!predicate(item)) {
                    break
                }
            }
        }
    }
}
