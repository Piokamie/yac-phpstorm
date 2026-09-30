package dev.icebear.yac.cli

open class YacCliException(message: String) : RuntimeException(message)

class YacTooOldException : YacCliException("The installed yac has no `context --stdin`; update icebear/yac.")
