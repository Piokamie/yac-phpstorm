package dev.icebear.yac.cli

import kotlinx.serialization.Serializable

@Serializable
data class ContextResult(
    val schema: Int,
    val files: List<ContextFile> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ContextFile(
    val annotations: List<Note> = emptyList(),
)

@Serializable
data class Note(
    val id: String,
    val line: Int? = null,
    val status: String,
    val scope: String? = null,
    val comment: String,
    val problems: List<String> = emptyList(),
) {
    val isResolved: Boolean
        get() = NoteStatus.RESOLVED == status
}

object NoteStatus {
    const val RESOLVED = "resolved"
    const val UNPARSEABLE_SOURCE = "unparseable_source"
}
