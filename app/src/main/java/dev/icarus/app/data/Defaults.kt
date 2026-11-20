package dev.icarus.app.data

import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Interrupt
import dev.icarus.engine.Priority
import dev.icarus.engine.Routine
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The starting routine. This is a guess at a workable shape, not a rulebook:
 * edit it in the app until it matches your week. The engine does not care
 * what the blocks are called.
 */
object Defaults {

    private fun t(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    private fun block(
        day: DayOfWeek,
        title: String,
        start: LocalTime,
        end: LocalTime,
        category: Category,
        priority: Priority,
        interrupt: Interrupt,
    ): Block = Block(
        id = "${day.name.take(3).lowercase()}-${"%02d%02d".format(start.hour, start.minute)}-" +
            title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
        title = title,
        start = start,
        end = end,
        category = category,
        priority = priority,
        interrupt = interrupt,
    )

    private fun weekday(day: DayOfWeek): List<Block> = listOf(
        block(day, "Wake + shower", t(6, 30), t(7), Category.PERSONAL, Priority.ANCHOR, Interrupt.SILENT),
        block(day, "French", t(7), t(7, 40), Category.SCHOOL, Priority.FIRM, Interrupt.NUDGE),
        block(day, "Commute", t(7, 40), t(8), Category.ADMIN, Priority.FLEX, Interrupt.SILENT),
        block(day, "School", t(8), t(14, 30), Category.SCHOOL, Priority.ANCHOR, Interrupt.SILENT),
        block(day, "Food + reset", t(14, 30), t(15, 15), Category.PERSONAL, Priority.FIRM, Interrupt.SILENT),
        block(day, "Exercise", t(15, 15), t(16, 15), Category.PERSONAL, Priority.FIRM, Interrupt.NUDGE),
        block(day, "Hano development", t(16, 15), t(18), Category.HANO, Priority.FIRM, Interrupt.STRICT),
        block(day, "Revision", t(18), t(19), Category.SCHOOL, Priority.FIRM, Interrupt.NUDGE),
        block(day, "Trading practice", t(19), t(20), Category.TRADING, Priority.FLEX, Interrupt.NUDGE),
        block(day, "Free time", t(20), t(21), Category.PERSONAL, Priority.FLEX, Interrupt.SILENT),
        block(day, "Life admin", t(21), t(21, 45), Category.ADMIN, Priority.FLEX, Interrupt.SILENT),
        block(day, "Wind down", t(21, 45), t(22, 30), Category.PERSONAL, Priority.ANCHOR, Interrupt.SILENT),
    )

    private val saturday: List<Block> = listOf(
        block(DayOfWeek.SATURDAY, "French", t(8), t(9), Category.SCHOOL, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SATURDAY, "Hano deep work", t(9), t(11), Category.HANO, Priority.FIRM, Interrupt.STRICT),
        block(DayOfWeek.SATURDAY, "Exercise", t(11), t(12), Category.PERSONAL, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SATURDAY, "Errands + admin", t(12), t(14), Category.ADMIN, Priority.FLEX, Interrupt.SILENT),
        block(DayOfWeek.SATURDAY, "Free time", t(14), t(18), Category.PERSONAL, Priority.FLEX, Interrupt.SILENT),
        block(DayOfWeek.SATURDAY, "Trading study", t(18), t(19), Category.TRADING, Priority.FLEX, Interrupt.NUDGE),
        block(DayOfWeek.SATURDAY, "Wind down", t(21, 30), t(22, 30), Category.PERSONAL, Priority.ANCHOR, Interrupt.SILENT),
    )

    private val sunday: List<Block> = listOf(
        block(DayOfWeek.SUNDAY, "French", t(9), t(10), Category.SCHOOL, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SUNDAY, "Weekly review", t(10), t(11), Category.ADMIN, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SUNDAY, "Free time", t(11), t(13), Category.PERSONAL, Priority.FLEX, Interrupt.SILENT),
        block(DayOfWeek.SUNDAY, "Hano development", t(13), t(15), Category.HANO, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SUNDAY, "Free time", t(15), t(19), Category.PERSONAL, Priority.FLEX, Interrupt.SILENT),
        block(DayOfWeek.SUNDAY, "Plan the week", t(19), t(20), Category.ADMIN, Priority.FIRM, Interrupt.NUDGE),
        block(DayOfWeek.SUNDAY, "Wind down", t(21, 30), t(22, 30), Category.PERSONAL, Priority.ANCHOR, Interrupt.SILENT),
    )

    val routine: Routine = Routine(
        mapOf(
            DayOfWeek.MONDAY to weekday(DayOfWeek.MONDAY),
            DayOfWeek.TUESDAY to weekday(DayOfWeek.TUESDAY),
            DayOfWeek.WEDNESDAY to weekday(DayOfWeek.WEDNESDAY),
            DayOfWeek.THURSDAY to weekday(DayOfWeek.THURSDAY),
            DayOfWeek.FRIDAY to weekday(DayOfWeek.FRIDAY),
            DayOfWeek.SATURDAY to saturday,
            DayOfWeek.SUNDAY to sunday,
        ),
    )
}
