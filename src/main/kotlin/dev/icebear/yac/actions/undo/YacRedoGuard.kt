package dev.icebear.yac.actions.undo

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.GlobalUndoableAction

internal class YacRedoGuard(private val replay: ByteReplay, documents: Array<DocumentReference>) : GlobalUndoableAction(*documents) {
    override fun undo() = Unit

    override fun redo() = replay.verifyRedo()
}
