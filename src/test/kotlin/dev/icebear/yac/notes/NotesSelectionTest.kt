package dev.icebear.yac.notes

import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.NoteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesSelectionTest {
    private fun note(id: String, status: String) = Note(id, null, status, null, "Note.")

    @Test
    fun keepsWhatIsShownWhileTheBufferDoesNotParse() {
        assertNull(NotesSelection.toShow(listOf(note("A", NoteStatus.UNPARSEABLE_SOURCE)), listOf(note("A", NoteStatus.RESOLVED))))
    }

    @Test
    fun showsFreshNotesOtherwise() {
        val fresh = listOf(note("A", NoteStatus.UNPARSEABLE_SOURCE))
        val parsed = listOf(note("A", "orphaned"))

        assertEquals(fresh, NotesSelection.toShow(fresh, null))
        assertEquals(parsed, NotesSelection.toShow(parsed, listOf(note("A", NoteStatus.RESOLVED))))
        assertEquals(emptyList<Note>(), NotesSelection.toShow(emptyList(), listOf(note("A", NoteStatus.RESOLVED))))
    }
}
