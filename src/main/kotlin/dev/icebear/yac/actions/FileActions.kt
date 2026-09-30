package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

abstract class FileCommandAction(private val command: String, private val progressTitle: String) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty()
        e.presentation.isEnabledAndVisible = null != project && files.isNotEmpty() && files.all { null != sourceOf(project, it) }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty().toList()
        val sources = files.mapNotNull { sourceOf(project, it) }
        if (sources.isEmpty() || !confirm(project, sources)) {
            return
        }

        YacCommandRunner(project).run(progressTitle, listOf(command) + sources, files)
    }

    protected open fun confirm(project: Project, sources: List<String>): Boolean = true

    private fun sourceOf(project: Project, file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        if (!file.isInLocalFileSystem) {
            return null
        }

        return when (file.path) {
            basePath -> CURRENT_DIRECTORY
            else -> if (file.path.startsWith("$basePath/")) file.path.removePrefix("$basePath/") else null
        }
    }

    private companion object {
        const val CURRENT_DIRECTORY = "."
    }
}

class ExtractFileAction : FileCommandAction("extract", "Extracting inline YAC notes")

class InjectFileAction : FileCommandAction("inject", "Injecting YAC notes")

class YeetFileAction : FileCommandAction("yeet", "Deleting YAC notes") {
    override fun confirm(project: Project, sources: List<String>): Boolean =
        Messages.YES == Messages.showYesNoDialog(
            project,
            "Delete all YAC notes for ${sources.joinToString(", ")}? The PHP source is not touched.",
            "Yeet YAC Notes",
            Messages.getQuestionIcon(),
        )
}
