package dev.icarus.engine

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The recovery engine: "I fell behind. What is still worth doing today?"
 *
 * The rule that makes this useful is simple:
 *
 *   Anchors never move. Flexible time absorbs what you missed.
 *
 * If you lose two hours to a rabbit hole at 18:00, the 18:00–19:00 revision
 * block is gone. But tonight's 20:00–21:00 "free time" is a real hour that
 * still exists. Recovery offers to put the revision there. Nothing is
 * silently deleted, nothing is silently stretched, and you approve the
 * proposal before it becomes the plan.
 *
 * Deterministic. Same inputs, same plan.
 */
object Recovery {

    val MIN_FOCUS: Duration = Duration.ofMinutes(10)

    enum class ChangeType { MOVED, SHORTENED, DROPPED, MISSED, KEPT }

    data class Change(
        val block: Block,
        val type: ChangeType,
        val detail: String,
    )

    data class Plan(
        val blocks: List<Block>,
        val changes: List<Change>,
    ) {
        val missed: List<Change> get() = changes.filter { it.type == ChangeType.MISSED }

        /** Short lines for a confirmation dialog or a notification body. */
        val summary: List<String>
            get() = changes.filter { it.type != ChangeType.KEPT }.map { it.detail }

        val isEmpty: Boolean get() = changes.none { it.type != ChangeType.KEPT }
    }

    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun plan(
        now: LocalTime,
        blocks: List<Block>,
        completed: Set<String> = emptySet(),
        minFocus: Duration = MIN_FOCUS,
    ): Plan {
        val ordered = blocks.sortedBy { it.start }
        val changes = mutableListOf<Change>()

        val finished = ordered.filter { !it.end.isAfter(now) }
        val live = ordered.filter { it.end.isAfter(now) }.toMutableList()

        // Flexible slots with real time still in them, earliest first.
        // A slot that is already running only counts from *now*: if you are
        // 30 minutes into free time, you have 30 minutes to give away, not 60.
        val slots = live
            .filter { it.priority == Priority.FLEX && it.end.isAfter(now) }
            .sortedBy { it.start }
            .map { Slot(it, maxOf(it.start, now)) }
            .toMutableList()

        // What genuinely did not happen: finished and never checked off.
        // Anchors are facts, so they are never "reclaimed"; free time is not
        // worth rescheduling either.
        val missed = finished
            .filter { it.id !in completed && it.priority == Priority.FIRM }
            .sortedBy { it.start }

        finished.forEach { changes += Change(it, ChangeType.KEPT, "${it.title} is in the past") }

        for (block in missed) {
            val slot = slots.firstOrNull { candidate ->
                candidate.length >= minFocus && candidate.length >= block.duration.dividedBy(3)
            }

            if (slot == null) {
                changes += Change(
                    block,
                    ChangeType.MISSED,
                    "${block.title} missed - no flexible time left today",
                )
                continue
            }

            val end = minOf(slot.end, slot.from.plus(block.duration))
            val gained = Duration.between(slot.from, end)

            // Take over the slot's time and identity: one row per time range,
            // so alerts and completion stay attached to a single block.
            val placed = block.withTimes(slot.from, end).copy(id = slot.block.id)

            live[live.indexOf(slot.block)] = placed
            slots.remove(slot)

            changes += Change(
                placed,
                if (gained == block.duration) ChangeType.MOVED else ChangeType.SHORTENED,
                "${block.title}: ${if (gained == block.duration) "reclaimed" else "partly reclaimed"} " +
                    "in ${slot.from.format(clock)}-${slot.end.format(clock)} " +
                    "(${gained.toMinutes()}m of ${block.duration.toMinutes()}m)",
            )
        }

        val result = (finished + live).sortedBy { it.start }

        // Invariant: a plan may never double-book you.
        check(result.zipWithNext().all { (a, b) -> !a.end.isAfter(b.start) }) {
            "recovery produced overlapping blocks"
        }

        return Plan(result, changes)
    }

    /** A stretch of flexible time that still has minutes left in it. */
    private data class Slot(val block: Block, val from: LocalTime) {
        val end: LocalTime get() = block.end
        val length: Duration get() = Duration.between(from, block.end)
    }
}
