package dev.icebear.yac.presentation

object NoteText {
    const val ELLIPSIS = "…"
    const val MAX_LINES = 3
    const val LABEL_LENGTH = 40

    fun displayLines(comment: String): List<String> {
        val lines = comment.lines()
        val shown = lines.take(MAX_LINES)

        return if (lines.size > MAX_LINES) shown.dropLast(1) + (shown.last() + ELLIPSIS) else shown
    }

    fun label(comment: String): String = comment.lineSequence().first().let { if (it.length > LABEL_LENGTH) it.take(LABEL_LENGTH) + ELLIPSIS else it }
}
