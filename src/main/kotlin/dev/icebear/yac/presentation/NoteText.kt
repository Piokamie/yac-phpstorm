package dev.icebear.yac.presentation

import dev.icebear.yac.cli.Note

object NoteText {
    private const val ELLIPSIS = "…"
    private const val MAX_LINES = 3
    private const val LABEL_LENGTH = 40

    fun displayLines(note: Note): List<String> {
        if (note.comment.isBlank()) {
            return listOf(note.id)
        }

        val lines = note.comment.lines()
        val shown = lines.take(MAX_LINES)

        return if (lines.size > MAX_LINES) shown.dropLast(1) + (shown.last() + ELLIPSIS) else shown
    }

    fun label(note: Note): String {
        if (note.comment.isBlank()) {
            return note.id
        }

        return note.comment.lineSequence().first().let { if (it.length > LABEL_LENGTH) it.take(LABEL_LENGTH) + ELLIPSIS else it }
    }
}
