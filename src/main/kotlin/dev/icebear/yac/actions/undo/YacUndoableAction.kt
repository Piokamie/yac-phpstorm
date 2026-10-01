package dev.icebear.yac.actions.undo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.GlobalUndoableAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

internal class YacUndoableAction(
    private val project: Project,
    private val replay: ByteReplay,
    documents: Array<DocumentReference>,
    private val saved: List<Document>,
) : GlobalUndoableAction(*documents) {
    override fun undo() {
        replay.undo()
        saveLater()
    }

    override fun redo() {
        replay.redo()
        saveLater()
    }

    private fun saveLater() {
        ApplicationManager.getApplication().invokeLater({ saved.forEach { FileDocumentManager.getInstance().saveDocument(it) } }, project.disposed)
    }
}
