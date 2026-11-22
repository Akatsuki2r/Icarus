package dev.icarus.app

import dev.icarus.app.data.RoutineJson
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.DayState
import dev.icarus.engine.Routine
import dev.icarus.engine.Timeline
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DataSafetyTest {

    @Test
    fun `unknown enum values are rejected instead of silently changing meaning`() {
        val json = """
            {"version":1,"days":{"MONDAY":[{"id":"a","title":"A","start":"09:00","end":"10:00","category":"NOT_A_CATEGORY"}]}}
        """.trimIndent()
        val error = runCatching { RoutineJson.fromJson(json) }.exceptionOrNull()
        assertTrue("expected the bad category to be rejected", error != null)
    }

    @Test
    fun `old json without intervention fields uses defaults`() {
        val old = JSONObject()
            .put("id", "legacy")
            .put("title", "Legacy")
            .put("start", "09:00")
            .put("end", "10:00")
            .put("category", "SCHOOL")
        val block = RoutineJson.blockFromJson(old)
        assertEquals(Category.SCHOOL, block.category)
        assertEquals(true, block.interventionEnabled)
        assertEquals(0L, block.dwellLimitMinutes)
        assertEquals(false, block.productiveContext)
    }

    @Test
    fun `clearing an accepted plan makes routine edits visible`() {
        val date = LocalDate.of(2026, 11, 21)
        val old = Block("a", "Old", LocalTime.of(9, 0), LocalTime.of(10, 0), Category.SCHOOL)
        val updated = old.copy(title = "New")
        val routine = Routine(mapOf(DayOfWeek.FRIDAY to listOf(updated)))
        val state = DayState.empty(date).withPlan(listOf(old))

        assertEquals("Old", Timeline.of(DayOfWeek.FRIDAY, date, routine, state).blocks.first().title)
        val afterEdit = state.clearPlan()
        assertEquals("New", Timeline.of(DayOfWeek.FRIDAY, date, routine, afterEdit).blocks.first().title)
    }
}
