package dev.icebear.yac.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.icebear.yac.cli.YacProcessResult

class YacCommandRunnerTest : BasePlatformTestCase() {
    fun testExitCodesMapToNotificationTypesAndTexts() {
        val cases = mapOf(
            YacProcessResult(0, "Removed yac_01 from src/A.php.\n", "", false) to (NotificationType.INFORMATION to "Removed yac_01 from src/A.php."),
            YacProcessResult(1, "", "Error: Annotation \"yac_0000\" not found.\n", false) to (NotificationType.WARNING to "Annotation \"yac_0000\" not found."),
            YacProcessResult(2, "", "Error: Cannot write .yac: could not create directory.\n", false) to (NotificationType.ERROR to "Cannot write .yac: could not create directory."),
            YacProcessResult(-1, "", "", true) to (NotificationType.ERROR to "yac did not finish in time."),
        )

        cases.forEach { (result, expected) ->
            assertEquals(expected, YacCommandRunner.notificationType(result) to YacCommandRunner.summary(result))
        }
    }

    fun testFinishNotifiesWithTheCliMessage() {
        val received = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                received.add(notification)
            }
        })

        YacCommandRunner(project).finish(YacProcessResult(1, "", "Warning: src/A.php: skipped, its sidecar is invalid (x).\n", false), emptyList())

        assertEquals(listOf(NotificationType.WARNING to "src/A.php: skipped, its sidecar is invalid (x)."), received.map { it.type to it.content })
    }
}
