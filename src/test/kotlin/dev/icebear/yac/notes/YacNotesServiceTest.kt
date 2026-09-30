package dev.icebear.yac.notes

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.PlatformTestUtil
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.presentation.NoteGutterIconRenderer
import dev.icebear.yac.presentation.NoteInlayRenderer
import dev.icebear.yac.settings.YacSettings

class YacNotesServiceTest : YacTestCase() {
    private fun open(tree: TemporaryTree): Editor {
        myFixture.openFileInEditor(tree.find("ok.php"))

        return myFixture.editor
    }

    private fun refresh(editor: Editor) {
        val job = project.service<YacNotesService>().scheduleRefresh(editor.document, 0)
        PlatformTestUtil.waitWithEventsDispatching("refresh did not finish", { job.isCompleted }, 10)
    }

    private fun gutters(editor: Editor): List<RangeHighlighter> = editor.markupModel.allHighlighters.filter { it.gutterIconRenderer is NoteGutterIconRenderer }

    private fun inlayTexts(editor: Editor): List<String> =
        editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, NoteInlayRenderer::class.java).map { it.renderer.note.comment }

    fun testNotesAreResolvedAgainstTheUnsavedBuffer() {
        useFakePhp()
        val received = collectNotifications()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("<?php bar();") }

        refresh(editor)

        assertEquals(listOf(listOf("yac_01JB8M3Z4XAAAAAAAAAAAAAAAA")), gutters(editor).map { (it.gutterIconRenderer as NoteGutterIconRenderer).notes.map { note -> note.id } })
        assertEquals(listOf("<?php bar();"), inlayTexts(editor))
        assertEquals(listOf("Sidecar .yac/x.php.yac is invalid"), received.map { it.content })

        refresh(editor)

        assertEquals(1, received.size)
    }

    fun testUnparseableBufferKeepsTheShownNotesUntouched() {
        useFakePhp()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        refresh(editor)
        val shown = gutters(editor)
        assertEquals(1, shown.size)

        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("BROKEN") }
        refresh(editor)

        assertEquals(shown, gutters(editor))
    }

    fun testFailureClearsNotesAndIsReportedOnceUntilYacWorksAgain() {
        useFakePhp()
        val received = collectNotifications()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        refresh(editor)
        val settings = project.service<YacSettings>()

        settings.state.phpPath = "/does/not/exist/php"
        refresh(editor)
        refresh(editor)

        assertEquals(emptyList<RangeHighlighter>(), gutters(editor))
        assertEquals(1, received.count { it.content.startsWith(YacNotesService.CANNOT_RUN) })

        useFakePhp()
        refresh(editor)

        assertEquals(1, gutters(editor).size)

        settings.state.phpPath = "/does/not/exist/php"
        refresh(editor)

        assertEquals(2, received.count { it.content.startsWith(YacNotesService.CANNOT_RUN) })
    }

    fun testProjectsWithoutYacDirectoryShowNothing() {
        useFakePhp()
        val editor = open(TemporaryTree.create().directory(".git").fakeYac().file("ok.php", "<?php foo();"))

        refresh(editor)

        assertEquals(emptyList<RangeHighlighter>(), gutters(editor))
    }

    fun testMissingBinaryIsReportedWhenTheProjectUsesYac() {
        val received = collectNotifications()
        val editor = open(TemporaryTree.create().directory(".yac").file("ok.php", "<?php foo();"))

        refresh(editor)

        assertEquals(listOf(YacNotesService.NO_YAC), received.map { it.content })
    }
}
