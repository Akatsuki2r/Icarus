package dev.icarus.app

import dev.icarus.engine.DayState
import dev.icarus.engine.History
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistorySummaryTest {

    private fun state(date: LocalDate, done: Int, skipped: Int, planned: Boolean) = DayState(
        date = date,
        completed = (0 until done).map { "done$it" }.toSet(),
        skipped = (0 until skipped).map { "skip$it" }.toSet(),
        plan = if (planned) listOf(
            dev.icarus.engine.Block(
                "p", "P", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0),
                dev.icarus.engine.Category.SCHOOL,
            ),
        ) else null,
    )

    @Test
    fun `history is newest first and counts what is actually stored`() {
        val today = LocalDate.of(2026, 11, 21)
        val summaries = History.summarize(
            listOf(
                state(today.minusDays(2), done = 4, skipped = 1, planned = true),
                state(today, done = 2, skipped = 0, planned = false),
            ),
        )
        assertEquals(today, summaries.first().date)
        assertEquals(2, summaries.first().completed)
        assertEquals(0, summaries.first().skipped)
        assertEquals(false, summaries.first().reclaimed)
        assertEquals(4, summaries[1].completed)
        assertEquals(1, summaries[1].skipped)
        assertEquals(true, summaries[1].reclaimed)
    }

    @Test
    fun `empty history stays empty`() {
        assertEquals(emptyList<Any>(), History.summarize(emptyList()))
    }
}
