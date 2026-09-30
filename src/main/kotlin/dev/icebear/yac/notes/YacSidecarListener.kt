package dev.icebear.yac.notes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class YacSidecarListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any { isYacChange(it.path) }) {
            project.service<YacNotesService>().refreshOpenEditors(YacNotesService.DEBOUNCE_MILLIS)
        }
    }

    companion object {
        private const val YAC_DIRECTORY = "/.yac"
        private const val CACHE_DIRECTORY = "/.yac/.cache/"
        private const val SIDECAR_EXTENSION = ".yac"

        fun isYacChange(path: String): Boolean =
            path.endsWith(YAC_DIRECTORY) || path.contains("$YAC_DIRECTORY/") && path.endsWith(SIDECAR_EXTENSION) && !path.contains(CACHE_DIRECTORY)
    }
}
