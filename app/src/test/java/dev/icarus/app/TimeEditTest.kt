package dev.icarus.app

import dev.icarus.app.ui.TimeEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeEditTest {

    @Test
    fun shiftStaysInsideTheDay() {
        assertNull(TimeEdit.shift(LocalTime.of(23, 45), 15))
        assertNull(TimeEdit.shift(LocalTime.of(0, 0), -15))
        assertEquals(LocalTime.of(23, 45), TimeEdit.shift(LocalTime.of(23, 30), 15))
    }

    @Test
    fun shiftMovesInFifteenMinuteSteps() {
        assertEquals(LocalTime.of(10, 15), TimeEdit.shift(LocalTime.of(10, 0), 15))
        assertEquals(LocalTime.of(9, 45), TimeEdit.shift(LocalTime.of(10, 0), -15))
    }

    @Test
    fun invalidRangesAreRejectedInline() {
        assertTrue(TimeEdit.validate(LocalTime.of(10, 0), LocalTime.of(9, 0)) != null)
        assertTrue(TimeEdit.validate(LocalTime.of(10, 0), LocalTime.of(10, 0)) != null)
        assertNull(TimeEdit.validate(LocalTime.of(9, 0), LocalTime.of(10, 0)))
    }
}
