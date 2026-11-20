package dev.icarus.app

import dev.icarus.app.data.Defaults
import dev.icarus.app.data.RoutineJson
import dev.icarus.engine.DayState
import dev.icarus.engine.Recovery
import dev.icarus.engine.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RoutinesTest {

    @Test
    fun `default routine is internally valid`() {
        assertTrue(Defaults.routine.problems().joinToString(), Defaults.routine.problems().isEmpty())
        assertEquals(7, Defaults.routine.byDay.size)
        DayOfWeek.values().forEach { day ->
            assertTrue("$day has no blocks", Defaults.routine.blocksFor(day).isNotEmpty())
        }
    }

    @Test
    fun `routine survives a json round trip`() {
        val restored = RoutineJson.fromJson(RoutineJson.toJson(Defaults.routine))
        assertEquals(Defaults.routine, restored)
    }

    @Test
    fun `every block belongs to a category and has a stable id`() {
        val ids = Defaults.routine.allBlocks().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Defaults.routine.allBlocks().all { it.title.isNotBlank() })
    }

    @Test
    fun `a normal weekday resolves to a timeline`() {
        val date = LocalDate.of(2026, 11, 18) // Wednesday
        val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))
        assertTrue(timeline.blocks.isNotEmpty())
        assertNotNull(timeline.moment(LocalTime.parse("19:30")))
    }

    @Test
    fun `losing the afternoon still leaves a usable evening`() {
        val date = LocalDate.of(2026, 11, 18)
        val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))
        val plan = Recovery.plan(LocalTime.parse("21:00"), timeline.blocks, timeline.completed)

        // Anchors must be untouched.
        val wind = plan.blocks.first { it.title == "Wind down" }
        assertEquals(LocalTime.parse("21:45"), wind.start)
        assertEquals(LocalTime.parse("22:30"), wind.end)

        // And the plan must be coherent.
        plan.blocks.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.title} overlaps ${b.title}", !a.end.isAfter(b.start))
        }
    }
}
