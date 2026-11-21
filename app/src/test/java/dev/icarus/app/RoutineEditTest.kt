package dev.icarus.app

import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Routine
import dev.icarus.engine.nextFreeHour
import dev.icarus.engine.removeBlock
import dev.icarus.engine.withBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class RoutineEditTest {

    private fun block(id: String, hour: Int, minute: Int = 0) = Block(
        id = id,
        title = id,
        start = LocalTime.of(hour, minute),
        end = LocalTime.of(hour, minute).plusMinutes(60),
        category = Category.SCHOOL,
    )

    @Test
    fun overlapRejected() {
        val routine = Routine(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("a", 8),
                    Block("b", "B", LocalTime.of(8, 30), LocalTime.of(9, 30), Category.SCHOOL),
                ),
            ),
        )
        assertTrue(routine.problems().any { it.contains("overlaps") })
    }

    @Test
    fun withBlockReplacesSameIdAndKeepsSortOrder() {
        val routine = Routine(mapOf(DayOfWeek.MONDAY to listOf(block("a", 8), block("b", 10))))
        val edited = routine.withBlock(DayOfWeek.MONDAY, block("b", 9))
        val monday = edited.blocksFor(DayOfWeek.MONDAY)
        assertEquals(listOf("a", "b"), monday.map { it.id })
        assertEquals(LocalTime.of(9, 0), monday[1].start)
        assertEquals(2, routine.blocksFor(DayOfWeek.MONDAY).size)
    }

    @Test
    fun removeBlockLeavesOtherDaysUntouched() {
        val routine = Routine(
            mapOf(
                DayOfWeek.MONDAY to listOf(block("a", 8)),
                DayOfWeek.TUESDAY to listOf(block("b", 9)),
            ),
        )
        val edited = routine.removeBlock(DayOfWeek.MONDAY, "a")
        assertTrue(edited.blocksFor(DayOfWeek.MONDAY).isEmpty())
        assertEquals(1, edited.blocksFor(DayOfWeek.TUESDAY).size)
    }

    @Test
    fun nextFreeHourFindsFirstCleanSlot() {
        val routine = Routine(mapOf(DayOfWeek.MONDAY to listOf(block("a", 6), block("b", 8))))
        assertEquals(LocalTime.of(7, 0), routine.nextFreeHour(DayOfWeek.MONDAY))
    }
}
