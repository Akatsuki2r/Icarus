package dev.icarus.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.icarus.engine.NotifyPolicy
import dev.icarus.engine.Timeline
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A wake-up with the date attached: the last alarm of today must arm tomorrow. */
data class Wake(val date: LocalDate, val time: LocalTime)

/**
 * Wake-ups. The foreground service does the second-by-second work, but a
 * service can be killed by the OS or by a battery cleaner. Alarms are the
 * backstop: they fire at the next transition or alert and put the service
 * back on its feet.
 */
object Alarms {

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** The next moment worth waking for, rolling into tomorrow when today is done. */
    fun nextWake(
        timeline: Timeline,
        now: LocalTime = LocalTime.now(),
        date: LocalDate = timeline.date,
        tomorrow: Timeline? = null,
    ): Wake? {
        candidates(timeline, now).minOrNull()?.let { return Wake(date, it) }
        val nextDay = tomorrow ?: return null
        candidates(nextDay, LocalTime.MIDNIGHT).minOrNull()?.let {
            return Wake(date.plusDays(1), it)
        }
        return null
    }

    private fun candidates(timeline: Timeline, after: LocalTime): List<LocalTime> {
        val blocks = timeline.activeBlocks()
        val alert = NotifyPolicy.next(after, blocks)?.at
        val transition = blocks.firstOrNull { it.start.isAfter(after) }?.start
        return listOfNotNull(alert, transition).filter { it.isAfter(after) }
    }

    fun scheduleNext(context: Context, wake: Wake) {
        scheduleAt(
            context,
            wake.date.atTime(wake.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    /** Used when the foreground service was blocked: retry shortly, not at the next transition. */
    fun scheduleRetry(context: Context, delayMillis: Long = 30_000L) {
        scheduleAt(context, System.currentTimeMillis() + delayMillis)
    }

    private fun scheduleAt(context: Context, trigger: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        try {
            if (canScheduleExact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        Contract.ALARM_REQUEST,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
