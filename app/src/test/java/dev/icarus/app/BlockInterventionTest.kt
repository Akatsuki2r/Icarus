package dev.icarus.app

import dev.icarus.app.data.Classification
import dev.icarus.app.data.RoutineJson
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class BlockInterventionTest {

    private fun block(enabled: Boolean = true, dwell: Long = 0, productive: Boolean = false) = Block(
        id = "work",
        title = "Work",
        start = LocalTime.of(10, 0),
        end = LocalTime.of(11, 0),
        category = Category.HANO,
        interventionEnabled = enabled,
        dwellLimitMinutes = dwell,
        productiveContext = productive,
    )

    /** Fire the one late nudge first, then test the scroll decision after its cooldown. */
    private fun afterLate(
        enabled: Boolean,
        dwellLimit: Long,
        productive: Boolean,
        dwellMinutes: Long,
    ): InterventionRequest? {
        var now = Instant.ofEpochMilli(0)
        val controller = InterventionController { LocalTime.of(10, 2) to now }
        val b = block(enabled, dwellLimit, productive)
        val moment = Moment.Active(b, Duration.ofMinutes(2), Duration.ofMinutes(58))
        val signal = UsageSignal(
            foreground = "com.tiktok",
            dwellMillis = dwellMinutes * 60_000L,
            recentProductive = false,
            classifications = mapOf("com.tiktok" to Classification.DISTRACTION),
            interventionsEnabled = enabled,
            dwellLimitMinutes = dwellLimit.takeIf { it > 0 },
            productiveContext = productive,
        )
        controller.onTick(LocalTime.of(10, 2), moment, signal)
        controller.clear()
        now = Instant.ofEpochMilli(11 * 60_000L)
        controller.onTick(LocalTime.of(10, 10), moment, signal)
        return controller.pending
    }

    @Test
    fun interventionSettingsSurviveJsonRoundTrip() {
        val original = block(enabled = false, dwell = 20, productive = true)
        val parsed = RoutineJson.blockFromJson(RoutineJson.blockToJson(original))
        assertFalse(parsed.interventionEnabled)
        assertEquals(20L, parsed.dwellLimitMinutes)
        assertTrue(parsed.productiveContext)
    }

    @Test
    fun disabledBlockNeverIntervenes() {
        assertNull(afterLate(enabled = false, dwellLimit = 0, productive = false, dwellMinutes = 10))
    }

    @Test
    fun customDwellLimitOverridesGlobalPolicy() {
        // Ten minutes in a distraction app: global policy (10) would fire,
        // but this block asks for twenty.
        assertNull(afterLate(enabled = true, dwellLimit = 20, productive = false, dwellMinutes = 10))
        assertNotNull(afterLate(enabled = true, dwellLimit = 5, productive = false, dwellMinutes = 10))
    }

    @Test
    fun productiveBlockShortensTheGlobalThreshold() {
        assertNull(afterLate(enabled = true, dwellLimit = 0, productive = false, dwellMinutes = 4))
        assertNotNull(afterLate(enabled = true, dwellLimit = 0, productive = true, dwellMinutes = 4))
    }
}
