package dev.icebear.yac.cli

import com.intellij.openapi.project.Project
import com.jetbrains.php.config.PhpProjectConfigurationFacade

data class PhpInterpreterPath(val localPath: String?, val isRemote: Boolean) {
    companion object {
        fun of(project: Project): PhpInterpreterPath {
            val interpreter = PhpProjectConfigurationFacade.getInstance(project).interpreter ?: return PhpInterpreterPath(null, false)

            return if (interpreter.isRemote) PhpInterpreterPath(null, true) else PhpInterpreterPath(interpreter.pathToPhpExecutable, false)
        }
    }
}
