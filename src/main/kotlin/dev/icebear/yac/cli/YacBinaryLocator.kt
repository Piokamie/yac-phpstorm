package dev.icebear.yac.cli

import java.nio.file.Files
import java.nio.file.Path

class YacBinaryLocator(
    private val projectRoot: Path,
    private val configuredYac: String,
    private val configuredPhp: String,
    private val interpreterPath: () -> String?,
) {
    fun yac(): Path? {
        val candidates = if (configuredYac.isBlank()) DEFAULT_YAC_PATHS else listOf(configuredYac)

        return candidates.map { projectRoot.resolve(it) }.firstOrNull { Files.isRegularFile(it) }
    }

    fun php(): String {
        val interpreter = interpreterPath()
        if (null != interpreter && Files.isRegularFile(Path.of(interpreter))) {
            return interpreter
        }

        return configuredPhp.ifBlank { DEFAULT_PHP }
    }

    companion object {
        val DEFAULT_YAC_PATHS = listOf("vendor/bin/yac", "bin/yac")
        const val DEFAULT_PHP = "php"
    }
}
