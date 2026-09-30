package dev.icebear.yac.cli

import com.intellij.openapi.project.Project
import com.jetbrains.php.config.PhpProjectConfigurationFacade

object PhpInterpreterPath {
    fun of(project: Project): String? {
        val interpreter = PhpProjectConfigurationFacade.getInstance(project).interpreter ?: return null

        return if (interpreter.isRemote) null else interpreter.pathToPhpExecutable
    }
}
