package dev.icebear.yac

object YacMessages {
    const val NO_YAC = "No yac binary found; run composer require --dev icebear/yac or set the path in Settings | Tools | YAC."
    const val REMOTE_INTERPRETER = "The project PHP interpreter is remote; yac runs with php from the PATH. Set a local PHP executable in Settings | Tools | YAC."
    const val CANNOT_RUN = "Cannot run yac: "
    const val CANCELLED = "yac was stopped. Files it had already written keep their changes."
    const val CANCELLED_BEFORE_CHANGES = "yac was stopped before it changed anything."
    const val ALREADY_RUNNING = "Another yac action is still running."
    const val STILL_RUNNING = "yac is still running; try again when it finishes."
    const val CANNOT_BE_UNDONE = "This change cannot be undone: an affected file changed in the editor before yac finished."
    const val PREVIEW_FAILED = "This change cannot be undone: yac could not preview it first."
    const val CAPTURE_FAILED = "This change cannot be undone: its changes could not be captured."
    const val UNDO = "undo"
    const val REDO = "redo"

    fun tooManyFiles(limit: Int): String = "This change cannot be undone: it affects more than $limit PHP files."

    fun changedSince(path: String, action: String): String =
        "Cannot $action: $path changed after this YAC action. Restore it or discard the change, then try again."

    fun unwritable(path: String, action: String): String = "Cannot $action: $path could not be read or written."
}
