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
import dev.icebear.yac.YacNotifications
import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.YacCli
import dev.icebear.yac.cli.YacCliException
import dev.icebear.yac.presentation.NotesPresenter
import dev.icebear.yac.settings.YacSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class YacNotesService(private val project: Project, private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<Document, Job>()

    fun scheduleRefresh(document: Document, delayMillis: Long = DEBOUNCE_MILLIS) {
        val job = scope.launch {
            delay(delayMillis)
            refresh(document)
        }
        jobs.put(document, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(document, job) }
    }

    fun showCached(editor: Editor) {
        editor.document.getUserData(NOTES)?.let { NotesPresenter.render(editor, it, showInlays()) }
    }

    fun refreshOpenEditors() {
        EditorFactory.getInstance().allEditors.filter { project == it.project }.map { it.document }.distinct().forEach { scheduleRefresh(it, 0) }
    }

    private suspend fun refresh(document: Document) {
        val request = readAction { request(document) } ?: return
        val notes = try {
            withContext(Dispatchers.IO) {
                val result = request.cli.context(request.source, request.text)
                result.warnings.forEach { YacNotifications.notifyOnce(project, it, NotificationType.WARNING) }
                result.files.flatMap { it.annotations }
            }
        } catch (exception: YacCliException) {
            YacNotifications.notifyOnce(project, exception.message ?: exception.toString(), NotificationType.WARNING)
            return
        } catch (exception: ExecutionException) {
            YacNotifications.notifyOnce(project, "Cannot run yac: ${exception.message}", NotificationType.WARNING)
            return
        }

        withContext(Dispatchers.EDT) {
            if (document.modificationStamp != request.stamp) {
                return@withContext
            }

            val shown = NotesSelection.toShow(notes, document.getUserData(NOTES))
            document.putUserData(NOTES, shown)
            EditorFactory.getInstance().getEditors(document, project).forEach { NotesPresenter.render(it, shown, showInlays()) }
        }
    }

    private fun request(document: Document): RefreshRequest? {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        if (PHP_EXTENSION != file.extension) {
            return null
        }

        val environment = YacEnvironment.of(project) ?: return null
        val source = environment.sourceOf(file) ?: return null

        return RefreshRequest(environment.cli, source, document.text, document.modificationStamp)
    }

    private fun showInlays(): Boolean = project.service<YacSettings>().showInlays

    private class RefreshRequest(val cli: YacCli, val source: String, val text: String, val stamp: Long)

    companion object {
        const val DEBOUNCE_MILLIS = 500L
        private const val PHP_EXTENSION = "php"
        private val NOTES = Key.create<List<Note>>("yac.notes")
    }
}
