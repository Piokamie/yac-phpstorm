package dev.icebear.yac.notes

import dev.icebear.yac.cli.Note
import dev.icebear.yac.cli.NoteStatus

object NotesSelection {
    fun toShow(fresh: List<Note>, previous: List<Note>?): List<Note>? =
        if (null != previous && fresh.isNotEmpty() && fresh.all { NoteStatus.UNPARSEABLE_SOURCE == it.status }) null else fresh
}
