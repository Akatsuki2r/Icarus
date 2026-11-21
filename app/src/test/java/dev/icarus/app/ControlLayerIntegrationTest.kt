package dev.icarus.app

import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Moment
import dev.icarus.engine.Routine
import dev.icarus.engine.withBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class ControlLayerIntegrationTest {

    private class Surface : OverlaySurface {
        val calls = mutableListOf<String>()
        override fun canShow() = true
        override fun show(kind: InterventionKind, title: String) { calls += "show:$kind:$title" }
        override fun dismiss() { calls += "dismiss" }
        override fun fallback(request: InterventionRequest) { calls += "fallback" }
    }

    @Test
    fun `routine edit to intervention to snooze is one flow`() {
        val hano = Block("hano", "Hano development", LocalTime.of(19, 0), LocalTime.of(20, 0), Category.HANO)
        val trading = Block("trade", "Trading practice", LocalTime.of(20, 0), LocalTime.of(21, 0), Category.TRADING)
        val routine = Routine(mapOf(java.time.DayOfWeek.FRIDAY to listOf(hano)))
        val edited = routine.withBlock(java.time.DayOfWeek.FRIDAY, trading)
        assertTrue(edited.problems().isEmpty())

        val classifications = mapOf("com.tiktok" to Classification.DISTRACTION)
        assertEquals(
            10,
            AwarenessPolicy.thresholdMinutes(
                AwarenessPolicy.classify(classifications, "com.tiktok"),
                productiveContext = false,
            ),
        )

        val instant = Instant.ofEpochMilli(0)
        val controller = InterventionController({ LocalTime.of(19, 10) to instant })
        val signal = UsageSignal(
            foreground = "com.tiktok",
            dwellMillis = 10 * 60 * 1000L,
            recentProductive = false,
            classifications = classifications,
        )
        controller.onTick(
            LocalTime.of(19, 10),
            Moment.Active(hano, Duration.ofMinutes(10), Duration.ofMinutes(50)),
            signal,
        )
        val request = controller.pending
        org.junit.Assert.assertNotNull(request)
        request!!
        assertEquals(InterventionKind.LATE, request.kind)

        val surface = Surface()
        val gateway = OverlayGateway(surface) { "Hano development" }
        gateway.onSnooze = { controller.snooze() }
        gateway.show(request)
        gateway.snooze()

        assertFalse(gateway.hasVisibleOverlay())
        assertFalse(controller.shouldIntervene(LocalTime.of(19, 11)))
        assertTrue(surface.calls.contains("dismiss"))
    }
}
