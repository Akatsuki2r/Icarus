package dev.icarus.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Reads only foreground package and dwell time. No keystrokes, no screen text,
 * no notification contents. Access is granted by the user in system settings.
 */
class AppUsageReader(private val context: Context) {

    private val usage = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.checkOpNoThrow(APP_OP_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun foreground(): String? {
        if (!hasAccess()) return null
        val now = System.currentTimeMillis()
        val events = usage?.queryEvents(now - WINDOW_MS, now) ?: return null
        var found: String? = null
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) found = event.packageName
        }
        return found
    }

    fun dwellMillis(pkg: String): Long {
        if (!hasAccess()) return 0L
        val now = System.currentTimeMillis()
        return usage?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - WINDOW_MS, now)
            ?.firstOrNull { it.packageName == pkg }
            ?.totalTimeInForeground ?: 0L
    }

    fun recentPackages(limit: Int = 12): List<String> {
        if (!hasAccess()) return emptyList()
        val now = System.currentTimeMillis()
        return usage?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DAY_MS, now)
            .orEmpty()
            .sortedByDescending { it.totalTimeInForeground }
            .map { it.packageName }
            .take(limit)
    }

    fun productiveSeenWithin(packages: Set<String>, millis: Long): Boolean {
        if (!hasAccess() || packages.isEmpty()) return false
        val now = System.currentTimeMillis()
        val events = usage?.queryEvents(now - millis, now) ?: return false
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.packageName in packages) {
                return true
            }
        }
        return false
    }

    private companion object {
        const val APP_OP_USAGE_STATS = "android:get_usage_stats"
        const val WINDOW_MS = 15 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
