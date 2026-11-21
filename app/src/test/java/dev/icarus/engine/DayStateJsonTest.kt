package dev.icarus.engine

import dev.icarus.app.data.DayStateJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayStateJsonTest {

    private fun block(id: String) = Block(
        id, id, LocalTime.of(9, 0), LocalTime.of(10, 0), Category.SCHOOL,
    )

    @Test
    fun `skipped completed and plan survive a round trip`() {
        val date = LocalDate.of(2026, 11, 21)
        val state = DayState(
            date = date,
            skipped = setOf("a"),
            completed = setOf("b"),
            plan = listOf(block("planned")),
        )
        val parsed = DayStateJson.fromJson(DayStateJson.toJson(state), date)
        assertEquals(state.skipped, parsed.skipped)
        assertEquals(state.completed, parsed.completed)
        assertEquals(listOf("planned"), parsed.plan?.map { it.id })
    }

    @Test
    fun `a corrupt record falls back to an empty day`() {
        val date = LocalDate.of(2026, 11, 21)
        val state = DayStateJson.fromJsonOrEmpty("{not json", date)
        assertTrue(state.skipped.isEmpty())
        assertTrue(state.completed.isEmpty())
        assertEquals(null, state.plan)
    }

    @Test
    fun `a day with no record is empty`() {
        val date = LocalDate.of(2026, 11, 21)
        assertEquals(DayState.empty(date), DayStateJson.fromJsonOrEmpty(null, date))
    }
}
