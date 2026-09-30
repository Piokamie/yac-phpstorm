package dev.icebear.yac.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class YacSidecarListenerTest {
    @Test
    fun sidecarsAndTheYacDirectoryAreChangesLockAndCacheAreNot() {
        val paths = listOf(
            "/p/.yac/src/A.php.yac",
            "/p/.yac",
            "/p/.yac/src",
            "/p/.yac/.lock",
            "/p/.yac/.cache/index.yac",
            "/p/src/A.php",
            "/p/src/notes.yac",
        )

        assertEquals(listOf(true, true, false, false, false, false, false), paths.map { YacSidecarListener.isYacChange(it) })
    }
}
