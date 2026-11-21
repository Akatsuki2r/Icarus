package dev.icarus.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

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

    fun foreground(): String? = stats()
        .filter { it.lastTimeUsed > 0 }
        .maxByOrNull { it.lastTimeUsed }
        ?.packageName

    fun dwellMillis(pkg: String): Long =
        stats().firstOrNull { it.packageName == pkg }?.totalTimeInForeground ?: 0L

    fun recentPackages(limit: Int = 12): List<String> {
        if (!hasAccess()) return emptyList()
        val now = System.currentTimeMillis()
        return usage?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DAY_MS, now)
            .orEmpty()
            .sortedByDescending { it.totalTimeInForeground }
            .map { it.packageName }
            .take(limit)
    }

    private fun stats(): List<UsageStats> {
        if (!hasAccess()) return emptyList()
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return usage?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now).orEmpty()
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
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
