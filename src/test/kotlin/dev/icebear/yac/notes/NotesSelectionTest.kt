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
    fun keepsWhatIsShownWhenOnlySomeNotesReportAnUnparseableSource() {
        val mixed = listOf(note("A", "invalid_anchor"), note("B", NoteStatus.UNPARSEABLE_SOURCE))

        assertNull(NotesSelection.toShow(mixed, listOf(note("A", NoteStatus.RESOLVED))))
        assertNull(NotesSelection.toShow(mixed.reversed(), listOf(note("A", NoteStatus.RESOLVED))))
    }

    @Test
    fun showsFreshNotesOtherwise() {
        val unparseable = listOf(note("A", NoteStatus.UNPARSEABLE_SOURCE))
        val mixed = listOf(note("A", "invalid_anchor"), note("B", NoteStatus.UNPARSEABLE_SOURCE))
        val parsed = listOf(note("A", "orphaned"), note("B", "invalid_anchor"))

        assertEquals(unparseable, NotesSelection.toShow(unparseable, null))
        assertEquals(mixed, NotesSelection.toShow(mixed, null))
        assertEquals(parsed, NotesSelection.toShow(parsed, listOf(note("A", NoteStatus.RESOLVED))))
        assertEquals(emptyList<Note>(), NotesSelection.toShow(emptyList(), listOf(note("A", NoteStatus.RESOLVED))))
    }
}
