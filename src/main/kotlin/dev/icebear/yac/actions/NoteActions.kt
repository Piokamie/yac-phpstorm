package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.YacCommands

abstract class NoteAction(
    protected val note: Note,
    private val file: VirtualFile?,
    text: String,
    description: String,
) : DumbAwareAction(text, description, null) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = null != project && null != file && isApplicable() && null != YacEnvironment.of(project, file)?.cli
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = file ?: return
        val environment = YacEnvironment.of(project, file) ?: return
        YacCommandRunner(project).run(environment, progressTitle(), arguments(), changedFiles(file), supportsDiff = supportsDiff(), undoFiles = listOf(file))
    }

    protected open fun isApplicable(): Boolean = true

    internal open fun supportsDiff(): Boolean = false

    protected abstract fun progressTitle(): String

    protected abstract fun arguments(): List<String>

    protected abstract fun changedFiles(file: VirtualFile): List<VirtualFile>
}

class PromoteNoteAction(note: Note, file: VirtualFile?) :
    NoteAction(note, file, "Promote to PHPDoc", "Turn this YAC note into a human PHPDoc comment above the code") {
    override fun isApplicable(): Boolean = note.isResolved

    override fun supportsDiff(): Boolean = true

    override fun progressTitle(): String = "Promoting ${note.id}"

    override fun arguments(): List<String> = listOf(YacCommands.PROMOTE, note.id)

    override fun changedFiles(file: VirtualFile): List<VirtualFile> = listOf(file)
}

class RemoveNoteAction(note: Note, file: VirtualFile?) :
    NoteAction(note, file, "Remove Note", "Delete this YAC note (the PHP source is not touched)") {
    override fun progressTitle(): String = "Removing ${note.id}"

    override fun arguments(): List<String> = listOf(YacCommands.REMOVE, note.id)

    override fun changedFiles(file: VirtualFile): List<VirtualFile> = emptyList()
}
