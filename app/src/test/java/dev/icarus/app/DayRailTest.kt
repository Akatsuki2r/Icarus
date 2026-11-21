package dev.icarus.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DayRailTest {

    @Test
    fun fractionMapsMidnightToZeroAndNoonToHalf() {
        assertEquals(0f, DayRailView.fraction(LocalTime.MIDNIGHT), 0.001f)
        assertEquals(0.5f, DayRailView.fraction(LocalTime.NOON), 0.001f)
    }

    @Test
    fun fractionMapsEndOfDayToNearlyOne() {
        assertEquals(23.75f / 24f, DayRailView.fraction(LocalTime.of(23, 45)), 0.001f)
    }
}
