package dev.icebear.yac.presentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import dev.icebear.yac.cli.Note

object NotesPresenter {
    private val PRESENTATION = Key.create<Presentation>("yac.presentation")
    private const val FIRST_LINE = 0

    fun render(editor: Editor, notes: List<Note>, showInlays: Boolean) {
        clear(editor)
        val document = editor.document
        if (0 == document.lineCount) {
            return
        }

        val highlighters = notes.groupBy { lineIndex(it, document.lineCount) }.toSortedMap().map { (line, lineNotes) ->
            editor.markupModel.addLineHighlighter(line, HighlighterLayer.ADDITIONAL_SYNTAX, null).apply {
                gutterIconRenderer = NoteGutterIconRenderer(lineNotes.sortedBy { it.id })
            }
        }

        val inlays = if (!showInlays) {
            emptyList()
        } else {
            notes.filter { it.isResolved }.sortedBy { it.id }.mapNotNull { note ->
                val line = lineIndex(note, document.lineCount)
                val lineStart = document.getLineStartOffset(line)
                val text = document.charsSequence
                var codeStart = lineStart
                while (codeStart < document.getLineEndOffset(line) && text[codeStart].isWhitespace()) {
                    codeStart++
                }

                editor.inlayModel.addBlockElement(lineStart, false, true, 0, NoteInlayRenderer(note, editor.offsetToXY(codeStart).x))
            }
        }

        editor.putUserData(PRESENTATION, Presentation(highlighters, inlays))
    }

    fun clear(editor: Editor) {
        val presentation = editor.getUserData(PRESENTATION) ?: return
        presentation.highlighters.filter { it.isValid }.forEach { editor.markupModel.removeHighlighter(it) }
        presentation.inlays.filter { it.isValid }.forEach { Disposer.dispose(it) }
        editor.putUserData(PRESENTATION, null)
    }

    private fun lineIndex(note: Note, lineCount: Int): Int = note.line?.let { (it - 1).coerceIn(FIRST_LINE, lineCount - 1) } ?: FIRST_LINE

    private data class Presentation(
        val highlighters: List<RangeHighlighter>,
        val inlays: List<Inlay<NoteInlayRenderer>>,
    )
}
