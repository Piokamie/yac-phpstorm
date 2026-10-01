package dev.icebear.yac.actions.undo

import com.intellij.openapi.components.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
internal class YacRunState {
    private val running = AtomicBoolean(false)

    val isRunning: Boolean
        get() = running.get()

    fun tryStart(): Boolean = running.compareAndSet(false, true)

    fun finish() {
        running.set(false)
    }
}
