package dev.icebear.yac.cli

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import java.nio.file.Path
import java.nio.file.Paths

class YacCliTest : BasePlatformTestCase() {
    private val fakeYac: Path = Paths.get(javaClass.getResource("/fake-yac.sh")!!.toURI())

    private fun cli(timeoutMillis: Int = YacCli.DEFAULT_TIMEOUT_MILLIS) = YacCli("/bin/sh", fakeYac, fakeYac.parent, timeoutMillis)

    fun testContextSendsTheBufferOnStdinAndParsesNotes() {
        val result = cli().context("ok.php", "Unsaved text")

        assertEquals(
            ContextResult(
                1,
                listOf(ContextFile("ok.php", listOf(Note("yac_01JB8M3Z4XAAAAAAAAAAAAAAAA", "ok.php", 3, "resolved", "Foo::bar", "foo();", "Unsaved text", listOf(3), emptyList())))),
                emptyList(),
            ),
            result,
        )
        assertTrue(result.files[0].annotations[0].isResolved)
    }

    fun testOldYacWithoutStdinIsReported() {
        assertThrows(YacTooOldException::class.java) { cli().context("old.php", "") }
    }

    fun testUnsupportedSchemaIsRejected() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli().context("schema.php", "") }

        assertEquals("yac context uses JSON schema 2; this plugin supports schema 1.", exception.message)
    }

    fun testUnusableOutputCarriesTheCliMessage() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli().context("broken.php", "") }

        assertEquals("yac context returned no usable JSON (exit 2): boom", exception.message)
    }

    fun testTimeoutIsReported() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli(200).context("slow.php", "") }

        assertEquals("yac context timed out after 200 ms.", exception.message)
    }

    fun testRunReturnsExitCodeOutputAndMessage() {
        val result = cli().run(listOf("remove", "yac_01JB"))

        assertEquals(YacProcessResult(1, "args:remove yac_01JB\n", "Warning: careful\n", false), result)
        assertEquals("careful", result.message)
    }
}
