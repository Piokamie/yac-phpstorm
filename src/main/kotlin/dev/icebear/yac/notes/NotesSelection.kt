package dev.icebear.yac.notes

import dev.icebear.yac.cli.Note

object NotesSelection {
    const val UNPARSEABLE_SOURCE = "unparseable_source"

    fun toShow(fresh: List<Note>, previous: List<Note>?): List<Note> =
        if (null != previous && fresh.isNotEmpty() && fresh.all { UNPARSEABLE_SOURCE == it.status }) previous else fresh
}
