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
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
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
    private val loadedRoots = ConcurrentHashMap.newKeySet<String>()
    private val notesKey = Key.create<List<Note>>(NOTES_KEY_PREFIX + project.locationHash)

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
        editor.document.getUserData(notesKey)?.let { NotesPresenter.render(editor, it, shouldShowInlays()) }
    }

    fun redrawOpenEditors() {
        openEditors().forEach { showCached(it) }
    }

    fun refreshOpenEditors(delayMillis: Long = IMMEDIATELY) {
        openEditors().map { it.document }.distinct().forEach { scheduleRefresh(it, delayMillis) }
    }

    fun refreshFile(file: VirtualFile) {
        FileDocumentManager.getInstance().getCachedDocument(file)
            ?.takeIf { mainEditors(it).isNotEmpty() }
            ?.let { scheduleRefresh(it, IMMEDIATELY) }
    }

    private fun openEditors(): List<Editor> = EditorFactory.getInstance().allEditors.filter { project == it.project && EditorKind.MAIN_EDITOR == it.editorKind }

    private fun mainEditors(document: Document): List<Editor> =
        EditorFactory.getInstance().getEditors(document, project).filter { EditorKind.MAIN_EDITOR == it.editorKind }

    private suspend fun refresh(document: Document) {
        val request = readAction { request(document) }
        if (null == request) {
            clear(document)

            return
        }

        val notes = withContext(Dispatchers.IO) { resolve(request) }

        withContext(Dispatchers.EDT) {
            if (!document.immutableCharSequence.contentEquals(request.text)) {
                return@withContext
            }

            val shown = NotesSelection.toShow(notes, document.getUserData(notesKey)) ?: return@withContext
            document.putUserData(notesKey, shown)
            mainEditors(document).forEach { NotesPresenter.render(it, shown, shouldShowInlays()) }
        }
    }

    private suspend fun clear(document: Document) {
        if (null == document.getUserData(notesKey)) {
            return
        }

        withContext(Dispatchers.EDT) {
            document.putUserData(notesKey, null)
            mainEditors(document).forEach { NotesPresenter.clear(it) }
        }
    }

    private fun request(document: Document): RefreshRequest? {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        if (!PHP_EXTENSION.equals(file.extension, ignoreCase = true)) {
            return null
        }

        val environment = YacEnvironment.of(project, file)?.takeIf { it.isInitialized }

        return RefreshRequest(environment, environment?.sourceOf(file), document.text)
    }

    private suspend fun resolve(request: RefreshRequest): List<Note> {
        val environment = request.environment ?: return emptyList()
        val source = request.source ?: return emptyList()
        loadSidecars(environment)
        val failureKey = FAILURE_KEY + environment.root.path
        val cli = environment.cli
        if (null == cli) {
            notifier().report(failureKey, listOf(YacMessages.NO_YAC), NotificationType.WARNING)

            return emptyList()
        }

        if (environment.isRemoteInterpreterSkipped) {
            notifier().notifyOnce(YacMessages.REMOTE_INTERPRETER, NotificationType.INFORMATION)
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
            notifier().report(failureKey, listOf(YacMessages.CANNOT_RUN + (exception.message ?: exception.javaClass.simpleName)), NotificationType.WARNING)
            emptyList()
        }
    }

    private fun loadSidecars(environment: YacEnvironment) {
        if (loadedRoots.add(environment.root.path)) {
            environment.yacDirectory?.let { VfsUtilCore.visitChildrenRecursively(it, object : VirtualFileVisitor<Unit>() {}) }
        }
    }

    private fun notifier(): YacNotifier = project.service<YacNotifier>()

    private fun shouldShowInlays(): Boolean = project.service<YacSettings>().shouldShowInlays

    private class RefreshRequest(val environment: YacEnvironment?, val source: String?, val text: String)

    companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val IMMEDIATELY = 0L
        private const val PHP_EXTENSION = "php"
        private const val FAILURE_KEY = "failure:"
        private const val WARNINGS_KEY = "warnings:"
        private const val PATH_SEPARATOR = "/"
        private const val NOTES_KEY_PREFIX = "yac.notes."
    }
}
