package dev.icebear.yac.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileEditor.impl.MemoryDiskConflictResolver
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestLoggerFactory
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacNotifier
import dev.icebear.yac.actions.undo.DiskSnapshot
import dev.icebear.yac.actions.undo.PendingSources
import dev.icebear.yac.actions.undo.YacRunState
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.cli.YacProcessResult
import dev.icebear.yac.notes.YacNotesService
import dev.icebear.yac.presentation.NoteGutterIconRenderer
import dev.icebear.yac.settings.YacSettings
import org.junit.Assert
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class YacCommandRunnerTest : YacTestCase() {
    private val editors = mutableListOf<Editor>()

    override fun tearDown() {
        try {
            editors.forEach { EditorFactory.getInstance().releaseEditor(it) }
            editors.clear()
        } finally {
            super.tearDown()
        }
    }

    fun testExitCodesMapToNotificationTypes() {
        assertEquals(
            listOf(NotificationType.INFORMATION, NotificationType.WARNING, NotificationType.ERROR),
            listOf(0, 1, 2).map { YacCommandRunner.notificationType(YacProcessResult(it, "", "")) },
        )
    }

    fun testFinishNotifiesWithOutputAndErrors() {
        val received = collectNotifications()
        val tree = TemporaryTree.create().directory(".yac").fakeYac()
        val environment = YacEnvironment.of(project, tree.find())!!

        YacCommandRunner(project).finish(environment, YacProcessResult(1, "Extracted 1 note.\n", "Warning: src/A.php: skipped <x>.\n"), emptyList())

        assertEquals(listOf(NotificationType.WARNING to "Extracted 1 note.<br>src/A.php: skipped &lt;x&gt;."), received.map { it.type to it.content })
    }

    fun testRunSavesDocumentsFirstAndReportsTheResult() {
        useFakePhp()
        val received = collectNotifications()
        val tree = TemporaryTree.create().directory(".yac").fakeYac().file("a.php", "<?php\n")
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("<?php\nfoo();\n") }

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Testing", listOf("remove", "yac_01"), listOf(file), supportsDiff = false, undoFiles = emptyList())

        assertEquals("<?php\nfoo();\n", Files.readString(tree.path.resolve("a.php")))
        PlatformTestUtil.waitWithEventsDispatching("no notification", { contents(received).isNotEmpty() }, 10)
        assertEquals(listOf(NotificationType.WARNING to "args:remove yac_01<br>careful"), received.map { it.type to it.content })
    }

    fun testRunWithoutYacSaysSo() {
        val received = collectNotifications()
        val environment = YacEnvironment.of(project, TemporaryTree.create().directory(".yac").find())!!

        YacCommandRunner(project).run(environment, "Testing", listOf("remove", "yac_01"), emptyList(), supportsDiff = false, undoFiles = emptyList())

        assertEquals(listOf(NotificationType.WARNING to YacMessages.NO_YAC), received.map { it.type to it.content })
    }

    fun testFinishRefreshesOpenEditorsAfterTheVirtualFileRefresh() {
        val tree = TemporaryTree.create().directory(".yac").fakeYac().file("ok.php", "<?php foo();")
        val file = tree.find("ok.php")
        val editor = EditorFactory.getInstance().createEditor(FileDocumentManager.getInstance().getDocument(file)!!, project, file, false, EditorKind.MAIN_EDITOR)
        editors.add(editor)
        project.service<YacSettings>().state.phpPath = "/does/not/exist/php"
        val job = project.service<YacNotesService>().scheduleRefresh(editor.document, YacNotesService.IMMEDIATELY)
        PlatformTestUtil.waitWithEventsDispatching("refresh did not finish", { job.isCompleted }, 10)
        useFakePhp()

        YacCommandRunner(project).finish(YacEnvironment.of(project, file)!!, YacProcessResult(0, "", ""), listOf(file))

        PlatformTestUtil.waitWithEventsDispatching(
            "editors were not refreshed",
            { editor.markupModel.allHighlighters.any { it.gutterIconRenderer is NoteGutterIconRenderer } },
            10,
        )
        assertEquals(
            listOf(listOf("yac_01JB8M3Z4XAAAAAAAAAAAAAAAA")),
            editor.markupModel.allHighlighters.mapNotNull { (it.gutterIconRenderer as? NoteGutterIconRenderer)?.notes?.map { note -> note.id } },
        )
    }

    private fun promoteTree(): TemporaryTree = TemporaryTree.create().directory(".yac").fakeYac().file("a.php", BEFORE_SOURCE).file(".yac/a.php.yac", BEFORE_SIDECAR)

    private fun open(file: VirtualFile): Editor {
        val editor = EditorFactory.getInstance().createEditor(FileDocumentManager.getInstance().getDocument(file)!!, project, file, false, EditorKind.MAIN_EDITOR)
        editors.add(editor)

        return editor
    }

    private fun await(message: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, condition, 10)
    }

    private fun awaitIdle(message: String, condition: () -> Boolean) {
        await(message) { condition() && !project.service<YacRunState>().isRunning }
    }

    private fun contents(received: List<Notification>): List<String> = received.map { it.content }

    private fun run(file: VirtualFile, id: String, undoFiles: List<VirtualFile> = listOf(file)) {
        YacCommandRunner(project).run(
            YacEnvironment.of(project, file)!!,
            "Promoting $id",
            listOf("promote", id),
            listOf(file),
            supportsDiff = true,
            undoFiles = undoFiles,
        )
    }

    private fun promote(tree: TemporaryTree, file: VirtualFile, received: List<Notification>) {
        run(file, "yac_undo")
        awaitIdle("no notification") { received.any { it.content == PROMOTED } }
        assertEquals(listOf(AFTER_SOURCE, AFTER_SIDECAR, GITIGNORE), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    private fun read(tree: TemporaryTree, path: String): String? = tree.path.resolve(path).let { if (Files.exists(it)) Files.readString(it) else null }

    private fun bytes(tree: TemporaryTree, path: String): List<Int> = Files.readAllBytes(tree.path.resolve(path)).map { it.toInt() and BYTE_MASK }

    private fun settle(editor: Editor) {
        await("the document was not saved") { !FileDocumentManager.getInstance().isDocumentUnsaved(editor.document) }
    }

    private fun start(
        file: VirtualFile,
        id: String,
        indicator: EmptyProgressIndicator = EmptyProgressIndicator(),
        capture: SnapshotCapture = DiskSnapshot.Companion::capture,
    ): EmptyProgressIndicator {
        val task = YacCommandRunner(project).prepare(YacEnvironment.of(project, file)!!, "Promoting $id", listOf("promote", id), listOf(file), supportsDiff = true, undoFiles = listOf(file), capture = capture)!!
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)

        return indicator
    }

    private fun release(tree: TemporaryTree) {
        Files.createFile(tree.path.resolve("release"))
    }

    private fun vfsText(file: VirtualFile): String = String(file.contentsToByteArray(), StandardCharsets.UTF_8)

    fun testUndoAfterPromoteRestoresTheDocumentAndTheSidecarAndRedoAppliesThemAgain() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)

        promote(tree, file, received)

        assertEquals("_Undo Promoting yac_undo", undoManager.getUndoActionNameAndDescription(textEditor).first)

        undoManager.undo(textEditor)
        settle(editor)

        assertEquals(BEFORE_SOURCE, editor.document.text)
        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR, null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
        assertEquals("_Redo Promoting yac_undo", undoManager.getRedoActionNameAndDescription(textEditor).first)

        undoManager.redo(textEditor)
        settle(editor)

        assertEquals(AFTER_SOURCE, editor.document.text)
        assertEquals(listOf(AFTER_SOURCE, AFTER_SIDECAR, GITIGNORE), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testUndoIsRefusedWithTheExactMessageWhenTheSidecarChangedOutsideTheIde() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        promote(tree, file, received)
        tree.file(".yac/a.php.yac", "tampered\n")

        val error = Assert.assertThrows(TestLoggerFactory.TestLoggerAssertionError::class.java) { UndoManager.getInstance(project).undo(textEditor) }

        assertEquals("Cannot undo: .yac/a.php.yac changed after this YAC action. Restore it or discard the change, then try again.", error.message)
        assertEquals(UnexpectedUndoException::class.java, error.cause?.javaClass)
        assertEquals(AFTER_SOURCE, editor.document.text)
        assertEquals(listOf("tampered\n", GITIGNORE), listOf(read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testRedoIsRefusedBeforeAnyDocumentIsReplayedWhenTheSidecarChangedAfterTheUndo() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)
        promote(tree, file, received)
        undoManager.undo(textEditor)
        settle(editor)
        tree.file(".yac/a.php.yac", "tampered\n")

        val error = Assert.assertThrows(TestLoggerFactory.TestLoggerAssertionError::class.java) { undoManager.redo(textEditor) }

        assertEquals("Cannot redo: .yac/a.php.yac changed after this YAC action. Restore it or discard the change, then try again.", error.message)
        assertEquals(UnexpectedUndoException::class.java, error.cause?.javaClass)
        assertEquals(BEFORE_SOURCE, editor.document.text)
        settle(editor)
        assertEquals(listOf(BEFORE_SOURCE, "tampered\n", null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testAStaleDocumentIsReloadedFromDiskAndTheChangeIsReportedAsNotUndoable() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        tree.file("a.php", "<?php\nexternal();\n")
        val undoManager = UndoManager.getInstance(project)
        val before = undoManager.getUndoActionNameAndDescription(null)

        run(file, "yac_undo")
        awaitIdle("no warning") { received.any { it.content == YacMessages.CANNOT_BE_UNDONE } }

        assertEquals(listOf(PROMOTED, YacMessages.CANNOT_BE_UNDONE), contents(received))
        assertEquals(before, undoManager.getUndoActionNameAndDescription(null))
        await("document was not reloaded") { AFTER_SOURCE == document.text }
        assertEquals(AFTER_SIDECAR, read(tree, ".yac/a.php.yac"))
    }

    fun testUndoAfterYeetRestoresTheSidecar() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Deleting YAC notes", listOf("yeet", "a.php"), emptyList(), supportsDiff = false, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Deleted 1 sidecar." } }
        assertNull(read(tree, ".yac/a.php.yac"))
        val undoManager = UndoManager.getInstance(project)
        assertEquals("_Undo Deleting YAC notes", undoManager.getUndoActionNameAndDescription(null).first)

        undoManager.undo(null)

        assertEquals(BEFORE_SIDECAR, read(tree, ".yac/a.php.yac"))
        assertEquals(BEFORE_SOURCE, read(tree, "a.php"))

        undoManager.redo(null)

        assertNull(read(tree, ".yac/a.php.yac"))
    }

    fun testARunThatChangesNothingRegistersNoUndoStep() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val undoManager = UndoManager.getInstance(project)
        val before = undoManager.getUndoActionNameAndDescription(null)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Removing yac_01", listOf("remove", "yac_01"), emptyList(), supportsDiff = false, undoFiles = listOf(file))
        awaitIdle("no notification") { contents(received).isNotEmpty() }

        assertEquals(before, undoManager.getUndoActionNameAndDescription(null))
        assertFalse(undoManager.isUndoAvailable(null))
        assertFalse(undoManager.isRedoAvailable(null))
        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
        assertEquals(listOf("args:remove yac_01<br>careful"), contents(received))
    }

    fun testAFailingDryRunRegistersNoUndoStepAndWarnsOnceTheRunChangedFiles() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        run(file, "yac_dryfail")
        awaitIdle("no warning") { received.any { it.content == YacMessages.PREVIEW_FAILED } }

        assertEquals(listOf("Promoted yac_dryfail.", YacMessages.PREVIEW_FAILED), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        await("document was not reloaded") { AFTER_SOURCE == document.text }
        assertEquals(listOf(AFTER_SOURCE, AFTER_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
    }

    fun testAnEmptyPreviewStillSnapshotsTheFilesOfTheActionAndTheChangeIsUndoable() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)

        run(file, "yac_unlisted")
        awaitIdle("no notification") { received.any { it.content == "Promoted yac_unlisted." } }

        assertEquals(listOf("Promoted yac_unlisted."), contents(received))
        assertEquals(AFTER_SOURCE, editor.document.text)

        UndoManager.getInstance(project).undo(textEditor)
        settle(editor)

        assertEquals(BEFORE_SOURCE, editor.document.text)
        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR, null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testASingleNoteCommandSnapshotsOnlyItsSidecarNotTheWholeYacTree() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree().file(".yac/stray.php.yac", "stray-before\n")
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)

        run(file, "yac_stray")
        awaitIdle("no notification") { received.any { it.content == "Promoted yac_stray." } }
        UndoManager.getInstance(project).undo(textEditor)
        settle(editor)

        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR, "stray\n", null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".yac/stray.php.yac"), read(tree, ".gitignore")))
    }

    fun testMoreSourcesThanTheLimitSkipUndoWithAWarning() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")

        run(file, "yac_many")
        awaitIdle("no warning") { received.any { it.content == YacMessages.tooManyFiles(YacCommandRunner.MAX_UNDO_SOURCES) } }

        assertEquals(listOf("Promoted yac_many.", "This change cannot be undone: it affects more than 200 PHP files."), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
    }

    fun testAFirstExtractIsUndoneDownToNoYacDirectoryAtAll() {
        useFakePhp()
        val received = collectNotifications()
        val tree = TemporaryTree.create().directory(".git").fakeYac().file("a.php", INLINE_SOURCE)
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Extracting inline YAC notes", listOf("extract", "a.php"), listOf(file), supportsDiff = true, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Extracted 1 note." } }

        assertEquals(
            listOf("<?php\nfoo();\n", AFTER_SIDECAR, GITIGNORE, YAC_GITIGNORE, true, true),
            listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore"), read(tree, ".yac/.gitignore"), Files.exists(tree.path.resolve(".yac/.lock")), Files.isDirectory(tree.path.resolve(".yac/.cache"))),
        )
        assertEquals("_Undo Extracting inline YAC notes", undoManager.getUndoActionNameAndDescription(textEditor).first)

        undoManager.undo(textEditor)
        settle(editor)

        assertEquals(listOf(INLINE_SOURCE, false, false), listOf(read(tree, "a.php"), Files.exists(tree.path.resolve(".yac")), Files.exists(tree.path.resolve(".gitignore"))))

        undoManager.redo(textEditor)
        settle(editor)

        assertEquals(
            listOf("<?php\nfoo();\n", AFTER_SIDECAR, GITIGNORE, YAC_GITIGNORE),
            listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore"), read(tree, ".yac/.gitignore")),
        )
    }

    fun testAnExtractIntoAnExistingYacDirectoryKeepsTheDirectoryAndTheLockOnUndo() {
        useFakePhp()
        val received = collectNotifications()
        val tree = TemporaryTree.create().directory(".yac").fakeYac().file("a.php", INLINE_SOURCE).file(".yac/other.php.yac", "other\n")
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Extracting inline YAC notes", listOf("extract", "a.php"), listOf(file), supportsDiff = true, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Extracted 1 note." } }
        UndoManager.getInstance(project).undo(textEditor)
        settle(editor)

        assertEquals(
            listOf(INLINE_SOURCE, "other\n", null, null, true, true),
            listOf(read(tree, "a.php"), read(tree, ".yac/other.php.yac"), read(tree, ".yac/a.php.yac"), read(tree, ".yac/.gitignore"), Files.isDirectory(tree.path.resolve(".yac")), Files.exists(tree.path.resolve(".yac/.lock"))),
        )
    }

    fun testACaptureFailureAfterTheRunStillReportsTheResultAndRegistersNoUndoStep() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        var calls = 0
        val capture: SnapshotCapture = { root, files, isYacTreeIncluded, checkCanceled ->
            if (1 < ++calls) {
                throw IOException("injected")
            }

            DiskSnapshot.capture(root, files, isYacTreeIncluded, checkCanceled)
        }

        start(file, "yac_undo", capture = capture)
        awaitIdle("no warning") { received.any { it.content == YacMessages.CAPTURE_FAILED } }

        assertEquals(listOf(PROMOTED, YacMessages.CAPTURE_FAILED), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        await("document was not reloaded") { AFTER_SOURCE == document.text }
        assertEquals(listOf(AFTER_SOURCE, AFTER_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
    }

    fun testACaptureFailureBeforeTheRunStillRunsYacAndHoldsNothing() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val capture: SnapshotCapture = { _, _, _, _ -> throw IOException("injected") }

        start(file, "yac_undo", capture = capture)
        awaitIdle("no warning") { received.any { it.content == YacMessages.CAPTURE_FAILED } }

        assertEquals(listOf(PROMOTED, YacMessages.CAPTURE_FAILED), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        assertFalse(PendingSources.isHeld(file))
        await("document was not reloaded") { AFTER_SOURCE == document.text }
    }

    fun testCancellingWhileCapturingAfterYacFinishedStillReportsTheResultAndSaysItCannotBeUndone() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val indicator = EmptyProgressIndicator()
        var calls = 0
        val capture: SnapshotCapture = { root, files, isYacTreeIncluded, checkCanceled ->
            if (1 < ++calls) {
                indicator.cancel()
                checkCanceled()
            }

            DiskSnapshot.capture(root, files, isYacTreeIncluded, checkCanceled)
        }

        start(file, "yac_undo", indicator, capture)
        awaitIdle("no warning") { received.any { it.content == YacMessages.CAPTURE_FAILED } }

        assertEquals(listOf(PROMOTED, YacMessages.CAPTURE_FAILED), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        assertFalse(PendingSources.isHeld(file))
        await("document was not reloaded") { AFTER_SOURCE == document.text }
    }

    fun testAPreviewThatExitsWithOneIsTrustedAndTheChangeIsUndoable() {
        useFakePhp()
        val received = collectNotifications()
        val tree = TemporaryTree.create().directory(".git").fakeYac().file("a.php", INLINE_SOURCE)
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Extracting inline YAC notes", listOf("extract", "yac_partial"), listOf(file), supportsDiff = true, undoFiles = listOf(file))
        awaitIdle("no notification") { contents(received).isNotEmpty() }

        assertEquals(listOf(NotificationType.WARNING to "Extracted 1 note.<br>a.php:9: not extracted (skipped)."), received.map { it.type to it.content })
        assertEquals(listOf("<?php\nfoo();\n", AFTER_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
        assertEquals("_Undo Extracting inline YAC notes", UndoManager.getInstance(project).getUndoActionNameAndDescription(textEditor).first)

        UndoManager.getInstance(project).undo(textEditor)
        settle(editor)

        assertEquals(listOf(INLINE_SOURCE, false), listOf(read(tree, "a.php"), Files.exists(tree.path.resolve(".yac"))))
    }

    fun testExactlyTheLimitOfListedSourcesIsStillUndoableAndTheActionsOwnFileIsNotCounted() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)

        run(file, "yac_200")
        awaitIdle("no notification") { received.any { it.content == "Promoted yac_200." } }

        assertEquals(listOf("Promoted yac_200."), contents(received))
        assertEquals(AFTER_SOURCE, editor.document.text)

        UndoManager.getInstance(project).undo(textEditor)
        settle(editor)

        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR, null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".gitignore")))
    }

    fun testUndoAfterInjectRestoresTheSourceAndTheSidecar() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Injecting YAC notes", listOf("inject", "a.php"), listOf(file), supportsDiff = true, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Injected 1 note into 1 file." } }

        assertEquals(listOf(INLINE_SOURCE, null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
        assertEquals(INLINE_SOURCE, editor.document.text)
        assertEquals("_Undo Injecting YAC notes", undoManager.getUndoActionNameAndDescription(textEditor).first)

        undoManager.undo(textEditor)
        settle(editor)

        assertEquals(BEFORE_SOURCE, editor.document.text)
        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))

        undoManager.redo(textEditor)
        settle(editor)

        assertEquals(INLINE_SOURCE, editor.document.text)
        assertEquals(listOf(INLINE_SOURCE, null), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
    }

    fun testUndoAfterRemovingANoteRestoresItsSidecarFromTheEditor() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree().file(".yac/stray.php.yac", "stray-before\n")
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Removing yac_removed", listOf("remove", "yac_removed"), listOf(file), supportsDiff = false, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Removed yac_removed." } }

        assertEquals(AFTER_SIDECAR, read(tree, ".yac/a.php.yac"))
        assertEquals("_Undo Removing yac_removed", undoManager.getUndoActionNameAndDescription(textEditor).first)

        undoManager.undo(textEditor)

        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR, "stray-before\n"), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac"), read(tree, ".yac/stray.php.yac")))

        undoManager.redo(textEditor)

        assertEquals(AFTER_SIDECAR, read(tree, ".yac/a.php.yac"))
    }

    fun testUndoAfterYeetRecreatesTheSidecarWithItsOriginalPermissions() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val sidecar = tree.path.resolve(".yac/a.php.yac")
        Files.setPosixFilePermissions(sidecar, PosixFilePermissions.fromString("rw-r-----"))

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Deleting YAC notes", listOf("yeet", "a.php"), emptyList(), supportsDiff = false, undoFiles = listOf(file))
        awaitIdle("no notification") { received.any { it.content == "Deleted 1 sidecar." } }
        UndoManager.getInstance(project).undo(null)

        assertEquals(BEFORE_SIDECAR, read(tree, ".yac/a.php.yac"))
        assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(sidecar)))
    }

    fun testTextTypedIntoAHeldDocumentDuringTheRunSurvivesAndTheChangeIsReportedAsNotUndoable() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val conflicts = mutableListOf<String>()
        (FileDocumentManager.getInstance() as FileDocumentManagerImpl).setAskReloadFromDisk(testRootDisposable, object : MemoryDiskConflictResolver() {
            override fun askReloadFromDisk(file: VirtualFile, document: Document): Boolean {
                conflicts.add(file.name)

                return false
            }
        })

        start(file, "yac_gated")
        await("run did not start") { Files.exists(tree.path.resolve("run-started")) }
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "typed();\n") }
        release(tree)
        awaitIdle("no warning") { received.any { it.content == YacMessages.CANNOT_BE_UNDONE } }
        await("no memory-disk conflict was raised") { conflicts.isNotEmpty() }

        assertEquals(listOf("a.php"), conflicts.distinct())
        assertEquals(listOf("Promoted yac_gated.", YacMessages.CANNOT_BE_UNDONE), contents(received))
        assertEquals(BEFORE_SOURCE + "typed();\n", document.text)
        assertEquals(AFTER_SOURCE, read(tree, "a.php"))
        assertFalse(PendingSources.isHeld(file))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
    }

    fun testAFailedPreviewReloadsTheHeldDocumentThatTheVfsRefreshedDuringTheRun() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        start(file, "yac_gateddry")
        await("run did not start") { Files.exists(tree.path.resolve("run-started")) }
        file.refresh(false, false)

        assertEquals(listOf(BEFORE_SOURCE, AFTER_SOURCE), listOf(document.text.toString(), vfsText(file)))

        release(tree)
        awaitIdle("no warning") { received.any { it.content == YacMessages.PREVIEW_FAILED } }

        assertEquals(listOf("Promoted yac_gateddry.", YacMessages.PREVIEW_FAILED), contents(received))
        assertEquals(AFTER_SOURCE, document.text)
        assertEquals(AFTER_SOURCE, read(tree, "a.php"))
        assertFalse(PendingSources.isHeld(file))
    }

    fun testCancellingTheRealRunReloadsTheHeldDocumentThatTheVfsRefreshedDuringTheRun() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        val indicator = start(file, "yac_gated")
        await("run did not start") { Files.exists(tree.path.resolve("run-started")) }
        file.refresh(false, false)

        assertEquals(listOf(BEFORE_SOURCE, AFTER_SOURCE), listOf(document.text.toString(), vfsText(file)))

        indicator.cancel()
        awaitIdle("no cancel message") { contents(received).isNotEmpty() }

        assertEquals(listOf(YacMessages.CANCELLED), contents(received))
        assertEquals(AFTER_SOURCE, document.text)
        assertEquals(AFTER_SOURCE, read(tree, "a.php"))
        assertFalse(PendingSources.isHeld(file))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
    }

    fun testCancellingTheDryRunSaysNothingWasWrittenAndFreesTheRun() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")

        val indicator = start(file, "yac_slowdry")
        await("dry run did not start") { Files.exists(tree.path.resolve("dry-started")) }
        indicator.cancel()
        awaitIdle("no cancel message") { contents(received).isNotEmpty() }

        assertEquals(listOf(YacMessages.CANCELLED_BEFORE_CHANGES), contents(received))
        assertEquals(listOf(BEFORE_SOURCE, BEFORE_SIDECAR), listOf(read(tree, "a.php"), read(tree, ".yac/a.php.yac")))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        assertFalse(PendingSources.isHeld(file))
    }

    fun testCancellingTheRealRunSaysFilesMayHaveChangedAndReleasesTheHeldDocuments() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")

        val indicator = start(file, "yac_slowrun")
        await("run did not start") { Files.exists(tree.path.resolve("run-started")) }
        assertTrue(PendingSources.isHeld(file))
        indicator.cancel()
        awaitIdle("no cancel message") { contents(received).isNotEmpty() }

        assertEquals(listOf(YacMessages.CANCELLED), contents(received))
        assertFalse(UndoManager.getInstance(project).isUndoAvailable(null))
        assertFalse(PendingSources.isHeld(file))
    }

    fun testASecondActionWhileOneRunsIsRefusedAndTheFirstKeepsRunning() {
        useFakePhp()
        val received = collectNotifications()
        val tree = promoteTree()
        val file = tree.find("a.php")
        val state = project.service<YacRunState>()

        val indicator = start(file, "yac_slowrun")
        await("run did not start") { Files.exists(tree.path.resolve("run-started")) }
        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Removing yac_01", listOf("remove", "yac_01"), emptyList(), supportsDiff = false, undoFiles = listOf(file))

        assertEquals(listOf(listOf(YacMessages.ALREADY_RUNNING), true), listOf(contents(received), state.isRunning))

        indicator.cancel()
        awaitIdle("no cancel message") { contents(received).size > 1 }

        assertEquals(listOf(YacMessages.ALREADY_RUNNING, YacMessages.CANCELLED), contents(received))
        assertFalse(state.isRunning)

        run(file, "yac_undo")
        awaitIdle("no notification") { received.any { it.content == PROMOTED } }
    }

    fun testASourceWithAByteOrderMarkIsEditedAndRestoredWithTheMarkOnDisk() {
        useFakePhp()
        val received = collectNotifications()
        val mark = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val tree = promoteTree().bytes("a.php", mark + BEFORE_SOURCE.toByteArray(StandardCharsets.UTF_8))
        val file = tree.find("a.php")
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)
        val markBytes = listOf(0xEF, 0xBB, 0xBF)

        run(file, "yac_insert")
        awaitIdle("no notification") { received.any { it.content == "Promoted yac_insert." } }

        assertEquals(AFTER_SOURCE, editor.document.text)
        assertEquals(markBytes + AFTER_SOURCE.toByteArray(StandardCharsets.UTF_8).map { it.toInt() }, bytes(tree, "a.php"))

        undoManager.undo(textEditor)
        settle(editor)

        assertEquals(BEFORE_SOURCE, editor.document.text)
        assertEquals(markBytes + BEFORE_SOURCE.toByteArray(StandardCharsets.UTF_8).map { it.toInt() }, bytes(tree, "a.php"))

        undoManager.redo(textEditor)
        settle(editor)

        assertEquals(markBytes + AFTER_SOURCE.toByteArray(StandardCharsets.UTF_8).map { it.toInt() }, bytes(tree, "a.php"))
    }

    fun testASourceInANonUtf8CharsetIsEditedAndRestoredByteForByte() {
        useFakePhp()
        val received = collectNotifications()
        val beforeBytes = "<?php\n// caf".toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(0xE9.toByte()) + "\nfoo();\n".toByteArray(StandardCharsets.ISO_8859_1)
        val tree = promoteTree().bytes("a.php", beforeBytes)
        val file = tree.find("a.php")
        file.charset = StandardCharsets.ISO_8859_1
        val editor = open(file)
        val textEditor: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        val undoManager = UndoManager.getInstance(project)
        val afterBytes = "<?php\n/** promoted */\n// caf".toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(0xE9.toByte()) + "\nfoo();\n".toByteArray(StandardCharsets.ISO_8859_1)

        run(file, "yac_insert")
        awaitIdle("no notification") { received.any { it.content == "Promoted yac_insert." } }

        assertEquals("<?php\n/** promoted */\n// caf\u00e9\nfoo();\n", editor.document.text)
        assertEquals(afterBytes.map { it.toInt() and BYTE_MASK }, bytes(tree, "a.php"))

        undoManager.undo(textEditor)
        settle(editor)

        assertEquals("<?php\n// caf\u00e9\nfoo();\n", editor.document.text)
        assertEquals(beforeBytes.map { it.toInt() and BYTE_MASK }, bytes(tree, "a.php"))

        undoManager.redo(textEditor)
        settle(editor)

        assertEquals(afterBytes.map { it.toInt() and BYTE_MASK }, bytes(tree, "a.php"))
    }

    fun testNotificationGroupIsRegisteredUnderTheNotifierId() {
        assertEquals("dev.icebear.yac", YacNotifier.GROUP_ID)
        assertNotNull(NotificationGroupManager.getInstance().getNotificationGroup(YacNotifier.GROUP_ID))
    }

    private companion object {
        const val PROMOTED = "Promoted yac_undo."
        const val BEFORE_SOURCE = "<?php\nfoo();\n"
        const val AFTER_SOURCE = "<?php\n/** promoted */\nfoo();\n"
        const val INLINE_SOURCE = "<?php\n/** @yac note */\nfoo();\n"
        const val YAC_GITIGNORE = ".lock\n/.cache/\n"
        const val BEFORE_SIDECAR = "before-sidecar\n"
        const val AFTER_SIDECAR = "after-sidecar\n"
        const val GITIGNORE = "/.yac/\n"
        const val BYTE_MASK = 0xFF
    }
}
