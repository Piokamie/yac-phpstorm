package dev.icebear.yac.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacNotifier
import dev.icebear.yac.cli.YacProcessResult
import dev.icebear.yac.notes.YacNotesService

class YacCommandRunner(private val project: Project) {
    fun run(environment: YacEnvironment, title: String, arguments: List<String>, changedFiles: List<VirtualFile>) {
        val cli = environment.cli
        if (null == cli) {
            notifier().notify(YacMessages.NO_YAC, NotificationType.WARNING)

            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()

        object : Task.Backgroundable(project, title, true) {
            private var result: YacProcessResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = cli.run(arguments, checkCanceled = indicator::checkCanceled)
            }

            override fun onSuccess() {
                result?.let { finish(environment, it, changedFiles) }
            }

            override fun onCancel() {
                refresh(environment, changedFiles)
                notifier().notify(YacMessages.CANCELLED, NotificationType.WARNING)
            }

            override fun onThrowable(error: Throwable) {
                notifier().notify(YacMessages.CANNOT_RUN + (error.message ?: error.javaClass.simpleName), NotificationType.ERROR)
            }
        }.queue()
    }

    internal fun finish(environment: YacEnvironment, result: YacProcessResult, changedFiles: List<VirtualFile>) {
        refresh(environment, changedFiles)
        notifier().notify(result.summary, notificationType(result))
    }

    private fun refresh(environment: YacEnvironment, changedFiles: List<VirtualFile>) {
        val yacDirectory = environment.yacDirectory
        val files = (changedFiles + listOfNotNull(yacDirectory)).toTypedArray()
        val refreshEditors = Runnable { project.service<YacNotesService>().refreshOpenEditors() }
        if (files.isNotEmpty()) {
            VfsUtil.markDirty(true, true, *files)
            RefreshQueue.getInstance().refresh(true, true, refreshEditors, *files)
        }

        if (null == yacDirectory) {
            VfsUtil.markDirty(false, true, environment.root)
            RefreshQueue.getInstance().refresh(true, false, refreshEditors, environment.root)
        }
    }

    private fun notifier(): YacNotifier = project.service<YacNotifier>()

    companion object {
        internal fun notificationType(result: YacProcessResult): NotificationType = when (result.exitCode) {
            YacProcessResult.SUCCESS -> NotificationType.INFORMATION
            YacProcessResult.FAILURE -> NotificationType.WARNING
            else -> NotificationType.ERROR
        }
    }
}
