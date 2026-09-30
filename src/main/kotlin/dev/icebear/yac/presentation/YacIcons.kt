package dev.icebear.yac.presentation

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object YacIcons {
    @JvmField
    val NOTE: Icon = IconLoader.getIcon("/icons/note.svg", YacIcons::class.java)

    @JvmField
    val NOTE_PROBLEM: Icon = IconLoader.getIcon("/icons/noteProblem.svg", YacIcons::class.java)
}
