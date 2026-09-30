package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.cli.YacCommands

abstract class FileCommandAction(private val command: String, private val progressTitle: String) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = null != target(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = target(e) ?: return
        if (!confirm(project, target)) {
            return
        }

        YacCommandRunner(project).run(target.environment, progressTitle, listOf(command) + target.sources, target.files)
    }

    protected open fun confirm(project: Project, target: Target): Boolean = true

    class Target(val environment: YacEnvironment, val files: List<VirtualFile>, val sources: List<String>)

    companion object {
        internal fun target(project: Project, files: List<VirtualFile>): Target? {
            if (files.isEmpty()) {
                return null
            }

            val environments = files.map { YacEnvironment.of(project, it) ?: return null }
            val environment = environments.first()
            if (null == environment.cli || environments.any { environment.root != it.root }) {
                return null
            }

            return Target(environment, files, files.map { environment.sourceOf(it) ?: return null })
        }

        internal fun target(e: AnActionEvent): Target? {
            val project = e.project ?: return null

            return target(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty().toList())
        }
    }
}

class ExtractFileAction : FileCommandAction(YacCommands.EXTRACT, "Extracting inline YAC notes")

class InjectFileAction : FileCommandAction(YacCommands.INJECT, "Injecting YAC notes")

class YeetFileAction : FileCommandAction(YacCommands.YEET, "Deleting YAC notes") {
    override fun confirm(project: Project, target: Target): Boolean =
        Messages.YES == Messages.showYesNoDialog(project, question(target.sources, target.environment.root.name), TITLE, Messages.getQuestionIcon())

    companion object {
        private const val TITLE = "Yeet YAC Notes"
        private const val MAX_LISTED = 5

        internal fun question(sources: List<String>, rootName: String): String {
            val names = sources.map { if (YacEnvironment.CURRENT_DIRECTORY == it) "the whole project ($rootName)" else it }
            val listed = names.take(MAX_LISTED) + if (names.size > MAX_LISTED) listOf("and ${names.size - MAX_LISTED} more") else emptyList()

            return "Delete all YAC notes of:\n\n${listed.joinToString("\n")}\n\nThe PHP source is not touched."
        }
    }
}

class YacFileActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = null != FileCommandAction.target(e)
    }
}
