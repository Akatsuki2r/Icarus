package dev.icarus.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.icarus.app.data.DayStateStore
import java.time.LocalDate
import java.time.LocalTime

/**
 * Handles taps on notification buttons. Every action mutates the day, clears
 * the alert that asked the question, and bounces the timekeeper so the live
 * notification reflects reality immediately.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getStringExtra(Contract.EXTRA_BLOCK).orEmpty()
        val minutes = intent.getIntExtra(Contract.EXTRA_MINUTES, 15)
        val store = DayStateStore(context)
        val today = LocalDate.now()

        when (intent.action) {
            Contract.ACTION_STARTED -> {
                if (blockId.isNotEmpty()) acknowledge(store, today, blockId)
            }

            Contract.ACTION_SKIP -> {
                if (blockId.isNotEmpty()) {
                    acknowledge(store, today, blockId)
                    Actions.skip(context, blockId)
                }
            }

            Contract.ACTION_LATE, Contract.ACTION_RECOVER -> {
                if (blockId.isNotEmpty()) acknowledge(store, today, blockId)
                val plan = Actions.recover(context, LocalTime.now())
                val text = when {
                    plan.isEmpty -> "Nothing missed - you're on schedule."
                    else -> "Recovered: ${plan.summary.first()}"
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
 * A reboot should not reset your day. Restart the timekeeper and re-arm the
 * next wake-up; state is already on disk.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                TimekeeperService.start(context)
                Alarms.nextWake(Actions.timeline(context))?.let { Alarms.scheduleNext(context, it) }
            }
        }
    }
}
