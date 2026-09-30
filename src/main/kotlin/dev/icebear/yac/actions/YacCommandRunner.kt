package dev.icebear.yac.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacNotifications
import dev.icebear.yac.cli.YacProcessResult
import dev.icebear.yac.notes.YacNotesService

class YacCommandRunner(private val project: Project) {
    fun run(title: String, arguments: List<String>, changedFiles: List<VirtualFile>) {
        val environment = YacEnvironment.of(project) ?: return
        FileDocumentManager.getInstance().saveAllDocuments()

        object : Task.Backgroundable(project, title, false) {
            private var result: YacProcessResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = environment.cli.run(arguments)
            }

            override fun onSuccess() {
                result?.let { finish(it, changedFiles) }
            }

            override fun onThrowable(error: Throwable) {
                YacNotifications.notify(project, "yac failed: ${error.message}", NotificationType.ERROR)
            }
        }.queue()
    }

    fun finish(result: YacProcessResult, changedFiles: List<VirtualFile>) {
        if (changedFiles.isNotEmpty()) {
            VfsUtil.markDirtyAndRefresh(false, true, true, *changedFiles.toTypedArray())
        }

        project.service<YacNotesService>().refreshOpenEditors()
        YacNotifications.notify(project, summary(result), notificationType(result))
    }

    companion object {
        private const val TIMED_OUT = "yac did not finish in time."

        fun notificationType(result: YacProcessResult): NotificationType = when {
            result.isTimedOut -> NotificationType.ERROR
            YacProcessResult.SUCCESS == result.exitCode -> NotificationType.INFORMATION
            YacProcessResult.FAILURE == result.exitCode -> NotificationType.WARNING
            else -> NotificationType.ERROR
        }

        fun summary(result: YacProcessResult): String = when {
            result.isTimedOut -> TIMED_OUT
            YacProcessResult.SUCCESS == result.exitCode -> result.stdout.trim()
            else -> result.message
        }
    }
}
