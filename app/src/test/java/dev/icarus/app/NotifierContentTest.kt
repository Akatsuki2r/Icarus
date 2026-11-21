package dev.icarus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotifierContentTest {

    @Test
    fun `three distinct alert channels are declared`() {
        val ids = listOf(Contract.CHANNEL_PREPARE, Contract.CHANNEL_START, Contract.CHANNEL_OVERDUE)
        assertEquals(3, ids.toSet().size)
        assertNotEquals(Contract.CHANNEL_LIVE, Contract.CHANNEL_PREPARE)
    }

    @Test
    fun `live copy does not repeat progress as a percentage`() {
        val title = NotificationCopy.liveTitle("Hano development", 24)
        assertEquals("Hano development · 24m left", title)
        assertFalse(title.contains("%"))
    }

    @Test
    fun `chime keys map to raw resources`() {
        assertEquals("chime_prepare", NotificationCopy.rawName("prepare"))
        assertEquals("chime_start", NotificationCopy.rawName("start"))
        assertEquals("chime_overdue", NotificationCopy.rawName("overdue"))
    }
}
