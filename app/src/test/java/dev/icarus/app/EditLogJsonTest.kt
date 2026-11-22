package dev.icarus.app

import dev.icarus.app.data.EditLogJson
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.EditEntry
import dev.icarus.engine.EditKind
import dev.icarus.engine.EditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class EditLogJsonTest {

    private fun block(title: String) = Block(
        "a", title, LocalTime.of(9, 0), LocalTime.of(10, 0), Category.SCHOOL,
    )

    private fun entry(seconds: Long) = EditEntry(
        id = "e$seconds",
        at = Instant.ofEpochSecond(seconds),
        kind = EditKind.EDIT,
        day = DayOfWeek.FRIDAY,
        before = block("Before"),
        after = block("After"),
        note = "moved",
    )

    @Test
    fun `entries are kept newest first and capped`() {
        val all = (0 until 205).fold(emptyList<EditEntry>()) { acc, i -> EditLog.add(acc, entry(i.toLong())) }
        assertEquals(200, all.size)
        assertEquals("e204", all.first().id)
        assertEquals("e5", all.last().id)
    }

    @Test
    fun `log survives a json round trip`() {
        val entries = listOf(entry(10), entry(20))
        val parsed = EditLogJson.fromJsonOrEmpty(EditLogJson.toJson(entries))
        assertEquals(entries, parsed)
    }

    @Test
    fun `a corrupt log falls back to empty`() {
        assertTrue(EditLogJson.fromJsonOrEmpty("{not json").isEmpty())
        assertTrue(EditLogJson.fromJsonOrEmpty(null).isEmpty())
    }

    @Test
    fun `an edit with no block snapshots survives a round trip`() {
        val entry = EditEntry(
            id = "json",
            at = Instant.ofEpochSecond(5),
            kind = EditKind.DELETE,
            day = DayOfWeek.MONDAY,
            before = null,
            after = null,
            note = "JSON edit",
        )
        assertEquals(listOf(entry), EditLogJson.fromJsonOrEmpty(EditLogJson.toJson(listOf(entry))))
    }
}
