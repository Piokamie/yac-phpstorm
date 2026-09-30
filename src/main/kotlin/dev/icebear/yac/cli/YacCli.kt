package dev.icebear.yac.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class YacCli(
    private val php: String,
    private val yac: String,
    private val workingDirectory: Path,
    private val contextTimeoutMillis: Long = CONTEXT_TIMEOUT_MILLIS,
) {
    fun run(arguments: List<String>, stdin: String? = null, timeoutMillis: Long? = null, checkCanceled: () -> Unit = {}): YacProcessResult {
        val commandLine = GeneralCommandLine(listOf(php, yac) + arguments)
            .withWorkingDirectory(workingDirectory)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(XDEBUG_MODE, XDEBUG_OFF)
        val output = ProcessOutput()
        val handler = OSProcessHandler(commandLine)
        handler.addProcessListener(CapturingProcessAdapter(output))
        handler.startNotify()
        try {
            handler.processInput?.use { input -> stdin?.let { input.write(it.toByteArray(StandardCharsets.UTF_8)) } }
        } catch (exception: IOException) {
            LOG.debug("yac exited before reading stdin", exception)
        }

        val deadline = timeoutMillis?.let { System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(it) }
        while (!handler.waitFor(POLL_MILLIS)) {
            try {
                checkCanceled()
            } catch (exception: Throwable) {
                handler.destroyProcess()
                throw exception
            }

            if (null != deadline && System.nanoTime() >= deadline) {
                handler.destroyProcess()
                handler.waitFor()

                return YacProcessResult(YacProcessResult.NOT_FINISHED, output.stdout, output.stderr, true)
            }
        }

        return YacProcessResult(output.exitCode, output.stdout, output.stderr)
    }

    fun context(source: String, contents: String, checkCanceled: () -> Unit = {}): ContextResult {
        val result = run(listOf(YacCommands.CONTEXT, source, STDIN_OPTION, JSON_FORMAT_OPTION), contents, contextTimeoutMillis, checkCanceled)
        if (result.isTimedOut) {
            throw YacCliException("yac context did not finish within $contextTimeoutMillis ms.")
        }

        if (result.stderr.contains(MISSING_STDIN_OPTION)) {
            throw YacTooOldException()
        }

        val json = try {
            JSON.parseToJsonElement(result.stdout).jsonObject
        } catch (exception: IllegalArgumentException) {
            throw unusableOutput(result)
        }

        val schema = (json[SCHEMA_KEY] as? JsonPrimitive)?.intOrNull ?: throw unusableOutput(result)
        if (SUPPORTED_SCHEMA != schema) {
            throw YacCliException("yac context uses JSON schema $schema; this plugin supports schema $SUPPORTED_SCHEMA.")
        }

        return try {
            JSON.decodeFromJsonElement(ContextResult.serializer(), json)
        } catch (exception: IllegalArgumentException) {
            throw unusableOutput(result)
        }
    }

    private fun unusableOutput(result: YacProcessResult): YacCliException =
        YacCliException("yac context returned no usable JSON (exit ${result.exitCode}): ${result.summary}")

    companion object {
        const val CONTEXT_TIMEOUT_MILLIS = 10_000L
        const val SUPPORTED_SCHEMA = 1
        private const val POLL_MILLIS = 50L
        private const val SCHEMA_KEY = "schema"
        private const val STDIN_OPTION = "--stdin"
        private const val JSON_FORMAT_OPTION = "--format=json"
        private const val XDEBUG_MODE = "XDEBUG_MODE"
        private const val XDEBUG_OFF = "off"
        private const val MISSING_STDIN_OPTION = "The \"--stdin\" option does not exist."
        private val JSON = Json { ignoreUnknownKeys = true }
        private val LOG = logger<YacCli>()
    }
}
