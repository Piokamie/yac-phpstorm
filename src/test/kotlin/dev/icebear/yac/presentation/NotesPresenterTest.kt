package dev.icebear.yac.presentation

import com.intellij.openapi.editor.Editor
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.NoteStatus

class NotesPresenterTest : YacTestCase() {
    private fun editor(): Editor {
        myFixture.configureByText("Foo.txt", "<?php\n\nfunction a()\n{\n    foo();\n    bar();\n}\n")

        return myFixture.editor
    }

    private fun note(id: String, line: Int?, status: String = NoteStatus.RESOLVED, comment: String = "Note $id.") =
        Note(id, line, status, "a", comment)

    private fun gutters(editor: Editor): Map<Int, List<String>> = editor.markupModel.allHighlighters
        .mapNotNull { highlighter -> (highlighter.gutterIconRenderer as? NoteGutterIconRenderer)?.let { editor.document.getLineNumber(highlighter.startOffset) to it } }
        .associate { (line, renderer) -> line to renderer.notes.map { it.id } }

    private fun inlays(editor: Editor): List<Pair<Int, String>> = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, NoteInlayRenderer::class.java)
        .map { editor.document.getLineNumber(it.offset) to it.renderer.note.id }

    fun testIconsOnNoteLinesAndBlocksAboveResolvedStatements() {
        val editor = editor()

        NotesPresenter.render(editor, listOf(note("B", 6), note("A", 5), note("C", 5), note("O", null, "orphaned")), true)

        assertEquals(mapOf(0 to listOf("O"), 4 to listOf("A", "C"), 5 to listOf("B")), gutters(editor))
        assertEquals(listOf(4 to "A", 4 to "C", 5 to "B"), inlays(editor))
        assertTrue(editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, NoteInlayRenderer::class.java).all { it.properties.isShownAbove })
    }

    fun testProblemIconWhenAnyNoteOnTheLineIsUnresolved() {
        val editor = editor()

        NotesPresenter.render(editor, listOf(note("A", 1), note("O", null, "ambiguous")), true)

        val renderer = editor.markupModel.allHighlighters.firstNotNullOf { it.gutterIconRenderer as? NoteGutterIconRenderer }
        assertSame(YacIcons.NOTE_PROBLEM, renderer.icon)
        assertEquals("<html><b>A · a</b><br>Note A.<hr><b>O · a · ambiguous</b><br>Note O.</html>", renderer.tooltipText)
    }

    fun testRenderReplacesThePreviousPresentationAndClearRemovesIt() {
        val editor = editor()
        NotesPresenter.render(editor, listOf(note("A", 5), note("B", 6)), true)

        NotesPresenter.render(editor, listOf(note("C", 3)), false)

        assertEquals(mapOf(2 to listOf("C")), gutters(editor))
        assertEquals(emptyList<Pair<Int, String>>(), inlays(editor))

        NotesPresenter.clear(editor)

        assertEquals(emptyMap<Int, List<String>>(), gutters(editor))
    }

    fun testLinesBeyondTheDocumentAreClamped() {
        val editor = editor()

        NotesPresenter.render(editor, listOf(note("A", 99)), true)

        assertEquals(mapOf(editor.document.lineCount - 1 to listOf("A")), gutters(editor))
    }

    fun testTooltipEscapesHtml() {
        val renderer = NoteGutterIconRenderer(listOf(note("A", 1, comment = "Use <b> & keep\nlines.")), null)

        assertEquals("<html><b>A · a</b><br>Use &lt;b&gt; &amp; keep<br>lines.</html>", renderer.tooltipText)
        assertSame(YacIcons.NOTE, renderer.icon)
    }

    fun testGutterPopupOffersPromoteAndRemovePerNote() {
        val single = NoteGutterIconRenderer(listOf(note("A", 1)), null)
        val several = NoteGutterIconRenderer(listOf(note("A", 1, comment = "A first line that is far too long to show in a menu.\nSecond."), note("B", 1, comment = "Use _mnemonic_free labels.")), null)

        assertEquals(listOf("PromoteNoteAction", "RemoveNoteAction"), single.popupMenuActions.getChildren(null).map { it.javaClass.simpleName })
        assertEquals(
            listOf("A first line that is far too long to sho…", "Use _mnemonic_free labels."),
            several.popupMenuActions.getChildren(null).map { it.templatePresentation.text },
        )
    }

    fun testBlankCommentsShowTheNoteIdInBlocksAndMenus() {
        val editor = editor()

        NotesPresenter.render(editor, listOf(note("A", 5, comment = " "), note("B", 6)), true)

        assertEquals(
            listOf(listOf("A"), listOf("Note B.")),
            editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, NoteInlayRenderer::class.java).map { it.renderer.lines },
        )
        assertEquals(
            listOf("A", "Note B."),
            NoteGutterIconRenderer(listOf(note("A", 1, comment = ""), note("B", 1)), null).popupMenuActions.getChildren(null).map { it.templatePresentation.text },
        )
    }

    fun testAccessibleNamesArePlainText() {
        val resolved = note("A", 1, comment = "Use <b> here.\nSecond.")
        val orphaned = note("O", null, "orphaned")

        assertEquals(
            listOf(
                "YAC note: Use <b> here.",
                "YAC note with a problem: Note O.",
                "YAC notes (2)",
                "YAC notes with problems (2)",
            ),
            listOf(
                NoteGutterIconRenderer(listOf(resolved), null),
                NoteGutterIconRenderer(listOf(orphaned), null),
                NoteGutterIconRenderer(listOf(resolved, note("B", 1)), null),
                NoteGutterIconRenderer(listOf(resolved, orphaned), null),
            ).map { it.accessibleName },
        )
    }

    fun testGutterIconsKnowTheirFile() {
        val editor = editor()

        NotesPresenter.render(editor, listOf(note("A", 1)), true)

        assertEquals(listOf(myFixture.file.virtualFile), editor.markupModel.allHighlighters.mapNotNull { (it.gutterIconRenderer as? NoteGutterIconRenderer)?.file })
    }
}
