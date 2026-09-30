package dev.icebear.yac.notes

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.presentation.NoteGutterIconRenderer
import dev.icebear.yac.presentation.NoteInlayRenderer
import dev.icebear.yac.settings.YacSettings

class YacNotesServiceTest : YacTestCase() {
    private val editors = mutableListOf<Editor>()

    override fun tearDown() {
        try {
            editors.forEach { EditorFactory.getInstance().releaseEditor(it) }
            editors.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun open(file: VirtualFile): Editor {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val editor = EditorFactory.getInstance().createEditor(document, project, file, false, EditorKind.MAIN_EDITOR)
        editors.add(editor)

        return editor
    }

    private fun open(tree: TemporaryTree, name: String = "ok.php"): Editor = open(tree.find(name))

    private fun refresh(editor: Editor) {
        val job = project.service<YacNotesService>().scheduleRefresh(editor.document, YacNotesService.IMMEDIATELY)
        PlatformTestUtil.waitWithEventsDispatching("refresh did not finish", { job.isCompleted }, 10)
    }

    private fun gutters(editor: Editor): List<RangeHighlighter> = editor.markupModel.allHighlighters.filter { it.gutterIconRenderer is NoteGutterIconRenderer }

    private fun shownIds(editor: Editor): List<List<String>> = gutters(editor).map { (it.gutterIconRenderer as NoteGutterIconRenderer).notes.map { note -> note.id } }

    private fun inlayTexts(editor: Editor): List<String> =
        editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength, NoteInlayRenderer::class.java).map { it.renderer.note.comment }

    fun testNotesAreResolvedAgainstTheUnsavedBuffer() {
        useFakePhp()
        val received = collectNotifications()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("<?php bar();") }

        refresh(editor)

        assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))
        assertEquals(listOf("<?php bar();"), inlayTexts(editor))
        assertEquals(listOf(WARNING), received.map { it.content })

        refresh(editor)

        assertEquals(listOf(WARNING), received.map { it.content })
    }

    fun testUnparseableBufferKeepsTheShownNotesUntouched() {
        useFakePhp()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        refresh(editor)
        val shown = gutters(editor)
        assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))

        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("BROKEN") }
        refresh(editor)

        assertEquals(shown, gutters(editor))
        assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))
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
        assertEquals(listOf(WARNING, CANNOT_RUN), received.map { it.content.substringBefore(": ") })

        useFakePhp()
        refresh(editor)

        assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))

        settings.state.phpPath = "/does/not/exist/php"
        refresh(editor)

        assertEquals(listOf(WARNING, CANNOT_RUN, CANNOT_RUN), received.map { it.content.substringBefore(": ") })
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

        assertEquals(listOf(YacMessages.NO_YAC), received.map { it.content })
    }

    fun testRenamingTheOpenFileAwayFromPhpClearsItsNotes() {
        useFakePhp()
        val tree = TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();")
        val editor = open(tree)
        refresh(editor)
        assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))
        assertEquals(listOf("<?php foo();"), inlayTexts(editor))

        WriteAction.runAndWait<Exception> { tree.find("ok.php").rename(this, "ok.txt") }
        PlatformTestUtil.waitWithEventsDispatching("notes were not cleared", { gutters(editor).isEmpty() }, 10)

        assertEquals(emptyList<List<String>>(), shownIds(editor))
        assertEquals(emptyList<String>(), inlayTexts(editor))
    }

    fun testUpperCaseExtensionsAreResolved() {
        useFakePhp()
        val received = collectNotifications()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("OK.PHP", "<?php foo();"), "OK.PHP")

        refresh(editor)

        assertEquals(listOf("yac context returned no usable JSON (exit 1): args:context OK.PHP --stdin --format=json…"), received.map { it.content })
    }

    fun testOnlyMainEditorsShowNotes() {
        useFakePhp()
        val editor = open(TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();"))
        val preview = EditorFactory.getInstance().createViewer(editor.document, project, EditorKind.PREVIEW)

        try {
            refresh(editor)

            assertEquals(listOf(listOf(NOTE_ID)), shownIds(editor))
            assertEquals(emptyList<List<String>>(), shownIds(preview))
            assertEquals(emptyList<String>(), inlayTexts(preview))
        } finally {
            EditorFactory.getInstance().releaseEditor(preview)
        }
    }

    fun testRefreshLoadsTheSidecarTreeSoLaterChangesProduceEvents() {
        useFakePhp()
        val tree = TemporaryTree.create().directory(".yac/src/deep").fakeYac().file("ok.php", "<?php foo();")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tree.path.resolve("ok.php"))!!
        val yacDirectory = file.parent.findChild(".yac") as NewVirtualFile
        assertNull(yacDirectory.findChildIfCached("src"))

        refresh(open(file))

        val source = yacDirectory.findChildIfCached("src")
        assertEquals("src", source?.name)
        assertEquals("deep", (source as NewVirtualFile).findChildIfCached("deep")?.name)
    }

    private companion object {
        const val NOTE_ID = "yac_01JB8M3Z4XAAAAAAAAAAAAAAAA"
        const val WARNING = "Sidecar .yac/x.php.yac is invalid"
        const val CANNOT_RUN = "Cannot run yac"
    }
}
