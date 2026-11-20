package dev.icarus.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class RecoveryTest {

    private fun b(
        id: String,
        start: String,
        end: String,
        priority: Priority = Priority.FIRM,
        title: String = id,
    ) = Block(id, title, LocalTime.parse(start), LocalTime.parse(end), Category.SCHOOL, priority, Interrupt.NUDGE)

    /** A normal evening: revision 18-19, trading 19-20, free 20-21, wind-down anchor 21-22. */
    private fun evening() = listOf(
        b("revision", "18:00", "19:00"),
        b("trading", "19:00", "20:00", Priority.FLEX),
        b("free", "20:00", "21:00", Priority.FLEX, title = "Free time"),
        b("wind", "21:00", "22:00", Priority.ANCHOR, title = "Wind down"),
    )

    @Test
    fun `missed firm block is reclaimed inside flexible time`() {
        // 20:30: revision never happened, but 30 minutes of free time remain.
        val plan = Recovery.plan(LocalTime.parse("20:30"), evening(), completed = emptySet())

        val reclaimed = plan.blocks.first { it.id == "free" }
        assertEquals("revision", reclaimed.title)
        assertEquals(LocalTime.parse("20:30"), reclaimed.start)
        assertEquals(LocalTime.parse("21:00"), reclaimed.end)

        assertTrue(plan.summary.any { it.contains("partly reclaimed") && it.contains("30m of 60m") })
    }

    @Test
    fun `a shorter slot produces a shortened block, not a lie`() {
        val blocks = listOf(
            b("revision", "18:00", "19:30"), // 90 minutes missed
            b("free", "20:00", "21:00", Priority.FLEX, "Free time"),
            b("wind", "21:00", "22:00", Priority.ANCHOR, "Wind down"),
        )
        val plan = Recovery.plan(LocalTime.parse("19:40"), blocks)

        val placed = plan.blocks.first { it.id == "free" }
        assertEquals(LocalTime.parse("20:00"), placed.start)
        assertEquals(LocalTime.parse("21:00"), placed.end)
        assertTrue(plan.summary.any { it.contains("partly reclaimed") && it.contains("60m of 90m") })
    }

    @Test
    fun `anchors are never moved by recovery`() {
        val blocks = listOf(
            b("revision", "18:00", "19:00"),
            b("wind", "21:00", "22:00", Priority.ANCHOR, "Wind down"),
        )
        val plan = Recovery.plan(LocalTime.parse("20:30"), blocks)
        val wind = plan.blocks.first { it.id == "wind" }
        assertEquals(LocalTime.parse("21:00"), wind.start)
        assertEquals(LocalTime.parse("22:00"), wind.end)
    }

    @Test
    fun `nothing is invented when there is no flexible time`() {
        val blocks = listOf(
            b("revision", "18:00", "19:00"),
            b("wind", "21:00", "22:00", Priority.ANCHOR, "Wind down"),
        )
        val plan = Recovery.plan(LocalTime.parse("20:30"), blocks)
        assertEquals(blocks, plan.blocks)
        assertEquals(1, plan.missed.size)
        assertTrue(plan.summary.any { it.contains("no flexible time left today") })
    }

    @Test
    fun `completed work is not reclaimed`() {
        val plan = Recovery.plan(LocalTime.parse("20:30"), evening(), completed = setOf("revision"))
        assertTrue(plan.missed.isEmpty())
        assertEquals(LocalTime.parse("20:00"), plan.blocks.first { it.id == "free" }.start)
    }

    @Test
    fun `free time is not treated as a miss`() {
        val plan = Recovery.plan(LocalTime.parse("21:30"), evening())
        assertTrue(plan.missed.none { it.block.id == "free" || it.block.id == "trading" })
    }

    @Test
    fun `recovery never produces overlapping blocks`() {
        val plan = Recovery.plan(LocalTime.parse("20:05"), evening())
        plan.blocks.zipWithNext().forEach { (a, b) ->
            assertFalse("${a.title} overlaps ${b.title}", a.end.isAfter(b.start))
        }
    }

    @Test
    fun `an on-track evening needs no changes`() {
        val plan = Recovery.plan(LocalTime.parse("17:30"), evening())
        assertTrue(plan.isEmpty)
        assertTrue(plan.summary.isEmpty())
    }
}
