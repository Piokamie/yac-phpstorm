package dev.icebear.yac.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacNotifier
import dev.icebear.yac.actions.undo.DiffPaths
import dev.icebear.yac.actions.undo.DiskSnapshot
import dev.icebear.yac.actions.undo.PendingSources
import dev.icebear.yac.actions.undo.SourceDocument
import dev.icebear.yac.actions.undo.UndoPlan
import dev.icebear.yac.actions.undo.UndoRecorder
import dev.icebear.yac.actions.undo.YacRunState
import dev.icebear.yac.cli.YacCli
import dev.icebear.yac.cli.YacCommands
import dev.icebear.yac.cli.YacProcessResult
import dev.icebear.yac.notes.YacNotesService
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

internal typealias SnapshotCapture = (root: Path, files: List<String>, isYacTreeIncluded: Boolean, checkCanceled: () -> Unit) -> DiskSnapshot

class YacCommandRunner(private val project: Project) {
    fun run(
        environment: YacEnvironment,
        title: String,
        arguments: List<String>,
        changedFiles: List<VirtualFile>,
        supportsDiff: Boolean,
        undoFiles: List<VirtualFile>,
    ) {
        val task = prepare(environment, title, arguments, changedFiles, supportsDiff, undoFiles) ?: return
        try {
            task.queue()
        } catch (error: Throwable) {
            project.service<YacRunState>().finish()
            throw error
        }
    }

