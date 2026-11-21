package dev.icarus.app

import dev.icarus.app.ui.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellNavigationTest {

    @Test
    fun `four destinations are fixed and ordered`() {
        assertEquals(
            listOf("TODAY", "ROUTINE", "HISTORY", "SETTINGS"),
            Destination.entries.map { it.name },
        )
    }
}
