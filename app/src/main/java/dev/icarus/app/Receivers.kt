package dev.icarus.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.icarus.app.data.DayStateStore
import java.time.LocalDate
import java.time.LocalTime

/**
 * Handles taps on notification buttons. Every action mutates the day the
 * question belonged to, clears the alert that asked it, and bounces the
 * timekeeper so the live notification reflects reality immediately.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getStringExtra(Contract.EXTRA_BLOCK).orEmpty()
        val minutes = intent.getIntExtra(Contract.EXTRA_MINUTES, 0)
        val date = runCatching {
            intent.getStringExtra(Contract.EXTRA_DAY)?.let(LocalDate::parse)
        }.getOrNull() ?: LocalDate.now()
        val store = DayStateStore(context)

        when (intent.action) {
            Contract.ACTION_STARTED -> {
                if (blockId.isNotEmpty()) {
                    acknowledge(store, date, blockId)
                    Actions.complete(context, blockId, date)
                }
            }

            Contract.ACTION_SKIP -> {
                if (blockId.isNotEmpty()) {
                    acknowledge(store, date, blockId)
                    Actions.skip(context, blockId, date)
                }
            }

            Contract.ACTION_DEFER -> {
                if (blockId.isNotEmpty()) {
                    acknowledge(store, date, blockId)
                    val value = minutes.coerceAtLeast(1)
                    val text = if (Actions.defer(context, blockId, value.toLong(), date)) {
                        "Deferred $blockId by $value min."
                    } else {
                        "No room to defer $blockId before the next anchor."
                    }
                    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            Contract.ACTION_LATE -> {
                if (blockId.isEmpty()) return
                acknowledge(store, date, blockId)
                val text = if (minutes > 0) {
                    if (Actions.defer(context, blockId, minutes.toLong(), date)) {
                        "Deferred $blockId by $minutes min."
                    } else {
                        "No room to defer $blockId before the next anchor."
                    }
                } else {
                    val plan = Actions.recover(context, LocalTime.now(), date)
                    if (plan.isEmpty) "Nothing missed - you're on schedule."
                    else "Recovered: ${plan.summary.first()}"
                }
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
            }

            Contract.ACTION_RECOVER -> {
                val plan = Actions.recover(context, LocalTime.now(), date)
                val text = if (plan.isEmpty) {
                    "Nothing missed - you're on schedule."
                } else {
                    "Recovered: ${plan.summary.first()}"
                }
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        TimekeeperService.start(context)
    }

    private fun acknowledge(store: DayStateStore, date: LocalDate, blockId: String) {
        listOf("prepare", "start", "overdue").forEach { store.markSent(date, "$blockId:$it") }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimekeeperService.start(context)
    }
}

/**
 * A reboot or a date change should not reset your day. Restart the
 * timekeeper and re-arm the next wake-up; state is already on disk.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> {
                TimekeeperService.start(context)
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                Alarms.nextWake(
                    Actions.timeline(context, today),
                    LocalTime.now(),
                    today,
                    Actions.timeline(context, tomorrow),
                )?.let { wake -> Alarms.scheduleNext(context, wake) }
            }
        }
    }
}
