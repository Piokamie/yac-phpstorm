package dev.icebear.yac.cli

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.icebear.yac.TemporaryTree
import org.junit.Assert
import java.util.concurrent.CancellationException

class YacCliTest : BasePlatformTestCase() {
    private fun cli(contextTimeoutMillis: Long = YacCli.CONTEXT_TIMEOUT_MILLIS) =
        YacCli("/bin/sh", TemporaryTree.FAKE_YAC.toString(), TemporaryTree.FAKE_YAC.parent, contextTimeoutMillis)

    fun testContextSendsTheBufferOnStdinAndParsesNotes() {
        val result = cli().context("ok.php", "Unsaved text")

        assertEquals(
            ContextResult(
                1,
                listOf(ContextFile(listOf(Note("yac_01JB8M3Z4XAAAAAAAAAAAAAAAA", 3, "resolved", "Foo::bar", "Unsaved text", emptyList())))),
                listOf("Sidecar .yac/x.php.yac is invalid"),
            ),
            result,
        )
        assertTrue(result.files[0].annotations[0].isResolved)
    }

    fun testOldYacWithoutStdinIsReported() {
        val exception = Assert.assertThrows(YacTooOldException::class.java) { cli().context("old.php", "") }

        assertEquals("The installed yac has no context --stdin option; update icebear/yac.", exception.message)
    }

    fun testUnsupportedSchemaIsRejected() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli().context("schema.php", "") }

        assertEquals("yac context uses JSON schema 2; this plugin supports schema 1.", exception.message)
    }

    fun testOutputWithoutSchemaIsUnusable() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli().context("noschema.php", "") }

        assertEquals("yac context returned no usable JSON (exit 0): {\"files\": []}", exception.message)
    }

    fun testUnusableOutputCarriesTheCliMessage() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli().context("broken.php", "") }

        assertEquals("yac context returned no usable JSON (exit 2): boom", exception.message)
    }

    fun testContextTimeoutIsReported() {
        val exception = Assert.assertThrows(YacCliException::class.java) { cli(200).context("slow.php", "") }

        assertEquals("yac context did not finish within 200 ms.", exception.message)
    }

    fun testCancellationStopsTheProcess() {
        val started = System.nanoTime()

        Assert.assertThrows(CancellationException::class.java) {
            cli().run(listOf("context", "slow.php"), checkCanceled = { throw CancellationException() })
        }

        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    fun testRunReturnsExitCodeOutputAndSummary() {
        val result = cli().run(listOf("remove", "yac_01JB"))

        assertEquals(YacProcessResult(1, "args:remove yac_01JB\n", "Warning: careful\n", false), result)
        assertEquals("args:remove yac_01JB\ncareful", result.summary)
    }

    fun testXdebugIsTurnedOff() {
        assertEquals("off", cli().run(listOf("context", "xdebug.php")).stdout)
    }

    fun testSummaryWithoutOutputNamesTheExitCode() {
        assertEquals("yac exited with code 2.", YacProcessResult(2, "", "\n").summary)
    }
}
