package dev.icebear.yac.actions.undo

import java.nio.file.attribute.PosixFilePermission

internal class FileChange(
    val before: ByteArray?,
    val after: ByteArray?,
    val beforePermissions: Set<PosixFilePermission>? = null,
    val afterPermissions: Set<PosixFilePermission>? = null,
)
