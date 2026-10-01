package dev.icebear.yac.actions.undo

import java.nio.file.Path

internal class DocumentEdit(val source: SourceDocument, val before: String, val after: String) {
    val splice: TextEdit? = TextEdit.between(before, after)
}

internal class UndoPlan private constructor(
    val root: Path,
    val changes: Map<String, FileChange>,
    val directories: Set<String>,
    val edits: List<DocumentEdit>,
) {
    val replays: Map<String, FileChange> = changes - edits.map { it.source.path }.toSet()

    companion object {
        fun of(root: Path, changes: Map<String, FileChange>, directories: Set<String>, sources: List<SourceDocument>): UndoPlan {
            val edits = sources.mapNotNull { source ->
                val change = changes[source.path] ?: return@mapNotNull null
                val before = change.before ?: return@mapNotNull null
                val after = change.after ?: return@mapNotNull null
                val old = SourceDocument.decode(source.file, before)
                val new = SourceDocument.decode(source.file, after)

                if (old == new) null else DocumentEdit(source, old, new)
            }

            return UndoPlan(root, changes, directories, edits)
        }
    }
}
