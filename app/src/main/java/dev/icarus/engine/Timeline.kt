package dev.icarus.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * What actually happened today, layered on top of the weekly template.
 *
 * The template is the plan. DayState is reality. The runtime always reads
 * template + state, so a restart, a reboot, or an accidental swipe never
 * loses where you are.
 */
data class DayState(
    val date: LocalDate,
    val skipped: Set<String> = emptySet(),
    val completed: Set<String> = emptySet(),
    /** Accepted recovery plan for this date. null means "still on the template". */
    val plan: List<Block>? = null,
) {
    fun withSkip(id: String) = copy(skipped = skipped + id)
    fun withComplete(id: String) = copy(completed = completed + id)
    fun withPlan(blocks: List<Block>?) = copy(plan = blocks)
    fun clearPlan() = copy(plan = null)

    companion object {
        fun empty(date: LocalDate) = DayState(date)
    }
}

/** What the clock says right now, relative to the day's blocks. */
sealed interface Moment {

    /** A block is running. */
    data class Active(
        val block: Block,
        val elapsed: Duration,
        val remaining: Duration,
    ) : Moment {
        val fraction: Float
            get() {
                val total = block.duration.toMillis().coerceAtLeast(1)
                return (elapsed.toMillis().toFloat() / total).coerceIn(0f, 1f)
            }
    }

    /** Nothing is running; the next block is still ahead. */
    data class Waiting(val next: Block?, val untilNext: Duration) : Moment

    /** Nothing left today. */
    data object Finished : Moment
}

/**
 * A resolved day: every block that belongs to today, in order, plus the
 * state needed to explain it. Skipped blocks stay in the list so the day's
 * record remains honest - they are just ignored by the clock.
 */
data class Timeline(
    val date: LocalDate,
    val blocks: List<Block>,
    val skipped: Set<String> = emptySet(),
    val completed: Set<String> = emptySet(),
) {

    fun activeBlocks(): List<Block> = blocks.filter { it.id !in skipped }

    fun moment(at: LocalTime): Moment {
        val active = activeBlocks().lastOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }
        if (active != null) {
            return Moment.Active(
                block = active,
                elapsed = Duration.between(active.start, at),
                remaining = Duration.between(at, active.end),
            )
        }
        val next = nextAfter(at)
        return if (next != null) {
            Moment.Waiting(next, Duration.between(at, next.start))
        } else {
            Moment.Finished
        }
    }

    fun nextAfter(at: LocalTime): Block? =
        activeBlocks().firstOrNull { it.start.isAfter(at) }

    /** Blocks that have not started yet, in order. */
    fun upcoming(at: LocalTime, limit: Int = 4): List<Block> =
        activeBlocks().filter { it.start.isAfter(at) }.take(limit)

    fun currentBlock(at: LocalTime): Block? =
        activeBlocks().lastOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }

    fun isDone(block: Block): Boolean = block.id in completed

    fun progress(): Float {
        val active = activeBlocks()
        if (active.isEmpty()) return 0f
        return active.count { it.id in completed }.toFloat() / active.size
    }

    companion object {

        /**
         * Resolve template + state into the day that will actually be executed.
         *
         * Accepted recovery plans win outright. Otherwise the template runs as
         * written. Skipped blocks are kept for display but excluded from time.
         */
        fun of(day: DayOfWeek, date: LocalDate, routine: Routine, state: DayState): Timeline {
            val source = state.plan ?: routine.blocksFor(day)
            return Timeline(
                date = date,
                blocks = source.sortedBy { it.start },
                skipped = state.skipped,
                completed = state.completed,
            )
        }
    }
}
