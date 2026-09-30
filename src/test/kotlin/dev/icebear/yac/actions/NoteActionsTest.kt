package dev.icebear.yac.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.NoteStatus

class NoteActionsTest : YacTestCase() {
    private fun isEnabled(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)

        return event.presentation.isEnabled
    }

    fun testEnabledOnlyForAFileWithYacAndPromoteOnlyForResolvedNotes() {
        val resolved = Note("yac_01", 2, NoteStatus.RESOLVED, null, "Note.")
        val orphaned = Note("yac_02", null, "orphaned", null, "Note.")
        val withYac = TemporaryTree.create().directory(".yac").fakeYac().file("A.php").find("A.php")
        val withoutYac = TemporaryTree.create().directory(".yac").file("A.php").find("A.php")

        assertEquals(
            listOf(true, false, true, true, false, false, false),
            listOf(
                PromoteNoteAction(resolved, withYac),
                PromoteNoteAction(orphaned, withYac),
                RemoveNoteAction(orphaned, withYac),
                RemoveNoteAction(resolved, withYac),
                RemoveNoteAction(resolved, withoutYac),
                PromoteNoteAction(resolved, null),
                RemoveNoteAction(resolved, null),
            ).map { isEnabled(it) },
        )
    }
}
