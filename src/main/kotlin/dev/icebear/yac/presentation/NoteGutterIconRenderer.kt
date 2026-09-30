package dev.icebear.yac.presentation

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import dev.icebear.yac.actions.PromoteNoteAction
import dev.icebear.yac.actions.RemoveNoteAction
import dev.icebear.yac.cli.Note
import java.awt.event.MouseEvent
import javax.swing.Icon

class NoteGutterIconRenderer(val notes: List<Note>, val file: VirtualFile?) : GutterIconRenderer() {
    override fun getIcon(): Icon = if (notes.all { it.isResolved }) YacIcons.NOTE else YacIcons.NOTE_PROBLEM

    override fun getTooltipText(): String = notes.joinToString(SEPARATOR, HTML_START, HTML_END) { tooltip(it) }

    override fun getAccessibleName(): String = accessibleName(notes)

    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getPopupMenuActions(): ActionGroup = DefaultActionGroup().apply {
        if (1 == notes.size) {
            addAll(noteActions(notes[0]))
        } else {
            notes.forEach { note ->
                add(DefaultActionGroup(noteActions(note)).apply {
                    templatePresentation.setText(NoteText.label(note), false)
                    isPopup = true
                })
            }
        }
    }

    override fun getClickAction(): AnAction = object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(null, popupMenuActions, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
            val mouseEvent = e.inputEvent as? MouseEvent
            if (null == mouseEvent) {
                popup.showInBestPositionFor(e.dataContext)
            } else {
                popup.show(RelativePoint(mouseEvent))
            }
        }
    }

    private fun noteActions(note: Note): List<AnAction> = listOf(PromoteNoteAction(note, file), RemoveNoteAction(note, file))

    override fun equals(other: Any?): Boolean = other is NoteGutterIconRenderer && notes == other.notes && file == other.file

    override fun hashCode(): Int = notes.hashCode()

    private fun tooltip(note: Note): String {
        val heading = listOfNotNull(note.id, note.scope, note.status.takeUnless { note.isResolved }?.replace(STATUS_SEPARATOR, STATUS_SPACE))
            .joinToString(HEADING_SEPARATOR) { StringUtil.escapeXmlEntities(it) }
        val body = StringUtil.escapeXmlEntities(note.comment).replace("\n", LINE_BREAK)
        val problems = note.problems.joinToString("") { LINE_BREAK + StringUtil.escapeXmlEntities(it) }

        return "<b>$heading</b>$LINE_BREAK$body$problems"
    }

    companion object {
        private const val HTML_START = "<html>"
        private const val HTML_END = "</html>"
        private const val SEPARATOR = "<hr>"
        private const val HEADING_SEPARATOR = " · "
        private const val LINE_BREAK = "<br>"
        private const val STATUS_SEPARATOR = '_'
        private const val STATUS_SPACE = ' '
        private const val SINGLE_NOTE = "YAC note: "
        private const val SINGLE_PROBLEM = "YAC note with a problem: "
        private const val SEVERAL_NOTES = "YAC notes"
        private const val SEVERAL_PROBLEMS = "YAC notes with problems"

        internal fun accessibleName(notes: List<Note>): String {
            val hasProblem = notes.any { !it.isResolved }
            if (1 == notes.size) {
                return (if (hasProblem) SINGLE_PROBLEM else SINGLE_NOTE) + NoteText.label(notes[0])
            }

            return "${if (hasProblem) SEVERAL_PROBLEMS else SEVERAL_NOTES} (${notes.size})"
        }
    }
}
