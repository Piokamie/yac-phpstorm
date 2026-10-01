package dev.icebear.yac

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.icebear.yac.actions.undo.PendingSources
import dev.icebear.yac.actions.undo.YacRunState
import dev.icebear.yac.notes.YacNotesService
import dev.icebear.yac.settings.YacSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val FAKE_PHP_SCRIPT = """#!/bin/sh
if [ "${'$'}1" = "-d" ]; then
  YAC_FAKE_PHP_OPTION="${'$'}2"
  export YAC_FAKE_PHP_OPTION
  shift 2
fi
exec /bin/sh "${'$'}@"
"""

class TemporaryTree(val path: Path) {
    fun file(relativePath: String, contents: String = ""): TemporaryTree = apply {
        val file = path.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, contents)
    }

    fun bytes(relativePath: String, contents: ByteArray): TemporaryTree = apply {
        val file = path.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.write(file, contents)
    }

    fun directory(relativePath: String): TemporaryTree = apply { Files.createDirectories(path.resolve(relativePath)) }

    fun fakeYac(relativePath: String = "vendor/bin/yac"): TemporaryTree = file(relativePath, Files.readString(FAKE_YAC))

    fun find(relativePath: String = ""): VirtualFile {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
        VfsUtil.markDirtyAndRefresh(false, true, true, root)

        return if (relativePath.isEmpty()) root else root.findFileByRelativePath(relativePath)!!
    }

    companion object {
        val FAKE_YAC: Path = Paths.get(TemporaryTree::class.java.getResource("/fake-yac.sh")!!.toURI())

        fun fakePhp(): Path = FileUtil.createTempFile("fake-php", ".sh", true).toPath().also {
            Files.writeString(it, FAKE_PHP_SCRIPT)
            it.toFile().setExecutable(true)
        }

        fun create(): TemporaryTree = TemporaryTree(FileUtil.createTempDirectory("yac", null, true).toPath())
    }
}

abstract class YacTestCase : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            project.service<YacNotesService>().cancelPendingRefreshes()
            val state = project.service<YacRunState>()
            val isRunning = state.isRunning
            state.finish()
            project.service<PendingSources>().releaseAll()
            project.service<YacSettings>().loadState(YacSettings.Options())
            project.service<YacNotifier>().reset()
            assertFalse("A yac run was still in flight when the test ended.", isRunning)
        } finally {
            super.tearDown()
        }
    }

    protected fun collectNotifications(): List<Notification> {
        val received = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                received.add(notification)
            }
        })

        return received
    }

    protected fun useFakePhp() {
        project.service<YacSettings>().state.phpPath = TemporaryTree.fakePhp().toString()
    }
}
