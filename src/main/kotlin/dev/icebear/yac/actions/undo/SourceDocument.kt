package dev.icebear.yac.actions.undo

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

internal class SourceDocument(val path: String, val file: VirtualFile, val document: Document, val text: String) {
    fun isStale(): Boolean {
        if (!file.isValid || text != document.text.toString()) {
            return false
        }

        return try {
            text != decode(file, file.contentsToByteArray())
        } catch (exception: IOException) {
            false
        }
    }

    companion object {
        private const val BYTE_ORDER_MARK = "\uFEFF"

        fun decode(file: VirtualFile, bytes: ByteArray): String =
            StringUtil.convertLineSeparators(String(bytes, file.charset).removePrefix(BYTE_ORDER_MARK))

        fun load(root: VirtualFile, sources: List<String>): List<SourceDocument> = runBlockingCancellable {
            readAction {
                sources.mapNotNull { path ->
                    val file = root.findFileByRelativePath(path)?.takeUnless { it.isDirectory }
                    val document = file?.let { FileDocumentManager.getInstance().getDocument(it) }

                    if (null != file && null != document) SourceDocument(path, file, document, document.text.toString()) else null
                }
            }
        }
    }
}
