package dev.icebear.yac.notes

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacNotifier
import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.YacCliException
import dev.icebear.yac.presentation.NotesPresenter
import dev.icebear.yac.settings.YacSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class YacNotesService(private val project: Project, private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<Document, Job>()

    fun scheduleRefresh(document: Document, delayMillis: Long = DEBOUNCE_MILLIS): Job {
        val job = scope.launch {
            delay(delayMillis)
            refresh(document)
        }
        jobs.put(document, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(document, job) }

        return job
    }

    fun showCached(editor: Editor) {
        editor.document.getUserData(NOTES)?.let { NotesPresenter.render(editor, it, shouldShowInlays()) }
    }

    fun redrawOpenEditors() {
        openEditors().forEach { showCached(it) }
    }

    fun refreshOpenEditors(delayMillis: Long = 0) {
        openEditors().map { it.document }.distinct().forEach { scheduleRefresh(it, delayMillis) }
    }

    private fun openEditors(): List<Editor> = EditorFactory.getInstance().allEditors.filter { project == it.project }

    private suspend fun refresh(document: Document) {
        val request = readAction { request(document) } ?: return
        val notes = withContext(Dispatchers.IO) { resolve(request) }

        withContext(Dispatchers.EDT) {
            if (!document.immutableCharSequence.contentEquals(request.text)) {
                return@withContext
            }

            val shown = NotesSelection.toShow(notes, document.getUserData(NOTES)) ?: return@withContext
            document.putUserData(NOTES, shown)
            EditorFactory.getInstance().getEditors(document, project).forEach { NotesPresenter.render(it, shown, shouldShowInlays()) }
        }
    }

    private fun request(document: Document): RefreshRequest? {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        if (PHP_EXTENSION != file.extension || !file.isInLocalFileSystem) {
            return null
        }

        val environment = YacEnvironment.of(project, file)?.takeIf { it.isInitialized }

        return RefreshRequest(environment, environment?.sourceOf(file), document.text)
    }

    private suspend fun resolve(request: RefreshRequest): List<Note> {
        val environment = request.environment ?: return emptyList()
        val source = request.source ?: return emptyList()
        val failureKey = FAILURE_KEY + environment.root.path
        val cli = environment.cli
        if (null == cli) {
            notifier().report(failureKey, listOf(NO_YAC), NotificationType.WARNING)

            return emptyList()
        }

        if (environment.isRemoteInterpreterSkipped) {
            notifier().notifyOnce(REMOTE_INTERPRETER, NotificationType.INFORMATION)
        }

        val job = currentCoroutineContext().job

        return try {
            val result = cli.context(source, request.text) { job.ensureActive() }
            notifier().report(failureKey, emptyList(), NotificationType.WARNING)
            notifier().report(WARNINGS_KEY + environment.root.path + PATH_SEPARATOR + source, result.warnings, NotificationType.WARNING)
            result.files.flatMap { it.annotations }
        } catch (exception: YacCliException) {
            notifier().report(failureKey, listOf(exception.message.orEmpty()), NotificationType.WARNING)
            emptyList()
        } catch (exception: ExecutionException) {
            notifier().report(failureKey, listOf(CANNOT_RUN + (exception.message ?: exception.javaClass.simpleName)), NotificationType.WARNING)
            emptyList()
        }
    }

    private fun notifier(): YacNotifier = project.service<YacNotifier>()

    private fun shouldShowInlays(): Boolean = project.service<YacSettings>().shouldShowInlays

    private class RefreshRequest(val environment: YacEnvironment?, val source: String?, val text: String)

    companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val NO_YAC = "No yac binary found; run composer require --dev icebear/yac or set the path in Settings | Tools | YAC."
        const val REMOTE_INTERPRETER = "The project PHP interpreter is remote; yac runs with php from the PATH. Set a local PHP executable in Settings | Tools | YAC."
        const val CANNOT_RUN = "Cannot run yac: "
        private const val PHP_EXTENSION = "php"
        private const val FAILURE_KEY = "failure:"
        private const val WARNINGS_KEY = "warnings:"
        private const val PATH_SEPARATOR = "/"
        private val NOTES = Key.create<List<Note>>("yac.notes")
    }
}
