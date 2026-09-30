package dev.icebear.yac

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class YacNotifier(private val project: Project) {
    private val shownOnce = ConcurrentHashMap.newKeySet<String>()
    private val reported = ConcurrentHashMap<String, Set<String>>()

    internal val reportedKeyCount: Int
        get() = reported.size

    fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
            .createNotification(TITLE, StringUtil.escapeXmlEntities(message).replace("\n", LINE_BREAK), type)
            .notify(project)
    }

    fun notifyOnce(message: String, type: NotificationType) {
        if (shownOnce.add(message)) {
            notify(message, type)
        }
    }

    fun report(key: String, messages: List<String>, type: NotificationType) {
        val previous = (if (messages.isEmpty()) reported.remove(key) else reported.put(key, messages.toSet())).orEmpty()
        messages.distinct().filterNot { it in previous }.forEach { notify(it, type) }
    }

    fun reset() {
        shownOnce.clear()
        reported.clear()
    }

    companion object {
        const val GROUP_ID = "dev.icebear.yac"
        private const val TITLE = "YAC"
        private const val LINE_BREAK = "<br>"
    }
}
