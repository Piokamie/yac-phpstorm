package dev.icebear.yac

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

object YacNotifications {
    private const val GROUP_ID = "YAC"
    private const val TITLE = "YAC"
    private val shown = ConcurrentHashMap.newKeySet<String>()

    fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID).createNotification(TITLE, message, type).notify(project)
    }

    fun notifyOnce(project: Project, message: String, type: NotificationType) {
        if (shown.add(project.locationHash + message)) {
            notify(project, message, type)
        }
    }
}
