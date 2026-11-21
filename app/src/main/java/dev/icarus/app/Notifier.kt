package dev.icarus.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.icarus.app.data.SettingsStore
import dev.icarus.app.ui.IcarusColors
import dev.icarus.engine.Alert
import dev.icarus.engine.Moment
import dev.icarus.engine.Timeline
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * All notification wording lives here. The persistent notification is the
 * product: if the app is never opened, the shade should still tell you what
 * you are supposed to be doing and how long is left.
 */
class Notifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                Contract.CHANNEL_LIVE,
                "Timekeeper",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Current block and next transition, always visible."
                setShowBadge(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        listOf(
            Triple(Contract.CHANNEL_PREPARE, "Prepare", "Ten minutes before a block starts."),
            Triple(Contract.CHANNEL_START, "Start", "A block is starting now."),
            Triple(Contract.CHANNEL_OVERDUE, "Overdue", "A strict block is ten minutes late."),
        ).forEach { (id, name, description) ->
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    this.description = description
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    val sound = soundFor(id.removePrefix("alert."))
                    if (sound != null) {
                        setSound(
                            sound,
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .build(),
                        )
                    }
                },
            )
        }
        // The old single alert channel is replaced by the three above.
        manager.deleteNotificationChannel(Contract.CHANNEL_ALERTS)
    }

    private fun soundFor(kind: String): Uri? {
        val choice = SettingsStore(context).chimeFor(kind)
        if (choice == "system") return Settings.System.DEFAULT_NOTIFICATION_URI
        val raw = context.resources.getIdentifier(NotificationCopy.rawName(choice), "raw", context.packageName)
        return if (raw == 0) {
            Settings.System.DEFAULT_NOTIFICATION_URI
        } else {
            Uri.parse("android.resource://${context.packageName}/$raw")
        }
    }

    fun live(timeline: Timeline, moment: Moment, now: LocalTime): Notification {
        val builder = Notification.Builder(context, Contract.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_icarus)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setContentIntent(openApp())
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(IcarusColors.ACCENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        when (moment) {
            is Moment.Active -> {
                val block = moment.block
                val remaining = moment.remaining.toMinutes().coerceAtLeast(0)
                builder
                    .setContentTitle("${block.title} · ${short(remaining)} left")
                    .setContentText(upNextText(timeline, now))
                    .setSubText("${block.start.format(time)} – ${block.end.format(time)}")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(System.currentTimeMillis() + moment.remaining.toMillis())
                    .addAction(action("Started", Contract.ACTION_STARTED, block.id))
                    .addAction(action("Running late", Contract.ACTION_LATE, block.id))
                    .addAction(action("Skip", Contract.ACTION_SKIP, block.id))
            }

            is Moment.Waiting -> {
                val next = moment.next
                if (next != null) {
                    builder
                        .setContentTitle("Next: ${next.title}")
                        .setContentText("Starts in ${short(moment.untilNext.toMinutes().coerceAtLeast(0))}")
                        .setSubText("${next.start.format(time)} – ${next.end.format(time)}")
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setWhen(System.currentTimeMillis() + moment.untilNext.toMillis())
                        .addAction(action("Running late", Contract.ACTION_LATE, next.id))
                } else {
                    builder
                        .setContentTitle("Day finished")
                        .setContentText("Nothing else scheduled. Close it out.")
                }
            }

            Moment.Finished -> builder
                .setContentTitle("Day finished")
                .setContentText("Nothing else scheduled.")
        }

        builder.setStyle(
            Notification.BigTextStyle().bigText(bigText(timeline, moment, now)),
        )
        return builder.build()
    }

    fun alert(alert: Alert, blockTitle: String) {
        val channel = when (alert.kind) {
            dev.icarus.engine.AlertKind.PREPARE -> Contract.CHANNEL_PREPARE
            dev.icarus.engine.AlertKind.START -> Contract.CHANNEL_START
            dev.icarus.engine.AlertKind.OVERDUE -> Contract.CHANNEL_OVERDUE
        }
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_icarus)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(Notification.BigTextStyle().bigText(alert.text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_REMINDER)

        when (alert.kind) {
            dev.icarus.engine.AlertKind.OVERDUE -> {
                builder
                    .addAction(action("Started", Contract.ACTION_STARTED, alert.blockId))
                    .addAction(action("Late 15", Contract.ACTION_LATE, alert.blockId, 15))
                    .addAction(action("Skip", Contract.ACTION_SKIP, alert.blockId))
            }

            dev.icarus.engine.AlertKind.START -> {
                builder
                    .addAction(action("Started", Contract.ACTION_STARTED, alert.blockId))
                    .addAction(action("Not now (+15)", Contract.ACTION_LATE, alert.blockId, 15))
            }

            dev.icarus.engine.AlertKind.PREPARE -> {
                builder.addAction(action("Running late", Contract.ACTION_LATE, alert.blockId, 15))
            }
        }

        manager.notify(alert.key.hashCode(), builder.build())
    }

    fun intervention(request: InterventionRequest) {
        val late = request.kind == InterventionKind.LATE
        val builder = Notification.Builder(context, Contract.CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_stat_icarus)
            .setContentTitle(if (late) "Started one minute ago." else "This block is running.")
            .setContentText("Overlay permission is off. Open Icarus to act.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(action("Snooze 5 min", Contract.ACTION_LATE, request.blockId, 5))
            .addAction(action("I'm on it", Contract.ACTION_STARTED, request.blockId))
        manager.notify(Contract.NOTIFICATION_LIVE + 7, builder.build())
    }

    fun cancel(key: String) = manager.cancel(key.hashCode())

    fun cancelAllAlerts() {
        manager.activeNotifications
            .map { it.id }
            .filter { it != Contract.NOTIFICATION_LIVE }
            .forEach(manager::cancel)
    }

    private fun bigText(timeline: Timeline, moment: Moment, now: LocalTime): String = buildString {
        when (moment) {
            is Moment.Active -> {
                appendLine("${moment.block.title} until ${moment.block.end.format(time)}")
                appendLine("${short(moment.remaining.toMinutes().coerceAtLeast(0))} remaining")
            }

            is Moment.Waiting -> moment.next?.let {
                appendLine("Next: ${it.title} at ${it.start.format(time)}")
            }

            Moment.Finished -> appendLine("Nothing else scheduled.")
        }

        val upcoming = timeline.upcoming(now, 3)
        if (upcoming.isNotEmpty()) {
            appendLine()
            upcoming.forEach { block ->
                appendLine("${block.start.format(time)}  ${block.title}")
            }
        }
        val done = timeline.blocks.count { it.id in timeline.completed }
        appendLine()
        append("$done/${timeline.blocks.size} blocks checked off today")
    }

    private fun upNextText(timeline: Timeline, now: LocalTime): String {
        val next = timeline.nextAfter(now)
        return if (next == null) {
            "Nothing after this."
        } else {
            "Next: ${next.title} at ${next.start.format(time)}"
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(label: String, action: String, blockId: String, minutes: Int = 0): Notification.Action {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(Contract.EXTRA_BLOCK, blockId)
            .putExtra(Contract.EXTRA_MINUTES, minutes)
        val pending = PendingIntent.getBroadcast(
            context,
            (action + blockId + minutes).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_icarus)
        return Notification.Action.Builder(icon, label, pending).build()
    }

    private fun short(minutes: Long): String {
        val duration = Duration.ofMinutes(minutes)
        val h = duration.toHours()
        val m = duration.toMinutes() % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}

/** Pure copy helpers so notification wording is JVM-testable. */
object NotificationCopy {
    fun liveTitle(title: String, minutes: Long): String = "$title · ${short(minutes)} left"
    fun rawName(kind: String): String = "chime_$kind"

    private fun short(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        return when {
            h > 0 && m % 60 > 0 -> "${h}h ${m % 60}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
