package dev.icarus.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NotifyPolicyTest {

    private fun b(
        id: String,
        start: String,
        end: String,
        interrupt: Interrupt = Interrupt.NUDGE,
    ) = Block(id, id, LocalTime.parse(start), LocalTime.parse(end), Category.SCHOOL, Priority.FIRM, interrupt)

    @Test
    fun `a block gets a prepare, a start and no overdue by default`() {
        val alerts = NotifyPolicy.schedule(listOf(b("revision", "18:00", "19:00")))
        assertEquals(listOf(AlertKind.PREPARE, AlertKind.START), alerts.map { it.kind })
        assertEquals(LocalTime.parse("17:50"), alerts.first().at)
    }

    @Test
    fun `strict blocks get one overdue check and nothing more`() {
        val alerts = NotifyPolicy.schedule(listOf(b("hano", "16:15", "18:00", Interrupt.STRICT)))
        assertEquals(
            listOf(AlertKind.PREPARE, AlertKind.START, AlertKind.OVERDUE),
            alerts.map { it.kind },
        )
        assertEquals(LocalTime.parse("16:25"), alerts.last().at)
    }

    @Test
    fun `silent blocks only announce their start`() {
        val alerts = NotifyPolicy.schedule(listOf(b("sleep", "22:00", "23:00", Interrupt.SILENT)))
        assertEquals(listOf(AlertKind.START), alerts.map { it.kind })
    }

    @Test
    fun `very short blocks skip the prepare nudge`() {
        val alerts = NotifyPolicy.schedule(listOf(b("commute", "07:45", "08:00")))
        assertEquals(listOf(AlertKind.START), alerts.map { it.kind })
    }

    @Test
    fun `alerts fire only inside their window`() {
        val blocks = listOf(b("revision", "18:00", "19:00"))

        assertTrue(NotifyPolicy.due(LocalTime.parse("17:49"), blocks, emptySet()).isEmpty())
        assertEquals(
            listOf(AlertKind.PREPARE),
            NotifyPolicy.due(LocalTime.parse("17:50"), blocks, emptySet()).map { it.kind },
        )
        assertEquals(
            listOf(AlertKind.START),
            NotifyPolicy.due(LocalTime.parse("18:00"), blocks, emptySet()).map { it.kind },
        )
    }

    @Test
    fun `a phone that was off does not dump a backlog`() {
        val blocks = listOf(b("revision", "18:00", "19:00"))
        // 25 minutes late: both alerts are stale.
        assertTrue(NotifyPolicy.due(LocalTime.parse("18:25"), blocks, emptySet()).isEmpty())
    }

    @Test
    fun `already sent alerts are not repeated`() {
        val blocks = listOf(b("revision", "18:00", "19:00"))
        val sent = setOf("revision:start", "revision:prepare")
        assertTrue(NotifyPolicy.due(LocalTime.parse("18:00"), blocks, sent).isEmpty())
    }

    @Test
    fun `next alert looks forward only`() {
        val blocks = listOf(b("revision", "18:00", "19:00"))
        val next = NotifyPolicy.next(LocalTime.parse("17:00"), blocks)
        assertEquals(LocalTime.parse("17:50"), next?.at)
        assertNull(NotifyPolicy.next(LocalTime.parse("20:00"), blocks))
    }
}
