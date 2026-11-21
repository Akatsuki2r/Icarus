package dev.icarus.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** The five areas of life this system actually serves. */
enum class Category(val label: String) {
    SCHOOL("School"),
    HANO("Hano"),
    TRADING("Trading"),
    PERSONAL("Personal"),
    ADMIN("Admin"),
}

/**
 * How much the schedule is allowed to argue with reality.
 *
 * ANCHOR  - immovable: school, sleep, a hard appointment. Recovery never moves it.
 * FIRM    - should happen today, at roughly this time. Recovery may move it.
 * FLEX    - filler. Recovery may move, shorten, or drop it.
 */
enum class Priority { ANCHOR, FIRM, FLEX }

/** How loudly the phone is allowed to interrupt you for this block. */
enum class Interrupt { SILENT, NUDGE, STRICT }

data class Block(
    val id: String,
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val category: Category,
    val priority: Priority = Priority.FIRM,
    val interrupt: Interrupt = Interrupt.NUDGE,
) {
    init {
        require(end.isAfter(start)) { "block '$id' ends at or before it starts" }
    }

    val duration: Duration get() = Duration.between(start, end)

    fun withTimes(start: LocalTime, end: LocalTime): Block = copy(start = start, end = end)

    override fun toString(): String = "$title ${start}-${end}"
}

data class Routine(val byDay: Map<DayOfWeek, List<Block>>) {

    fun blocksFor(day: DayOfWeek): List<Block> = byDay[day].orEmpty().sortedBy { it.start }

    fun allBlocks(): List<Block> = byDay.values.flatten()

    /** A routine is only useful if it is consistent: no overlaps, no duplicate ids. */
    fun problems(): List<String> = buildList {
        allBlocks()
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .forEach { add("duplicate block id '$it'") }

        for ((day, blocks) in byDay) {
            val sorted = blocks.sortedBy { it.start }
            for (i in 1 until sorted.size) {
                if (sorted[i].start < sorted[i - 1].end) {
                    add("$day: '${sorted[i - 1].title}' overlaps '${sorted[i].title}'")
                }
            }
            for (b in sorted) {
                if (b.start.isBefore(LocalTime.of(3, 0)) && b.end.isAfter(LocalTime.of(3, 0))) {
                    add("$day: '${b.title}' crosses midnight, which is not supported")
                }
            }
        }
    }

    companion object {
        val EMPTY = Routine(emptyMap())
    }
}

/** Immutable edits used by the block editor. */
fun Routine.withBlock(day: DayOfWeek, block: Block): Routine = copy(
    byDay = byDay + (day to (blocksFor(day).filterNot { it.id == block.id } + block).sortedBy { it.start }),
)

fun Routine.removeBlock(day: DayOfWeek, id: String): Routine = copy(
    byDay = byDay + (day to blocksFor(day).filterNot { it.id == id }),
)

/** First clean one-hour slot from 06:00 onward. */
fun Routine.nextFreeHour(day: DayOfWeek): LocalTime {
    val blocks = blocksFor(day)
    var candidate = LocalTime.of(6, 0)
    while (candidate.hour < 23) {
        val end = candidate.plusHours(1)
        if (blocks.none { it.start < end && it.end > candidate }) return candidate
        candidate = end
    }
    return LocalTime.of(22, 0)
}
