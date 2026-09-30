package dev.icebear.yac.cli

import com.intellij.openapi.project.Project
import com.jetbrains.php.config.PhpProjectConfigurationFacade

data class PhpInterpreterPath(val localPath: String?, val isRemote: Boolean) {
    companion object {
        val NONE = PhpInterpreterPath(localPath = null, isRemote = false)

        fun of(project: Project): PhpInterpreterPath {
            val interpreter = PhpProjectConfigurationFacade.getInstance(project).interpreter ?: return NONE

            return if (interpreter.isRemote) PhpInterpreterPath(localPath = null, isRemote = true) else PhpInterpreterPath(localPath = interpreter.pathToPhpExecutable, isRemote = false)
        }
    }
}
