package dev.icebear.yac.notes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import dev.icebear.yac.YacEnvironment

class YacSidecarListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val notes = project.service<YacNotesService>()
        if (events.any { isYacChange(it.path) }) {
            notes.refreshOpenEditors(YacNotesService.DEBOUNCE_MILLIS)
        }

        events.mapNotNull { relocated(it) }.forEach { notes.refreshFile(it) }
    }

    private fun relocated(event: VFileEvent): VirtualFile? = when {
        event is VFileMoveEvent -> event.file
        event is VFilePropertyChangeEvent && VirtualFile.PROP_NAME == event.propertyName -> event.file
        else -> null
    }

    companion object {
        private const val YAC_SEGMENT = "/" + YacEnvironment.YAC_DIRECTORY
        private const val YAC_CONTENTS = "$YAC_SEGMENT/"
        private const val CACHE_SEGMENT = YAC_CONTENTS + YacEnvironment.CACHE_DIRECTORY
        private const val CACHE_CONTENTS = "$CACHE_SEGMENT/"
        private const val LOCK_PATH = YAC_CONTENTS + YacEnvironment.LOCK_FILE
        private const val BINARY = "/" + YacEnvironment.REPOSITORY_YAC

        internal fun isYacChange(path: String): Boolean {
            if (path.endsWith(BINARY)) {
                return true
            }

            val isCache = path.endsWith(CACHE_SEGMENT) || path.contains(CACHE_CONTENTS)

            return (path.endsWith(YAC_SEGMENT) || path.contains(YAC_CONTENTS)) && !isCache && !path.endsWith(LOCK_PATH)
        }
    }
}
