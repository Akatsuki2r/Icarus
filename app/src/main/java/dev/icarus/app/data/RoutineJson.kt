package dev.icarus.app.data

import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Interrupt
import dev.icarus.engine.Priority
import dev.icarus.engine.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Plain JSON on disk. Human readable, diffable, easy to hand-edit in the
 * in-app editor. No database needed for a single person's week.
 */
object RoutineJson {

    private const val VERSION = 1
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toJson(routine: Routine): String {
        val days = JSONObject()
        for (day in DayOfWeek.values()) {
            val blocks = routine.blocksFor(day)
            if (blocks.isEmpty()) continue
            days.put(day.name, JSONArray().apply { blocks.forEach { put(blockToJson(it)) } })
        }
        return JSONObject()
            .put("version", VERSION)
            .put("days", days)
            .toString(2)
    }

    fun fromJson(text: String): Routine {
        val root = JSONObject(text)
        val days = root.optJSONObject("days") ?: JSONObject()
        val byDay = mutableMapOf<DayOfWeek, List<Block>>()
        for (day in DayOfWeek.values()) {
            val array = days.optJSONArray(day.name) ?: continue
            val blocks = (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { blockFromJson(it) }
            }
            if (blocks.isNotEmpty()) byDay[day] = blocks
        }
        return Routine(byDay)
    }

    fun blockToJson(block: Block): JSONObject = JSONObject()
        .put("id", block.id)
        .put("title", block.title)
        .put("start", block.start.format(time))
        .put("end", block.end.format(time))
        .put("category", block.category.name)
        .put("priority", block.priority.name)
        .put("interrupt", block.interrupt.name)

    fun blockFromJson(json: JSONObject): Block {
        val start = LocalTime.parse(json.getString("start"), time)
        val end = LocalTime.parse(json.getString("end"), time)
        return Block(
            id = json.getString("id"),
            title = json.getString("title"),
            start = start,
            end = end,
            category = json.optString("category").toEnum(Category.SCHOOL),
            priority = json.optString("priority").toEnum(Priority.FIRM),
            interrupt = json.optString("interrupt").toEnum(Interrupt.NUDGE),
        )
    }

    fun blocksToJson(blocks: List<Block>): JSONArray =
        JSONArray().apply { blocks.forEach { put(blockToJson(it)) } }

    fun blocksFromJson(array: JSONArray?): List<Block> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { blockFromJson(it) } }
    }

    private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)
}
