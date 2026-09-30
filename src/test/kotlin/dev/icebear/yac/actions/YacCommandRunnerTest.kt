package dev.icebear.yac.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import dev.icebear.yac.TemporaryTree
import dev.icebear.yac.YacEnvironment
import dev.icebear.yac.YacMessages
import dev.icebear.yac.YacNotifier
import dev.icebear.yac.YacTestCase
import dev.icebear.yac.cli.YacProcessResult
import dev.icebear.yac.notes.YacNotesService
import dev.icebear.yac.presentation.NoteGutterIconRenderer
import dev.icebear.yac.settings.YacSettings
import java.nio.file.Files

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

        YacCommandRunner(project).run(YacEnvironment.of(project, file)!!, "Testing", listOf("remove", "yac_01"), listOf(file))

        assertEquals("<?php\nfoo();\n", Files.readString(tree.path.resolve("a.php")))
        PlatformTestUtil.waitWithEventsDispatching("no notification", { received.isNotEmpty() }, 10)
        assertEquals(listOf(NotificationType.WARNING to "args:remove yac_01<br>careful"), received.map { it.type to it.content })
    }

    fun testRunWithoutYacSaysSo() {
        val received = collectNotifications()
        val environment = YacEnvironment.of(project, TemporaryTree.create().directory(".yac").find())!!

        YacCommandRunner(project).run(environment, "Testing", listOf("remove", "yac_01"), emptyList())

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

    fun testNotificationGroupIsRegisteredUnderTheNotifierId() {
        assertEquals("dev.icebear.yac", YacNotifier.GROUP_ID)
        assertNotNull(NotificationGroupManager.getInstance().getNotificationGroup(YacNotifier.GROUP_ID))
    }
}
