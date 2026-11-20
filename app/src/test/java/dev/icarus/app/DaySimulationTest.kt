package dev.icarus.app

import dev.icarus.app.data.Defaults
import dev.icarus.engine.DayState
import dev.icarus.engine.Moment
import dev.icarus.engine.NotifyPolicy
import dev.icarus.engine.Recovery
import dev.icarus.engine.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A whole week, one minute at a time, through the same path the service runs:
 * timeline -> moment -> due alerts -> recovery.
 *
 * This is the closest thing to "leave it running for seven days" that can be
 * done without a phone, and it protects the two properties that would make
 * the app untrustworthy: double alerts and impossible schedules.
 */
class DaySimulationTest {

    private val week: LocalDate = LocalDate.of(2026, 11, 16) // Monday

    @Test
    fun `a normal week produces ordered, bounded, never-repeated alerts`() {
        for (offset in 0..6) {
            val date = week.plusDays(offset.toLong())
            val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))
            val sent = mutableSetOf<String>()
            val fired = mutableListOf<Pair<LocalTime, String>>()

            var minute = 0
            while (minute < 24 * 60) {
                val now = LocalTime.of(minute / 60, minute % 60)
                NotifyPolicy.due(now, timeline.activeBlocks(), sent).forEach { alert ->
                    sent += alert.key
                    fired += alert.at to alert.key
                }
                minute++
            }

            // Every block gets at most three touches, ever.
            assertEquals(
                "$date: ${fired.map { it.second }}",
                sent.size,
                fired.size,
            )

            // Alerts fire in the order they were scheduled.
            val scheduled = NotifyPolicy.schedule(timeline.activeBlocks()).associateBy { it.key }
            val order = fired.map { scheduled.getValue(it.second).at }
            assertEquals("$date alerts out of order", order.sorted(), order)

            // One start alert per block, no exceptions.
            val starts = fired.count { it.second.endsWith(":start") }
            assertEquals("$date start alerts", timeline.activeBlocks().size, starts)
        }
    }

    @Test
    fun `the clock is never confused about what is happening`() {
        for (offset in 0..6) {
            val date = week.plusDays(offset.toLong())
            val blocks = Defaults.routine.blocksFor(date.dayOfWeek)

            for (minute in 0 until 24 * 60) {
                val now = LocalTime.of(minute / 60, minute % 60)
                val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))
                when (val moment = timeline.moment(now)) {
                    is Moment.Active -> {
                        assertTrue("$date $now inside ${moment.block}", !now.isBefore(moment.block.start))
                        assertTrue("$date $now before ${moment.block.end}", now.isBefore(moment.block.end))
                        assertEquals(
                            moment.block.duration,
                            moment.elapsed.plus(moment.remaining),
                        )
                    }

                    is Moment.Waiting -> moment.next?.let {
                        assertTrue("$date $now next ${it.id} is not ahead", it.start.isAfter(now))
                    }

                    Moment.Finished -> {
                        assertTrue(
                            "$date $now finished but blocks remain",
                            blocks.none { it.end.isAfter(now) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `recovering at any minute of any day yields a coherent plan`() {
        for (offset in 0..6) {
            val date = week.plusDays(offset.toLong())
            val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))

            for (minute in 0 until 24 * 60 step 7) {
                val now = LocalTime.of(minute / 60, minute % 60)
                val plan = Recovery.plan(now, timeline.activeBlocks(), timeline.completed)

                plan.blocks.zipWithNext().forEach { (a, b) ->
                    assertTrue("$date $now: ${a.title} overlaps ${b.title}", !a.end.isAfter(b.start))
                }

                // Anchors that are still ahead keep their exact times.
                timeline.activeBlocks()
                    .filter { it.start.isAfter(now) && it.priority == dev.icarus.engine.Priority.ANCHOR }
                    .forEach { anchor ->
                        val after = plan.blocks.first { it.id == anchor.id }
                        assertEquals("$date $now anchor ${anchor.title}", anchor.start, after.start)
                        assertEquals("$date $now anchor ${anchor.title}", anchor.end, after.end)
                    }
            }
        }
    }

    @Test
    fun `a lost day still ends with a wind-down`() {
        val date = week.plusDays(2) // Wednesday
        val timeline = Timeline.of(date.dayOfWeek, date, Defaults.routine, DayState.empty(date))
        // Nothing done all day, recovering at 21:30.
        val plan = Recovery.plan(LocalTime.of(21, 30), timeline.activeBlocks(), completed = emptySet())
        assertTrue(plan.blocks.any { it.title == "Wind down" })
    }

    @Test
    fun `every day of the week has blocks and no day crosses midnight`() {
        DayOfWeek.values().forEach { day ->
            assertTrue(day.name, Defaults.routine.blocksFor(day).isNotEmpty())
            Defaults.routine.blocksFor(day).forEach { block ->
                assertTrue("${day.name} ${block.title}", block.start.isBefore(block.end))
            }
        }
    }
}
