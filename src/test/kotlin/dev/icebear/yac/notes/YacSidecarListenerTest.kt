package dev.icebear.yac.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class YacSidecarListenerTest {
    @Test
    fun sidecarsAndDirectoriesUnderYacAreChangesLockAndCacheAreNot() {
        val paths = listOf(
            "/p/.yac/src/A.php.yac",
            "/p/.yac",
            "/p/.yac/src",
            "/p/.yac/.lock",
            "/p/.yac/.cache/x.yac",
            "/p/.yac/.cache",
            "/p/src/A.php",
            "/p/vendor/bin/yac",
            "/p/bin/yac",
            "/p/src/notes.yac",
        )

        assertEquals(
            mapOf(
                "/p/.yac/src/A.php.yac" to true,
                "/p/.yac" to true,
                "/p/.yac/src" to true,
                "/p/.yac/.lock" to false,
                "/p/.yac/.cache/x.yac" to false,
                "/p/.yac/.cache" to false,
                "/p/src/A.php" to false,
                "/p/vendor/bin/yac" to true,
                "/p/bin/yac" to true,
                "/p/src/notes.yac" to false,
            ),
            paths.associateWith { YacSidecarListener.isYacChange(it) },
        )
    }
}
