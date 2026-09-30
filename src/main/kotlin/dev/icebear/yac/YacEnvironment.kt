package dev.icebear.yac

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.icebear.yac.cli.PhpInterpreterPath
import dev.icebear.yac.cli.YacBinaryLocator
import dev.icebear.yac.cli.YacCli
import dev.icebear.yac.settings.YacSettings
import java.nio.file.Files
import java.nio.file.Path

class YacEnvironment private constructor(val cli: YacCli, private val basePath: String) {
    fun sourceOf(file: VirtualFile): String? =
        if (file.isInLocalFileSystem && file.path.startsWith(basePath + SEPARATOR)) file.path.removePrefix(basePath + SEPARATOR) else null

    companion object {
        private const val SEPARATOR = "/"
        private const val YAC_DIRECTORY = ".yac"

        fun of(project: Project): YacEnvironment? {
            val basePath = project.basePath ?: return null
            val root = Path.of(basePath)
            val settings = project.service<YacSettings>()
            val locator = YacBinaryLocator(root, settings.yacPath, settings.phpPath) { PhpInterpreterPath.of(project) }
            val yac = locator.yac()
            if (null == yac) {
                if (Files.isDirectory(root.resolve(YAC_DIRECTORY)) || settings.yacPath.isNotEmpty()) {
                    YacNotifications.notifyOnce(project, "No yac binary found; run composer require --dev icebear/yac or set the path in Settings | Tools | YAC.", NotificationType.WARNING)
                }

                return null
            }

            return YacEnvironment(YacCli(locator.php(), yac, root), basePath)
        }
    }
}
