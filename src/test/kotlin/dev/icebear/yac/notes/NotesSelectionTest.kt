package dev.icebear.yac.notes

import dev.icebear.yac.cli.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesSelectionTest {
    private fun note(id: String, status: String) = Note(id, "a.php", null, status, null, "a();", "Note.")

    @Test
    fun keepsThePreviousNotesWhileTheBufferDoesNotParse() {
        val previous = listOf(note("A", "resolved"))

        assertEquals(previous, NotesSelection.toShow(listOf(note("A", NotesSelection.UNPARSEABLE_SOURCE)), previous))
    }

    @Test
    fun showsFreshNotesOtherwise() {
        val fresh = listOf(note("A", NotesSelection.UNPARSEABLE_SOURCE))
        val parsed = listOf(note("A", "orphaned"))

        assertEquals(fresh, NotesSelection.toShow(fresh, null))
        assertEquals(parsed, NotesSelection.toShow(parsed, listOf(note("A", "resolved"))))
        assertEquals(emptyList<Note>(), NotesSelection.toShow(emptyList(), listOf(note("A", "resolved"))))
    }
}
