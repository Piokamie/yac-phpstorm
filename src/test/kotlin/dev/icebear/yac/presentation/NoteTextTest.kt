package dev.icebear.yac.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {
    @Test
    fun keepsTheNoteLines() {
        assertEquals(listOf("One.", "Two."), NoteText.displayLines("One.\nTwo."))
    }

    @Test
    fun longNotesAreCutAfterThreeLines() {
        assertEquals(listOf("1", "2", "3…"), NoteText.displayLines("1\n2\n3\n4\n5"))
        assertEquals(listOf("1", "2", "3"), NoteText.displayLines("1\n2\n3"))
    }

    @Test
    fun labelsAreTheFirstLineCutAtFortyCharacters() {
        assertEquals("A first line that is far too long to sho…", NoteText.label("A first line that is far too long to show in a menu.\nSecond."))
        assertEquals("Exactly forty characters long, not more.", NoteText.label("Exactly forty characters long, not more."))
    }
}
