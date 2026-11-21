package dev.icarus.app

import dev.icarus.engine.AlertKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParityTest {

    @Test
    fun `heads-up alerts carry at most two actions`() {
        AlertKind.entries.forEach { kind ->
            assertTrue(kind.name, NotificationCopy.actionLabels(kind).size <= 2)
        }
    }

    @Test
    fun `fallback keeps snooze on the late variant only`() {
        assertEquals(2, NotificationCopy.fallbackActions(late = true).size)
        assertTrue(NotificationCopy.fallbackActions(late = true).contains("Snooze 5 min"))
        assertEquals(listOf("I'm on it"), NotificationCopy.fallbackActions(late = false))
    }
}
