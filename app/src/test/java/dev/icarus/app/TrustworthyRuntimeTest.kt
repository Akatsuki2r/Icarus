package dev.icarus.app

import dev.icarus.engine.AlertKind
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.DayState
import dev.icarus.engine.Defer
import dev.icarus.engine.Interrupt
import dev.icarus.engine.NotifyPolicy
import dev.icarus.engine.Priority
import dev.icarus.engine.Routine
import dev.icarus.engine.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TrustworthyRuntimeTest {

    private fun block(id: String, start: String, end: String, priority: Priority = Priority.FIRM) = Block(
        id = id,
        title = id,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        category = Category.PERSONAL,
        priority = priority,
        interrupt = Interrupt.NUDGE,
    )

    @Test
    fun `a delayed alarm still delivers an overdue alert`() {
        val blocks = listOf(
            block("strict", "10:00", "11:00").copy(interrupt = Interrupt.STRICT),
        )
        val due = NotifyPolicy.due(LocalTime.of(10, 30), blocks, emptySet())
        assertTrue(due.any { it.key == "strict:overdue" && it.kind == AlertKind.OVERDUE })
    }

    @Test
    fun `defer moves the block and keeps it before an anchor`() {
        val blocks = listOf(
            block("a", "10:00", "11:00"),
            block("b", "11:00", "12:00"),
            block("wind", "12:30", "13:00", Priority.ANCHOR),
        )
        val shifted = Defer.apply(blocks, "a", 15)
        assertNotNull(shifted)
        shifted!!
        assertEquals(LocalTime.of(10, 15), shifted.first { it.id == "a" }.start)
        assertEquals(LocalTime.of(11, 15), shifted.first { it.id == "b" }.start)
        assertEquals(LocalTime.of(12, 30), shifted.first { it.id == "wind" }.start)
    }

    @Test
    fun `defer is refused when an anchor leaves no room`() {
        val blocks = listOf(
            block("a", "10:00", "11:00"),
            block("wind", "11:00", "12:00", Priority.ANCHOR),
        )
        assertNull(Defer.apply(blocks, "a", 15))
    }

    @Test
    fun `defer of an unknown block is refused`() {
        assertNull(Defer.apply(listOf(block("a", "10:00", "11:00")), "missing", 15))
    }

    @Test
    fun `next wake rolls into tomorrow after the last block`() {
        val date = LocalDate.of(2026, 11, 20)
        val routine = Routine(
            mapOf(
                DayOfWeek.FRIDAY to listOf(block("late", "22:00", "23:00")),
                DayOfWeek.SATURDAY to listOf(block("early", "08:00", "09:00")),
            ),
        )
        val today = Timeline.of(DayOfWeek.FRIDAY, date, routine, DayState.empty(date))
        val tomorrowDate = date.plusDays(1)
        val tomorrow = Timeline.of(
            DayOfWeek.SATURDAY,
            tomorrowDate,
            routine,
            DayState.empty(tomorrowDate),
        )
        val wake = Alarms.nextWake(today, LocalTime.of(23, 30), date, tomorrow)
        assertNotNull(wake)
        assertEquals(tomorrowDate, wake!!.date)
        assertEquals(LocalTime.of(7, 50), wake.time)
    }
}
