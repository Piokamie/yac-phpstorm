package dev.icebear.yac.notes

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class YacDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        EditorFactory.getInstance().getEditors(event.document)
            .filter { EditorKind.MAIN_EDITOR == it.editorKind }
            .mapNotNull { it.project }
            .distinct()
            .forEach { it.service<YacNotesService>().scheduleRefresh(event.document) }
    }
}
