package dev.icebear.yac.presentation

import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.NoteStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {
    private fun note(comment: String) = Note("yac_01", 1, NoteStatus.RESOLVED, null, comment)

    @Test
    fun keepsTheNoteLines() {
        assertEquals(listOf("One.", "Two."), NoteText.displayLines(note("One.\nTwo.")))
    }

    @Test
    fun longNotesAreCutAfterThreeLines() {
        assertEquals(listOf("1", "2", "3…"), NoteText.displayLines(note("1\n2\n3\n4\n5")))
        assertEquals(listOf("1", "2", "3"), NoteText.displayLines(note("1\n2\n3")))
    }

    @Test
    fun labelsAreTheFirstLineCutAtFortyCharacters() {
        assertEquals("A first line that is far too long to sho…", NoteText.label(note("A first line that is far too long to show in a menu.\nSecond.")))
        assertEquals("Exactly forty characters long, not more.", NoteText.label(note("Exactly forty characters long, not more.")))
    }

    @Test
    fun blankCommentsShowTheNoteId() {
        assertEquals(
            listOf(listOf("yac_01"), listOf("yac_01"), listOf("yac_01")),
            listOf("", " ", "\n \n").map { NoteText.displayLines(note(it)) },
        )
        assertEquals(listOf("yac_01", "yac_01", "yac_01"), listOf("", " ", "\n \n").map { NoteText.label(note(it)) })
    }
}
