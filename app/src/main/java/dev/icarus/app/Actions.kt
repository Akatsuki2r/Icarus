package dev.icarus.app

import android.content.Context
import dev.icarus.app.data.DayStateStore
import dev.icarus.app.data.RoutineStore
import dev.icarus.engine.DayState
import dev.icarus.engine.Recovery
import dev.icarus.engine.Timeline
import java.time.LocalDate
import java.time.LocalTime

/**
 * The only place that mutates the day. Both the screen and the notification
 * buttons go through here, so behaviour cannot drift between surfaces.
 */
object Actions {

    fun timeline(context: Context, date: LocalDate = LocalDate.now()): Timeline {
        val routine = RoutineStore(context).load()
        val state = DayStateStore(context).load(date)
        return Timeline.of(date.dayOfWeek, date, routine, state)
    }

    fun state(context: Context, date: LocalDate = LocalDate.now()): DayState =
        DayStateStore(context).load(date)

    fun skip(context: Context, blockId: String) = update(context) { it.withSkip(blockId) }

    fun complete(context: Context, blockId: String) = update(context) { it.withComplete(blockId) }

    fun onTrack(context: Context) = update(context) { it.clearPlan() }

    /**
     * "I'm behind." Rebuild the rest of today from the current block onward.
     * If a plan already exists, recovery runs over that plan, so pressing
     * +15 twice accumulates instead of resetting.
     */
    fun recover(context: Context, now: LocalTime = LocalTime.now()): Recovery.Plan {
        val date = LocalDate.now()
        val current = timeline(context, date)
        val plan = Recovery.plan(now, current.blocks, current.completed)
        DayStateStore(context).save(state(context, date).withPlan(plan.blocks))
        return plan
    }

    private fun update(context: Context, transform: (DayState) -> DayState) {
        val store = DayStateStore(context)
        val date = LocalDate.now()
        store.save(transform(store.load(date)))
    }
}
