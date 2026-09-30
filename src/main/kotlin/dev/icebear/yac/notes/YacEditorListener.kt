package dev.icebear.yac.notes

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import dev.icebear.yac.presentation.NotesPresenter

class YacEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        val notes = project.service<YacNotesService>()
        notes.showCached(event.editor)
        notes.scheduleRefresh(event.editor.document, 0)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        NotesPresenter.clear(event.editor)
    }
}
