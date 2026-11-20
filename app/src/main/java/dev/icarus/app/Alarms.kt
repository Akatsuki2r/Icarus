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

    /** The next moment worth waking the phone for, or null if the day is over. */
    fun nextWake(timeline: Timeline, now: LocalTime = LocalTime.now()): LocalTime? {
        val alert = NotifyPolicy.next(now, timeline.activeBlocks())?.at
        val transition = timeline.activeBlocks().firstOrNull { it.start.isAfter(now) }?.start
        return listOfNotNull(alert, transition).filter { it.isAfter(now) }.minOrNull()
    }

    fun scheduleNext(context: Context, at: LocalTime) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        val trigger = LocalDate.now()
            .atTime(at)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val exact = canScheduleExact(context)
        try {
            if (exact) {
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
