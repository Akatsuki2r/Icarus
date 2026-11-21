package dev.icarus.app

/** Pure helpers for the diagnostics surface; wording the user can act on. */
object DiagnosticsPolicy {

    fun heartbeat(lastTickMillis: Long, nowMillis: Long): String {
        if (lastTickMillis <= 0L) return "not reporting"
        val age = (nowMillis - lastTickMillis).coerceAtLeast(0)
        return when {
            age < 30_000L -> "running · just now"
            age < 120_000L -> "running · ${age / 1000}s ago"
            else -> "stalled · ${age / 60_000}m ago"
        }
    }

    fun interventionStatus(enabled: Boolean, dwellMinutes: Int): String =
        if (enabled) "observe ${dwellMinutes}m, then ask" else "interventions are off"
}
