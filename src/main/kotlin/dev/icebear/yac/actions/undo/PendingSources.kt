package dev.icebear.yac.actions.undo

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class PendingSources : Disposable {
    private val holds = ConcurrentHashMap<VirtualFile, Int>()

    fun hold(files: Collection<VirtualFile>) {
        files.forEach { file ->
            holds.merge(file, 1, Int::plus)
            INDEX.merge(file, 1, Int::plus)
        }
    }

    fun release(files: Collection<VirtualFile>) {
        files.forEach { file -> if (decrement(holds, file)) decrement(INDEX, file) }
    }

    fun releaseAll() {
        holds.entries.toList().forEach { (file, count) -> repeat(count) { release(listOf(file)) } }
    }

    override fun dispose() = releaseAll()

    companion object {
        private val INDEX = ConcurrentHashMap<VirtualFile, Int>()

        fun isHeld(file: VirtualFile): Boolean = INDEX.containsKey(file)

        private fun decrement(counts: ConcurrentHashMap<VirtualFile, Int>, file: VirtualFile): Boolean {
            var isDecremented = false
            counts.computeIfPresent(file) { _, count ->
                isDecremented = true

                if (count > 1) count - 1 else null
            }

            return isDecremented
        }
    }
}
