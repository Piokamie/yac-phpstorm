package dev.icebear.yac.cli

object YacCommands {
    const val CONTEXT = "context"
    const val PROMOTE = "promote"
    const val REMOVE = "remove"
    const val EXTRACT = "extract"
    const val INJECT = "inject"
    const val YEET = "yeet"
    const val DRY_RUN = "--dry-run"
    const val DIFF = "--diff"
    val NOTE_COMMANDS = setOf(PROMOTE, REMOVE)
}
