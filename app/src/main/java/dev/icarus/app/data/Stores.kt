package dev.icarus.app.data

import android.content.Context
import dev.icarus.engine.DayState
import dev.icarus.engine.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class RoutineStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "routine.json")

    fun load(): Routine {
        if (!file.exists()) {
            save(Defaults.routine)
            return Defaults.routine
        }
        return runCatching { RoutineJson.fromJson(file.readText()) }
            .getOrElse {
                // Never let a broken hand-edit brick the app: keep the bad file, fall back.
                val broken = File(context.filesDir, "routine.broken.json")
                runCatching { file.copyTo(broken, overwrite = true) }
                save(Defaults.routine)
                Defaults.routine
            }
    }

    fun save(routine: Routine) {
        file.parentFile?.mkdirs()
        file.writeText(RoutineJson.toJson(routine))
    }

    fun raw(): String = if (file.exists()) file.readText() else RoutineJson.toJson(Defaults.routine)

    /** Returns null on success, or a human-readable error. */
    fun replaceFromRaw(text: String): String? {
        val parsed = runCatching { RoutineJson.fromJson(text) }.getOrElse {
            return "Not valid routine JSON: ${it.message}"
        }
        if (parsed.allBlocks().isEmpty()) return "The routine has no blocks."
        val problems = parsed.problems()
        if (problems.isNotEmpty()) return problems.joinToString("\n")
        save(parsed)
        return null
    }

    fun reset() {
        save(Defaults.routine)
    }
}

class DayStateStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("icarus.state", Context.MODE_PRIVATE)

    fun load(date: LocalDate): DayState {
        val raw = prefs.getString(key(date), null) ?: return DayState.empty(date)
        return runCatching {
            val json = JSONObject(raw)
            DayState(
                date = date,
                skipped = json.optJSONArray("skipped").toStringSet(),
                completed = json.optJSONArray("completed").toStringSet(),
                plan = json.optJSONArray("plan")?.let { RoutineJson.blocksFromJson(it) },
            )
        }.getOrElse { DayState.empty(date) }
    }

    fun save(state: DayState) {
        val json = JSONObject()
            .put("skipped", JSONArray(state.skipped.toList()))
            .put("completed", JSONArray(state.completed.toList()))
            .apply { put("plan", state.plan?.let { RoutineJson.blocksToJson(it) } ?: JSONObject.NULL) }
        prefs.edit().putString(key(state.date), json.toString()).apply()
        prune(state.date)
    }

    fun sentAlerts(date: LocalDate): Set<String> =
        prefs.getStringSet(sentKey(date), emptySet())?.toSet() ?: emptySet()

    fun markSent(date: LocalDate, key: String) {
        val updated = sentAlerts(date) + key
        prefs.edit().putStringSet(sentKey(date), updated).apply()
    }

    fun clearSent(date: LocalDate) {
        prefs.edit().remove(sentKey(date)).apply()
    }

    /** Stored days, newest first, for the History destination. */
    fun recent(days: Int = 14): List<DayState> = prefs.all.keys
        .filter { it.startsWith("day.") }
        .mapNotNull { key -> runCatching { LocalDate.parse(key.removePrefix("day.")) }.getOrNull() }
        .filter { !it.isBefore(LocalDate.now().minusDays(days.toLong() - 1)) }
        .sortedDescending()
        .map(::load)

    /** Keep two weeks of history, drop everything older. */
    private fun prune(today: LocalDate) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("day.") }
            .mapNotNull { key -> runCatching { LocalDate.parse(key.removePrefix("day.")) }.getOrNull() }
            .filter { it.isBefore(today.minusDays(14)) }
            .forEach { old ->
                editor.remove("day.$old")
                editor.remove("sent.$old")
            }
        editor.apply()
    }

    private fun key(date: LocalDate) = "day.$date"
    private fun sentKey(date: LocalDate) = "sent.$date"

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it, null) }.toSet()
    }
}
