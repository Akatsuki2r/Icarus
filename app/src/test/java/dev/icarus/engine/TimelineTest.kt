package dev.icarus.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TimelineTest {

    private fun b(
        id: String,
        start: String,
        end: String,
        priority: Priority = Priority.FIRM,
        interrupt: Interrupt = Interrupt.NUDGE,
        category: Category = Category.SCHOOL,
        title: String = id,
    ) = Block(id, title, LocalTime.parse(start), LocalTime.parse(end), category, priority, interrupt)

    private val date = LocalDate.of(2026, 11, 20) // Friday
    private val routine = Routine(
        mapOf(
            DayOfWeek.FRIDAY to listOf(
                b("revision", "18:00", "19:00"),
                b("trading", "19:00", "20:00"),
                b("free", "20:00", "21:00", Priority.FLEX, Interrupt.SILENT),
                b("wind", "21:00", "22:00", Priority.ANCHOR, Interrupt.SILENT),
            ),
        ),
    )

    private fun timeline(state: DayState = DayState.empty(date)) =
        Timeline.of(date.dayOfWeek, date, routine, state)

    @Test
    fun `reports the running block with elapsed and remaining`() {
        val moment = timeline().moment(LocalTime.parse("18:30"))
        assertTrue(moment is Moment.Active)
        moment as Moment.Active
        assertEquals("revision", moment.block.id)
        assertEquals(Duration.ofMinutes(30), moment.elapsed)
        assertEquals(Duration.ofMinutes(30), moment.remaining)
        assertEquals(0.5f, moment.fraction, 0.001f)
    }

    @Test
    fun `reports the next block during a gap`() {
        val gap = timeline().moment(LocalTime.parse("17:30"))
        assertTrue(gap is Moment.Waiting)
        gap as Moment.Waiting
        assertEquals("revision", gap.next?.id)
        assertEquals(Duration.ofMinutes(30), gap.untilNext)
    }

    @Test
    fun `reports finished after the last block`() {
        assertEquals(Moment.Finished, timeline().moment(LocalTime.parse("23:00")))
        assertNull(timeline().nextAfter(LocalTime.parse("23:00")))
    }

    @Test
    fun `skipped blocks stop occupying time`() {
        val state = DayState.empty(date).withSkip("revision")
        val moment = timeline(state).moment(LocalTime.parse("18:30"))
        assertTrue(moment is Moment.Waiting)
        assertEquals("trading", (moment as Moment.Waiting).next?.id)

        // ...but they stay in the list so the day's record stays honest.
        assertTrue(timeline(state).blocks.any { it.id == "revision" })
    }

    @Test
    fun `a running block is skipped over too`() {
        val state = DayState.empty(date).withSkip("trading")
        val moment = timeline(state).moment(LocalTime.parse("19:30"))
        assertTrue(moment is Moment.Waiting)
        assertEquals("free", (moment as Moment.Waiting).next?.id)
    }

    @Test
    fun `accepted plan replaces the template for the day`() {
        val plan = listOf(b("revision", "20:00", "21:00"))
        val state = DayState.empty(date).withPlan(plan)
        val moment = timeline(state).moment(LocalTime.parse("20:30"))
        assertEquals("revision", (moment as Moment.Active).block.id)
    }

    @Test
    fun `progress counts completed active blocks`() {
        val state = DayState.empty(date)
            .withComplete("revision")
            .withComplete("trading")
        assertEquals(0.5f, timeline(state).progress(), 0.001f)
    }

    @Test
    fun `routine validation catches overlaps`() {
        val broken = Routine(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    b("a", "09:00", "11:00"),
                    b("b", "10:00", "12:00"),
                ),
            ),
        )
        assertFalse(broken.problems().isEmpty())
        assertTrue(routine.problems().isEmpty())
    }
}
