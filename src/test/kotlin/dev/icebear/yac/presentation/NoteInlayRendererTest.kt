package dev.icebear.yac.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteInlayRendererTest {
    @Test
    fun keepsTheNoteLines() {
        assertEquals(listOf("One.", "Two."), NoteInlayRenderer.displayLines("One.\nTwo."))
    }

    @Test
    fun longNotesAreCutAfterThreeLines() {
        assertEquals(listOf("1", "2", "3…"), NoteInlayRenderer.displayLines("1\n2\n3\n4\n5"))
        assertEquals(listOf("1", "2", "3"), NoteInlayRenderer.displayLines("1\n2\n3"))
    }
}
