package dev.icarus.app

import android.content.Context
import dev.icarus.app.data.DayStateStore
import dev.icarus.app.data.RoutineStore
import dev.icarus.engine.DayState
import dev.icarus.engine.Defer
import dev.icarus.engine.Recovery
import dev.icarus.engine.Timeline
import java.time.LocalDate
import java.time.LocalTime

/**
 * The only place that mutates the day. Both the screen and the notification
 * buttons go through here, so behaviour cannot drift between surfaces.
 * Every mutation takes the date carried by the notification action, so a tap
 * just after midnight still edits the day the question belonged to.
 */
object Actions {

    fun timeline(context: Context, date: LocalDate = LocalDate.now()): Timeline {
        val routine = RoutineStore(context).load()
        val state = DayStateStore(context).load(date)
        return Timeline.of(date.dayOfWeek, date, routine, state)
    }

    fun state(context: Context, date: LocalDate = LocalDate.now()): DayState =
        DayStateStore(context).load(date)

    fun skip(context: Context, blockId: String, date: LocalDate = LocalDate.now()) =
        update(context, date) { it.withSkip(blockId) }

    fun complete(context: Context, blockId: String, date: LocalDate = LocalDate.now()) =
        update(context, date) { it.withComplete(blockId) }

    fun uncomplete(context: Context, blockId: String, date: LocalDate = LocalDate.now()) =
        update(context, date) { it.withUncomplete(blockId) }

    fun onTrack(context: Context, date: LocalDate = LocalDate.now()) =
        update(context, date) { it.clearPlan() }

    /** "Not now, +15": a short defer, not a whole-day recovery. */
    fun defer(
        context: Context,
        blockId: String,
        minutes: Long,
        date: LocalDate = LocalDate.now(),
    ): Boolean {
        val blocks = timeline(context, date).activeBlocks()
        val shifted = Defer.apply(blocks, blockId, minutes) ?: return false
        val store = DayStateStore(context)
        store.save(store.load(date).withPlan(shifted))
        return true
    }

    /**
     * "I'm behind." Rebuild the rest of today from the current block onward.
     * If a plan already exists, recovery runs over that plan, so pressing
     * recover twice accumulates instead of resetting.
     */
    fun recover(
        context: Context,
        now: LocalTime = LocalTime.now(),
        date: LocalDate = LocalDate.now(),
    ): Recovery.Plan {
        val current = timeline(context, date)
        val plan = Recovery.plan(now, current.blocks, current.completed)
        DayStateStore(context).save(state(context, date).withPlan(plan.blocks))
        return plan
    }

    private fun update(context: Context, date: LocalDate, transform: (DayState) -> DayState) {
        val store = DayStateStore(context)
        store.save(transform(store.load(date)))
    }
}