    internal fun prepare(
        environment: YacEnvironment,
        title: String,
        arguments: List<String>,
        changedFiles: List<VirtualFile>,
        supportsDiff: Boolean,
        undoFiles: List<VirtualFile>,
        capture: SnapshotCapture = DiskSnapshot.Companion::capture,
    ): Task.Backgroundable? {
        val cli = environment.cli
        if (null == cli) {
            notifier().notify(YacMessages.NO_YAC, NotificationType.WARNING)

            return null
        }

        val state = project.service<YacRunState>()
        if (!state.tryStart()) {
            notifier().notify(YacMessages.ALREADY_RUNNING, NotificationType.WARNING)

            return null
        }

        try {
            FileDocumentManager.getInstance().saveAllDocuments()
            val ownSources = undoFiles.filterNot { it.isDirectory }.mapNotNull { environment.sourceOf(it) }
            val isSingleNote = YacCommands.NOTE_COMMANDS.contains(arguments.firstOrNull()) && 1 == undoFiles.size && 1 == ownSources.size

            return RunTask(environment, cli, title, arguments, changedFiles, supportsDiff, undoFiles, ownSources, if (isSingleNote) YacEnvironment.sidecarOf(ownSources.single()) else null, capture)
        } catch (error: Throwable) {
            state.finish()
            throw error
        }
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

    private class Outcome(val result: YacProcessResult, val plan: UndoPlan?, val warning: String?)

    private inner class RunTask(
        private val environment: YacEnvironment,
        private val cli: YacCli,
        private val title: String,
        private val arguments: List<String>,
        private val changedFiles: List<VirtualFile>,
        private val supportsDiff: Boolean,
        private val undoFiles: List<VirtualFile>,
        private val ownSources: List<String>,
        private val sidecar: String?,
        private val capture: SnapshotCapture,
    ) : Task.Backgroundable(project, title, true) {
        private val held = AtomicReference<List<VirtualFile>>(emptyList())

        @Volatile
        private var isRunStarted = false

        @Volatile
        private var finished: YacProcessResult? = null

        @Volatile
        private var documents: List<SourceDocument> = emptyList()

        @Volatile
        private var outcome: Outcome? = null

        override fun run(indicator: ProgressIndicator) {
            val checkCanceled = indicator::checkCanceled
            val preview = if (supportsDiff) cli.run(arguments + YacCommands.DRY_RUN + YacCommands.DIFF, checkCanceled = checkCanceled) else null
            val isPreviewed = null == preview || isTrusted(preview)
            val listed = if (null != preview && isPreviewed) DiffPaths.of(preview.stdout) else emptyList()
            if (listed.size > MAX_UNDO_SOURCES) {
                outcome = Outcome(execute(checkCanceled), null, YacMessages.tooManyFiles(MAX_UNDO_SOURCES))

                return
            }

            val sources = (listed + ownSources).distinct()
            val directory = environment.root.toNioPath()
            val before = degrade("snapshotting the files before the run") { snapshot(directory, sources, checkCanceled) }
            documents = if (null == before) emptyList() else SourceDocument.load(environment.root, sources)
            held.set(documents.map { it.file })
            project.service<PendingSources>().hold(held.get())
            val result = execute(checkCanceled)

            outcome = outcomeOf(result, before, isPreviewed, directory, sources, checkCanceled)
        }

        private fun execute(checkCanceled: () -> Unit): YacProcessResult {
            isRunStarted = true

            return cli.run(arguments, checkCanceled = checkCanceled).also { finished = it }
        }

        private fun snapshot(directory: Path, sources: List<String>, checkCanceled: () -> Unit): DiskSnapshot {
            val files = if (null == sidecar) sources else sources + sidecar + YacEnvironment.YAC_GITIGNORE

            return capture(directory, files, null == sidecar, checkCanceled)
        }

        private fun outcomeOf(result: YacProcessResult, before: DiskSnapshot?, isPreviewed: Boolean, directory: Path, sources: List<String>, checkCanceled: () -> Unit): Outcome {
            if (null == before) {
                return Outcome(result, null, YacMessages.CAPTURE_FAILED)
            }

            val plan = degrade("snapshotting the files after the run") {
                val after = snapshot(directory, sources, checkCanceled)

                UndoPlan.of(directory, before.changesTo(after), before.directories, documents).also { plan ->
                    if (isPreviewed) {
                        plan.edits.forEach { it.source.file.refresh(false, false) }
                    }
                }
            } ?: return Outcome(result, null, YacMessages.CAPTURE_FAILED)

            return if (!isPreviewed && plan.changes.isNotEmpty()) Outcome(result, null, YacMessages.PREVIEW_FAILED) else Outcome(result, plan, null)
        }

        override fun onSuccess() {
            release()
            val outcome = outcome ?: return
            var warning = outcome.warning
            try {
                if (null == warning && null != outcome.plan) {
                    warning = when (degrade("recording the undo step") { UndoRecorder(project).record(title, outcome.plan, undoFiles) }) {
                        true -> null
                        false -> YacMessages.CANNOT_BE_UNDONE
                        null -> YacMessages.CAPTURE_FAILED
                    }
                }

                if (null != warning) {
                    reloadStaleDocuments()
                }
            } finally {
                finish(environment, outcome.result, changedFiles)
            }

            warning?.let { notifier().notify(it, NotificationType.WARNING) }
        }

        override fun onCancel() {
            release()
            if (!isRunStarted) {
                notifier().notify(YacMessages.CANCELLED_BEFORE_CHANGES, NotificationType.WARNING)

                return
            }

            reloadStaleDocuments()
            val result = finished
            if (null == result) {
                refresh(environment, changedFiles)
                notifier().notify(YacMessages.CANCELLED, NotificationType.WARNING)
            } else {
                finish(environment, result, changedFiles)
                notifier().notify(outcome?.warning ?: YacMessages.CAPTURE_FAILED, NotificationType.WARNING)
            }
        }

        override fun onThrowable(error: Throwable) {
            notifier().notify(YacMessages.CANNOT_RUN + (error.message ?: error.javaClass.simpleName), NotificationType.ERROR)
        }

        override fun onFinished() {
            release()
            project.service<YacRunState>().finish()
        }

        private fun release() {
            project.service<PendingSources>().release(held.getAndSet(emptyList()))
        }

        private fun reloadStaleDocuments() {
            val stale = documents.filter { it.isStale() }
            if (stale.isNotEmpty()) {
                FileDocumentManager.getInstance().reloadFiles(*stale.map { it.file }.toTypedArray())
            }
        }
    }

    private inline fun <T> degrade(what: String, block: () -> T): T? = try {
        block()
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (exception: Exception) {
        LOG.warn("yac: $what failed; this change cannot be undone", exception)

        null
    }

    private fun notifier(): YacNotifier = project.service<YacNotifier>()

    companion object {
        internal const val MAX_UNDO_SOURCES = 200
        private val LOG = logger<YacCommandRunner>()

        private fun isTrusted(preview: YacProcessResult): Boolean =
            YacProcessResult.SUCCESS == preview.exitCode || YacProcessResult.FAILURE == preview.exitCode

        internal fun notificationType(result: YacProcessResult): NotificationType = when (result.exitCode) {
            YacProcessResult.SUCCESS -> NotificationType.INFORMATION
            YacProcessResult.FAILURE -> NotificationType.WARNING
            else -> NotificationType.ERROR
        }
    }
}
