package dev.icebear.yac.cli

import kotlinx.serialization.Serializable

@Serializable
data class ContextResult(
    val schema: Int,
    val files: List<ContextFile>,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ContextFile(
    val source: String,
    val annotations: List<Note>,
)

@Serializable
data class Note(
    val id: String,
    val source: String,
    val line: Int? = null,
    val status: String,
    val scope: String? = null,
    val anchor: String,
    val comment: String,
    val matches: List<Int> = emptyList(),
    val problems: List<String> = emptyList(),
) {
    val isResolved: Boolean
        get() = RESOLVED == status

    companion object {
        const val RESOLVED = "resolved"
    }
}
