package dev.icebear.yac.actions.undo

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestLoggerFactory
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacTestCase
import org.junit.Assert
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class UndoRecorderTest : YacTestCase() {
    private fun load(root: VirtualFile, sources: List<String>): List<SourceDocument> {
        var documents: List<SourceDocument> = emptyList()
        ProgressManager.getInstance().runProcess({ documents = SourceDocument.load(root, sources) }, EmptyProgressIndicator())

        return documents
    }

    private fun bytes(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)

    private fun change(before: String, after: String): FileChange = FileChange(bytes(before), bytes(after))

    private fun plan(tree: TemporaryTree, changes: Map<String, FileChange>, documents: List<SourceDocument>): UndoPlan = UndoPlan.of(tree.path, changes, setOf(".yac"), documents)

    fun testLoadFindsTheDocumentsOfExistingSourceFilesOnly() {
        val tree = TemporaryTree.create().file("a.php", "<?php\n").file("src/B.php", "<?php b\n").directory("lib")

        val documents = load(tree.find(), listOf("a.php", "src/B.php", "lib", "missing.php"))

        assertEquals(listOf("a.php", "src/B.php"), documents.map { it.path })
        assertEquals(listOf("<?php\n", "<?php b\n"), documents.map { it.document.text.toString() })
        assertEquals(listOf(tree.find("a.php"), tree.find("src/B.php")), documents.map { it.file })
    }

    fun testNoChangesRegistersNothing() {
        val tree = TemporaryTree.create().file("a.php", "<?php\n")
        val file = tree.find("a.php")
        val undoManager = UndoManager.getInstance(project)

        assertTrue(UndoRecorder(project).record("Nothing", plan(tree, emptyMap(), load(tree.find(), listOf("a.php"))), listOf(file)))

        assertEquals("<?php\n", Files.readString(tree.path.resolve("a.php")))
        assertFalse(undoManager.isUndoAvailable(null))
    }

    fun testASourceWithCarriageReturnsIsEditedAsNormalisedTextAndSavedWithTheSameSeparators() {
        val before = "<?php\r\nfoo();\r\n"
        val after = "<?php\r\n/** x */\r\nfoo();\r\n"
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a1").file("a.php", before)
        val documents = load(tree.find(), listOf("a.php"))
        tree.file(".yac/a.php.yac", "a2")

        val recorded = UndoRecorder(project).record("Promoting", plan(tree, mapOf("a.php" to change(before, after), ".yac/a.php.yac" to change("a1", "a2")), documents), emptyList())

        assertTrue(recorded)
        assertEquals("<?php\n/** x */\nfoo();\n", documents.single().document.text)
        assertEquals(after, Files.readString(tree.path.resolve("a.php")))
    }

    fun testADocumentThatNoLongerHoldsTheOldTextRegistersNothingAndIsLeftAlone() {
        val tree = TemporaryTree.create().file(".yac/a.php.yac", "a2").file("a.php", "<?php\nfoo();\n")
        val documents = load(tree.find(), listOf("a.php"))
        val document = documents.single().document
        WriteCommandAction.runWriteCommandAction(project) { document.setText("<?php\nedited();\n") }
        val undoManager = UndoManager.getInstance(project)
        val nameBefore = undoManager.getUndoActionNameAndDescription(null)

        val recorded = UndoRecorder(project).record(
            "Promoting",
            plan(tree, mapOf("a.php" to change("<?php\nfoo();\n", "<?php\nnew();\n"), ".yac/a.php.yac" to change("a1", "a2")), documents),
            emptyList(),
        )

        assertFalse(recorded)
        assertEquals("<?php\nedited();\n", document.text)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertEquals(nameBefore, undoManager.getUndoActionNameAndDescription(null))
    }

    fun testAChangeThatOnlyAddsOrDropsAByteOrderMarkIsReplayedAsBytesNotAsADocumentEdit() {
        val tree = TemporaryTree.create().file("a.php", "<?php\n")
        val documents = load(tree.find(), listOf("a.php"))
        val withMark = FileChange(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes("<?php\n"), bytes("<?php\n"))

        val plan = plan(tree, mapOf("a.php" to withMark), documents)

        assertEquals(emptyList<String>(), plan.edits.map { it.source.path })
        assertEquals(listOf("a.php"), plan.replays.keys.toList())
    }

    fun testAChangeThatOnlyConvertsLineSeparatorsIsReplayedAsBytesNotAsADocumentEdit() {
        val tree = TemporaryTree.create().file("a.php", "<?php\nfoo();\n")
        val documents = load(tree.find(), listOf("a.php"))

        val plan = plan(tree, mapOf("a.php" to change("<?php\r\nfoo();\r\n", "<?php\nfoo();\n")), documents)

        assertEquals(emptyList<String>(), plan.edits.map { it.source.path })
        assertEquals(listOf("a.php"), plan.replays.keys.toList())
    }

    fun testARealTextChangeIsADocumentEditAndOnlyTheOtherFilesAreReplayed() {
        val tree = TemporaryTree.create().file("a.php", "<?php\n")
        val documents = load(tree.find(), listOf("a.php"))

        val plan = plan(tree, mapOf("a.php" to change("<?php\n", "<?php\nfoo();\n"), ".yac/a.php.yac" to change("a1", "a2")), documents)

        assertEquals(listOf("a.php"), plan.edits.map { it.source.path })
        assertEquals(listOf(".yac/a.php.yac"), plan.replays.keys.toList())
    }

    private fun recordEdit(tree: TemporaryTree): Document {
        val documents = load(tree.find(), listOf("a.php"))
        val plan = plan(tree, mapOf("a.php" to change("<?php\n", "<?php\nfoo();\n")), documents)

        assertTrue(UndoRecorder(project).record("Editing", plan, listOf(tree.find("a.php"))))

        return documents.single().document
    }

    private fun refusal(action: () -> Unit): String? {
        val state = project.service<YacRunState>()
        assertTrue(state.tryStart())
        try {
            return Assert.assertThrows(TestLoggerFactory.TestLoggerAssertionError::class.java) { action() }.message
        } finally {
            state.finish()
        }
    }

    fun testAnEditOnlyPlanStillRegistersTheGuardSoUndoIsRefusedWhileYacRuns() {
        val document = recordEdit(TemporaryTree.create().file("a.php", "<?php\n"))
        val undoManager = UndoManager.getInstance(project)

        assertEquals(YacMessages.STILL_RUNNING, refusal { undoManager.undo(null) })

        assertEquals("<?php\nfoo();\n", document.text)
    }

    fun testAnEditOnlyPlanStillRegistersTheGuardSoRedoIsRefusedWhileYacRuns() {
        val document = recordEdit(TemporaryTree.create().file("a.php", "<?php\n"))
        val undoManager = UndoManager.getInstance(project)
        undoManager.undo(null)
        assertEquals("<?php\n", document.text)

        assertEquals(YacMessages.STILL_RUNNING, refusal { undoManager.redo(null) })

        assertEquals("<?php\n", document.text)
    }
}
