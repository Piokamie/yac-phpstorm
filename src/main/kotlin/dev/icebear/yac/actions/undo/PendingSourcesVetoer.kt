package dev.icebear.yac.actions.undo

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.vfs.VirtualFile

class PendingSourcesVetoer : FileDocumentSynchronizationVetoer() {
    override fun mayReloadFileContent(file: VirtualFile, document: Document): Boolean = !PendingSources.isHeld(file)

    override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
        val file = FileDocumentManager.getInstance().getFile(document)

        return null == file || !PendingSources.isHeld(file)
    }
}
