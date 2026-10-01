package dev.icebear.yac.actions.undo

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacTestCase
import java.nio.file.Files

class PendingSourcesVetoerTest : YacTestCase() {
    private fun pending(): PendingSources = project.service<PendingSources>()

    fun testOnlyHeldFilesAreVetoed() {
        val tree = TemporaryTree.create().file("a.php", "one\n").file("b.php", "one\n")
        val held = tree.find("a.php")
        val other = tree.find("b.php")
        val documents = listOf(held, other).map { FileDocumentManager.getInstance().getDocument(it)!! }
        val vetoer = PendingSourcesVetoer()

        pending().hold(listOf(held))
        val whileHeld = listOf(vetoer.mayReloadFileContent(held, documents[0]), vetoer.mayReloadFileContent(other, documents[1]))
        val savingWhileHeld = listOf(vetoer.maySaveDocument(documents[0], false), vetoer.maySaveDocument(documents[0], true), vetoer.maySaveDocument(documents[1], false))
        pending().release(listOf(held))
        val afterwards = listOf(vetoer.mayReloadFileContent(held, documents[0]), vetoer.maySaveDocument(documents[0], false))

        assertEquals(listOf(false, true), whileHeld)
        assertEquals(listOf(false, false, true), savingWhileHeld)
        assertEquals(listOf(true, true), afterwards)
    }

    fun testTwoOverlappingHoldsOfOneFileStayVetoedUntilBothAreReleased() {
        val tree = TemporaryTree.create().file("a.php", "one\n")
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val vetoer = PendingSourcesVetoer()
        val otherProject = PendingSources()

        pending().hold(listOf(file))
        otherProject.hold(listOf(file))
        pending().hold(listOf(file))
        val states = mutableListOf<Boolean>()
        pending().release(listOf(file))
        states.add(vetoer.mayReloadFileContent(file, document))
        pending().release(listOf(file))
        states.add(vetoer.mayReloadFileContent(file, document))
        pending().release(listOf(file))
        states.add(vetoer.mayReloadFileContent(file, document))
        otherProject.release(listOf(file))
        states.add(vetoer.mayReloadFileContent(file, document))

        assertEquals(listOf(false, false, false, true), states)
    }

    fun testReleasingAFileThatWasNeverHeldDropsNothingHeldByAnotherProject() {
        val tree = TemporaryTree.create().file("a.php", "one\n")
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val vetoer = PendingSourcesVetoer()
        val otherProject = PendingSources()

        otherProject.hold(listOf(file))
        pending().release(listOf(file))
        val whileOtherHolds = vetoer.mayReloadFileContent(file, document)
        otherProject.release(listOf(file))

        assertFalse(whileOtherHolds)
        assertTrue(vetoer.mayReloadFileContent(file, document))
    }

    fun testDisposingAProjectReleasesEverythingItHoldsAndNothingOfOtherProjects() {
        val tree = TemporaryTree.create().file("a.php", "one\n").file("b.php", "one\n")
        val first = tree.find("a.php")
        val second = tree.find("b.php")
        val documents = listOf(first, second).map { FileDocumentManager.getInstance().getDocument(it)!! }
        val vetoer = PendingSourcesVetoer()
        val closing = PendingSources()

        closing.hold(listOf(first, first, second))
        pending().hold(listOf(second))
        closing.dispose()
        val states = listOf(vetoer.mayReloadFileContent(first, documents[0]), vetoer.mayReloadFileContent(second, documents[1]))
        pending().release(listOf(second))

        assertEquals(listOf(true, false), states)
        assertTrue(vetoer.mayReloadFileContent(second, documents[1]))
    }

    fun testAHeldFileIsNotAutoSavedUntilItIsReleased() {
        val tree = TemporaryTree.create().file("a.php", "one\n")
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("typed\n") }

        pending().hold(listOf(file))
        FileDocumentManager.getInstance().saveAllDocuments()
        val whileHeld = Files.readString(tree.path.resolve("a.php"))
        pending().release(listOf(file))
        FileDocumentManager.getInstance().saveAllDocuments()

        assertEquals("one\n", whileHeld)
        assertEquals("typed\n", Files.readString(tree.path.resolve("a.php")))
    }

    fun testARefreshDoesNotReloadTheDocumentOfAHeldFile() {
        val tree = TemporaryTree.create().file("a.php", "one\n")
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        pending().hold(listOf(file))
        try {
            tree.file("a.php", "twotwo\n")
            file.refresh(false, false)

            assertEquals(listOf(7L, "twotwo\n"), listOf(file.length, String(file.contentsToByteArray(), Charsets.UTF_8)))
            assertEquals("one\n", document.text)
        } finally {
            pending().release(listOf(file))
        }

        tree.file("a.php", "three three\n")
        file.refresh(false, false)

        assertEquals("three three\n", document.text)
    }
}
