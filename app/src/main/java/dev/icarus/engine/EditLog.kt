package dev.icarus.engine

import java.time.DayOfWeek
import java.time.Instant

enum class EditKind { CREATE, EDIT, MOVE, DELETE }

data class EditEntry(
    val id: String,
    val at: Instant,
    val kind: EditKind,
    val day: DayOfWeek,
    val before: Block?,
    val after: Block?,
    val note: String,
)

/** An append-only trail of routine edits, newest first. */
object EditLog {
    const val MAX_ENTRIES = 200

    fun add(entries: List<EditEntry>, entry: EditEntry): List<EditEntry> =
        (listOf(entry) + entries).take(MAX_ENTRIES)
}
