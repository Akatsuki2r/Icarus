package dev.icarus.app

import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class InterventionControllerTest {

    private val block = Block(
        id = "hano",
        title = "Hano development",
        start = LocalTime.of(19, 0),
        end = LocalTime.of(20, 0),
        category = Category.HANO,
    )
    private val moment = Moment.Active(block, Duration.ofMinutes(10), Duration.ofMinutes(50))
    private val instant = Instant.ofEpochMilli(0)
    private val clock = { LocalTime.of(19, 10) to instant }
    private val usage = UsageSignal(
        foreground = "com.tiktok",
        dwellMillis = 10 * 60 * 1000L,
        recentProductive = false,
        classifications = mapOf("com.tiktok" to Classification.DISTRACTION),
    )

    @Test
    fun callKillsOverlayAndStartsGrace() {
        val c = InterventionController(clock)
        c.onTick(LocalTime.of(19, 10), moment, usage)
        assertNotNull(c.pending)

        c.onCall(true)
        assertNull(c.pending)
        c.onCall(false)
        assertFalse(c.shouldIntervene(LocalTime.of(19, 11)))
    }

    @Test
    fun lateFiresOncePerBlock() {
        val c = InterventionController(clock)
        c.onTick(LocalTime.of(19, 10), moment, usage)
        assertEquals(InterventionKind.LATE, c.pending?.kind)
        c.clear()
        c.onTick(LocalTime.of(19, 12), moment, usage)
        assertNull(c.pending)
    }

    @Test
    fun distractionNeedsTenMinutesWithoutProductiveContext() {
        assertEquals(
            10,
            AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, productiveContext = false),
        )
        assertEquals(
            4,
            AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, productiveContext = true),
        )
    }
}
