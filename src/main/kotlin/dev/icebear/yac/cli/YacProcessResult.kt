package dev.icebear.yac.cli

data class YacProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isTimedOut: Boolean,
) {
    val message: String
        get() = stderr.lineSequence().map { it.removePrefix(ERROR_PREFIX).removePrefix(WARNING_PREFIX) }.joinToString("\n").trim().ifEmpty { stdout.trim() }

    companion object {
        const val SUCCESS = 0
        const val FAILURE = 1
        private const val ERROR_PREFIX = "Error: "
        private const val WARNING_PREFIX = "Warning: "
    }
}
