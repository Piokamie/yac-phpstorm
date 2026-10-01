package dev.icebear.yac.actions.undo

internal object DiffPaths {
    private const val OLD_HEADER = "--- a/"
    private const val NEW_HEADER = "+++ b/"

    fun of(diff: String): List<String> {
        val lines = diff.lines()

        return lines.indices.mapNotNull { index ->
            val old = lines[index]
            val new = lines.getOrNull(index + 1)
            if (null != new && old.startsWith(OLD_HEADER) && new.startsWith(NEW_HEADER) && old.removePrefix(OLD_HEADER) == new.removePrefix(NEW_HEADER)) {
                old.removePrefix(OLD_HEADER)
            } else {
                null
            }
        }.distinct()
    }
}
