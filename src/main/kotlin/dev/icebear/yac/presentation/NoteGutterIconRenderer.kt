package dev.icebear.yac.presentation

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import dev.icebear.yac.actions.PromoteNoteAction
import dev.icebear.yac.actions.RemoveNoteAction
import java.awt.event.MouseEvent
import com.intellij.openapi.util.text.StringUtil
import dev.icebear.yac.cli.Note
import javax.swing.Icon

class NoteGutterIconRenderer(val notes: List<Note>) : GutterIconRenderer() {
    override fun getIcon(): Icon = if (notes.all { it.isResolved }) YacIcons.NOTE else YacIcons.NOTE_PROBLEM

    override fun getTooltipText(): String = notes.joinToString(SEPARATOR, HTML_START, HTML_END) { tooltip(it) }

    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getPopupMenuActions(): ActionGroup = DefaultActionGroup().apply {
        if (1 == notes.size) {
            addAll(noteActions(notes[0]))
        } else {
            notes.forEach { note -> add(DefaultActionGroup(label(note), noteActions(note)).apply { isPopup = true }) }
        }
    }

    override fun getClickAction(): AnAction = object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val mouseEvent = e.inputEvent as? MouseEvent ?: return
            JBPopupFactory.getInstance()
                .createActionGroupPopup(null, popupMenuActions, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
                .show(RelativePoint(mouseEvent))
        }
    }

    private fun noteActions(note: Note): List<AnAction> = listOf(PromoteNoteAction(note), RemoveNoteAction(note))

    private fun label(note: Note): String = note.comment.lineSequence().first().let { if (it.length > LABEL_LENGTH) it.take(LABEL_LENGTH) + ELLIPSIS else it }

    override fun equals(other: Any?): Boolean = other is NoteGutterIconRenderer && notes == other.notes

    override fun hashCode(): Int = notes.hashCode()

    private fun tooltip(note: Note): String {
        val heading = listOfNotNull(note.id, note.scope, note.status.takeUnless { note.isResolved }?.replace('_', ' '))
            .joinToString(HEADING_SEPARATOR) { StringUtil.escapeXmlEntities(it) }
        val body = StringUtil.escapeXmlEntities(note.comment).replace("\n", LINE_BREAK)
        val problems = note.problems.joinToString("") { LINE_BREAK + StringUtil.escapeXmlEntities(it) }

        return "<b>$heading</b>$LINE_BREAK$body$problems"
    }

    private companion object {
        const val HTML_START = "<html>"
        const val HTML_END = "</html>"
        const val SEPARATOR = "<hr>"
        const val HEADING_SEPARATOR = " · "
        const val LINE_BREAK = "<br>"
        const val LABEL_LENGTH = 40
        const val ELLIPSIS = "…"
    }
}
