package dev.icebear.yac.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class YacCli(
    private val php: String,
    private val yac: Path,
    private val workingDirectory: Path,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    fun run(arguments: List<String>, stdin: String? = null): YacProcessResult {
        val commandLine = GeneralCommandLine(listOf(php, yac.toString()) + arguments)
            .withWorkingDirectory(workingDirectory)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val process = commandLine.createProcess()
        try {
            process.outputStream.use { input -> stdin?.let { input.write(it.toByteArray(StandardCharsets.UTF_8)) } }
        } catch (exception: IOException) {
            LOG.debug("yac exited before reading stdin", exception)
        }
        val output = CapturingProcessHandler(process, StandardCharsets.UTF_8, commandLine.commandLineString).runProcess(timeoutMillis)

        return YacProcessResult(output.exitCode, output.stdout, output.stderr, output.isTimeout)
    }

    fun context(source: String, contents: String): ContextResult {
        val result = run(listOf(CONTEXT_COMMAND, source, STDIN_OPTION, JSON_FORMAT_OPTION), contents)
        if (result.isTimedOut) {
            throw YacCliException("yac context timed out after $timeoutMillis ms.")
        }

        if (result.stderr.contains(MISSING_STDIN_OPTION)) {
            throw YacTooOldException()
        }

        val parsed = try {
            JSON.decodeFromString<ContextResult>(result.stdout)
        } catch (exception: SerializationException) {
            throw YacCliException("yac context returned no usable JSON (exit ${result.exitCode}): ${result.message}")
        } catch (exception: IllegalArgumentException) {
            throw YacCliException("yac context returned no usable JSON (exit ${result.exitCode}): ${result.message}")
        }

        if (SUPPORTED_SCHEMA != parsed.schema) {
            throw YacCliException("yac context uses JSON schema ${parsed.schema}; this plugin supports schema $SUPPORTED_SCHEMA.")
        }

        return parsed
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000
        const val SUPPORTED_SCHEMA = 1
        private const val CONTEXT_COMMAND = "context"
        private const val STDIN_OPTION = "--stdin"
        private const val JSON_FORMAT_OPTION = "--format=json"
        private const val MISSING_STDIN_OPTION = "The \"--stdin\" option does not exist."
        private val JSON = Json { ignoreUnknownKeys = true }
        private val LOG = logger<YacCli>()
    }
}
