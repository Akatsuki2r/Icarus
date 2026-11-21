package dev.icarus.app.data

import dev.icarus.engine.DayState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Pure JSON boundary for a day's reality; SharedPreferences only stores strings. */
object DayStateJson {

    fun toJson(state: DayState): String = JSONObject()
        .put("skipped", JSONArray(state.skipped.toList()))
        .put("completed", JSONArray(state.completed.toList()))
        .apply { put("plan", state.plan?.let(RoutineJson::blocksToJson) ?: JSONObject.NULL) }
        .toString()

    fun fromJson(raw: String, date: LocalDate): DayState {
        val json = JSONObject(raw)
        return DayState(
            date = date,
            skipped = json.optJSONArray("skipped").toStringSet(),
            completed = json.optJSONArray("completed").toStringSet(),
            plan = json.optJSONArray("plan")?.let(RoutineJson::blocksFromJson),
        )
    }

    fun fromJsonOrEmpty(raw: String?, date: LocalDate): DayState = raw?.let {
        runCatching { fromJson(it, date) }.getOrDefault(DayState.empty(date))
    } ?: DayState.empty(date)

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it, null) }.toSet()
    }
}
