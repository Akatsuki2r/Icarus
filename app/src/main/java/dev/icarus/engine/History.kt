package dev.icarus.engine

import java.time.LocalDate

data class DaySummary(
    val date: LocalDate,
    val completed: Int,
    val skipped: Int,
    val reclaimed: Boolean,
)

/**
 * A history view can only report what was actually recorded. Missed blocks
 * are not inferred: an ignored day is not the same as a skipped block.
 */
object History {
    fun summarize(states: List<DayState>): List<DaySummary> = states
        .sortedByDescending { it.date }
        .map { state ->
            DaySummary(
                date = state.date,
                completed = state.completed.size,
                skipped = state.skipped.size,
                reclaimed = state.plan != null,
            )
        }
}
