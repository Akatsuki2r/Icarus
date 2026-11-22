package dev.icarus.app.data

import dev.icarus.engine.EditEntry
import dev.icarus.engine.EditKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant

/** Pure JSON boundary for the edit trail. */
object EditLogJson {

    fun toJson(entries: List<EditEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(
                JSONObject()
                    .put("id", entry.id)
                    .put("at", entry.at.toString())
                    .put("kind", entry.kind.name)
                    .put("day", entry.day.name)
                    .put("note", entry.note)
                    .put("before", entry.before?.let(RoutineJson::blockToJson) ?: JSONObject.NULL)
                    .put("after", entry.after?.let(RoutineJson::blockToJson) ?: JSONObject.NULL),
            )
        }
    }.toString()

    fun fromJsonOrEmpty(raw: String?): List<EditEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val json = array.optJSONObject(i) ?: return@mapNotNull null
                EditEntry(
                    id = json.getString("id"),
                    at = Instant.parse(json.getString("at")),
                    kind = EditKind.valueOf(json.getString("kind")),
                    day = DayOfWeek.valueOf(json.getString("day")),
                    before = json.optJSONObject("before")?.let(RoutineJson::blockFromJson),
                    after = json.optJSONObject("after")?.let(RoutineJson::blockFromJson),
                    note = json.optString("note"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
