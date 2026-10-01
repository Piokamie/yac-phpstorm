package dev.icebear.yac.actions.undo

internal class TextEdit(val start: Int, val end: Int, val replacement: String) {
    companion object {
        fun between(before: String, after: String): TextEdit? {
            if (before == after) {
                return null
            }

            val limit = minOf(before.length, after.length)
            var prefix = 0
            while (prefix < limit && before[prefix] == after[prefix]) {
                prefix++
            }

            var suffix = 0
            while (suffix < limit - prefix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) {
                suffix++
            }

            return TextEdit(prefix, before.length - suffix, after.substring(prefix, after.length - suffix))
        }
    }
}
