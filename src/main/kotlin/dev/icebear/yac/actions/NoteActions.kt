package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.LocalFileSystem
import dev.icebear.yac.cli.Note

class PromoteNoteAction(private val note: Note) : DumbAwareAction("Promote to PHPDoc", "Turn this YAC note into a human PHPDoc comment above the code", null) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = null != e.project && note.isResolved
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath("$it/${note.source}") }
        YacCommandRunner(project).run("Promoting ${note.id}", listOf(PROMOTE, note.id), listOfNotNull(file))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private companion object {
        const val PROMOTE = "promote"
    }
}

class RemoveNoteAction(private val note: Note) : DumbAwareAction("Remove Note", "Delete this YAC note (the PHP source is not touched)", null) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = null != e.project
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        YacCommandRunner(project).run("Removing ${note.id}", listOf(REMOVE, note.id), emptyList())
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private companion object {
        const val REMOVE = "remove"
    }
}
