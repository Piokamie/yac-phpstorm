package dev.icebear.yac

object YacMessages {
    const val NO_YAC = "No yac binary found; run composer require --dev icebear/yac or set the path in Settings | Tools | YAC."
    const val REMOTE_INTERPRETER = "The project PHP interpreter is remote; yac runs with php from the PATH. Set a local PHP executable in Settings | Tools | YAC."
    const val CANNOT_RUN = "Cannot run yac: "
    const val CANCELLED = "yac was stopped. Files it had already written keep their changes."
}
