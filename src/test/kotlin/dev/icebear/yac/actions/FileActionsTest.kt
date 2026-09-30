package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacTestCase

class FileActionsTest : YacTestCase() {
    private fun isVisible(files: List<VirtualFile>): Boolean {
        val action = ExtractFileAction()
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files.toTypedArray()).build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)

        return event.presentation.isEnabledAndVisible
    }

    fun testTargetsAreSourcesRelativeToOneYacRoot() {
        val tree = TemporaryTree.create().directory(".git").fakeYac().file("src/A.php").file("src/B.php")
        val files = listOf(tree.find(), tree.find("src/A.php"), tree.find("src"))

        val target = FileCommandAction.target(project, files)!!

        assertEquals(tree.find(), target.environment.root)
        assertEquals(listOf(".", "src/A.php", "src"), target.sources)
        assertEquals(files, target.files)
        assertTrue(isVisible(files))
    }

    fun testHiddenWithoutYacAcrossRootsOrWithoutFiles() {
        val first = TemporaryTree.create().directory(".git").fakeYac().file("A.php")
        val second = TemporaryTree.create().directory(".git").fakeYac().file("B.php")
        val withoutYac = TemporaryTree.create().directory(".git").file("C.php")

        assertFalse(isVisible(listOf(first.find("A.php"), second.find("B.php"))))
        assertFalse(isVisible(listOf(withoutYac.find("C.php"))))
        assertFalse(isVisible(emptyList()))
        assertNull(FileCommandAction.target(project, listOf(first.find("A.php"), second.find("B.php"))))
    }

    fun testYeetQuestionNamesTheRootAndCutsLongLists() {
        assertEquals(
            "Delete all YAC notes of:\n\nthe whole project (app)\nsrc/A.php\n\nThe PHP source is not touched.",
            YeetFileAction.question(listOf(".", "src/A.php"), "app"),
        )
        assertEquals(
            "Delete all YAC notes of:\n\n1\n2\n3\n4\n5\nand 2 more\n\nThe PHP source is not touched.",
            YeetFileAction.question(listOf("1", "2", "3", "4", "5", "6", "7"), "app"),
        )
    }
}
