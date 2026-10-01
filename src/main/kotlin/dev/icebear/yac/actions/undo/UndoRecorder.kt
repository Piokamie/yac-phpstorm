package dev.icebear.yac.actions.undo

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class UndoRecorder(private val project: Project) {
    fun record(title: String, plan: UndoPlan, relatedFiles: List<VirtualFile>): Boolean {
        if (plan.changes.isEmpty()) {
            return true
        }

        if (plan.edits.any { it.before != it.source.document.text.toString() }) {
            return false
        }

        val documents = plan.edits.map { it.source.document }
        val references = (documents + relatedFiles.mapNotNull { FileDocumentManager.getInstance().getCachedDocument(it) })
            .distinct()
            .map { DocumentReferenceManager.getInstance().create(it) }
            .toTypedArray()
        val undoManager = UndoManager.getInstance(project)
        val replay = ByteReplay(project, plan.root, plan.replays, plan.directories)

        WriteCommandAction.writeCommandAction(project).withName(title).run<RuntimeException> {
            undoManager.undoableActionPerformed(YacRedoGuard(replay, references))
            plan.edits.forEach { edit -> edit.splice?.let { edit.source.document.replaceString(it.start, it.end, it.replacement) } }
            undoManager.undoableActionPerformed(YacUndoableAction(project, replay, references, documents))
        }

        documents.forEach { FileDocumentManager.getInstance().saveDocument(it) }

        return true
    }
}
