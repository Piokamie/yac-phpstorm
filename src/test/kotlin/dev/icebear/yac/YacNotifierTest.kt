package dev.icebear.yac

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service

class YacNotifierTest : YacTestCase() {
    fun testReportShowsOnlyMessagesThatAreNewForTheKey() {
        val received = collectNotifications()
        val notifier = project.service<YacNotifier>()

        notifier.report("a", listOf("One", "Two"), NotificationType.WARNING)
        notifier.report("a", listOf("Two"), NotificationType.WARNING)
        notifier.report("b", listOf("Two"), NotificationType.WARNING)
        notifier.report("a", listOf("One", "Two"), NotificationType.WARNING)
        notifier.report("a", emptyList(), NotificationType.WARNING)
        notifier.report("a", listOf("Two"), NotificationType.WARNING)

        assertEquals(listOf("One", "Two", "Two", "One", "Two"), received.map { it.content })
    }

    fun testReportForgetsAKeyOnceItHasNoMessages() {
        val notifier = project.service<YacNotifier>()

        notifier.report("a", listOf("One"), NotificationType.WARNING)
        notifier.report("b", listOf("One"), NotificationType.WARNING)
        assertEquals(2, notifier.reportedKeyCount)

        notifier.report("a", emptyList(), NotificationType.WARNING)
        assertEquals(1, notifier.reportedKeyCount)
    }

    fun testNotifyOnceUntilResetAndEscaping() {
        val received = collectNotifications()
        val notifier = project.service<YacNotifier>()

        notifier.notifyOnce("Use <b>\nnow", NotificationType.INFORMATION)
        notifier.notifyOnce("Use <b>\nnow", NotificationType.INFORMATION)
        notifier.reset()
        notifier.notifyOnce("Use <b>\nnow", NotificationType.INFORMATION)

        assertEquals(listOf("Use &lt;b&gt;<br>now", "Use &lt;b&gt;<br>now"), received.map { it.content })
    }
}
