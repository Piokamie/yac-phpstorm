package dev.icebear.yac.notes

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import dev.icebear.yac.presentation.NotesPresenter

class YacEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        if (EditorKind.MAIN_EDITOR != event.editor.editorKind) {
            return
        }

        val project = event.editor.project ?: return
        val notes = project.service<YacNotesService>()
        notes.showCached(event.editor)
        notes.scheduleRefresh(event.editor.document, YacNotesService.IMMEDIATELY)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        NotesPresenter.clear(event.editor)
    }
}
