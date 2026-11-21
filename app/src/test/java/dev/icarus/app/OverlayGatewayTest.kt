package dev.icarus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGatewayTest {

    private class FakeSurface(var canShow: Boolean = true) : OverlaySurface {
        val calls = mutableListOf<String>()
        override fun canShow() = canShow
        override fun show(request: InterventionRequest, title: String) {
            calls += "show:${request.kind.name}:$title"
        }
        override fun dismiss() { calls += "dismiss" }
        override fun fallback(request: InterventionRequest) { calls += "fallback:${request.kind.name}" }
    }

    private val late = InterventionRequest(InterventionKind.LATE, "hano")

    @Test
    fun deniedUsesFallback() {
        val surface = FakeSurface(canShow = false)
        OverlayGateway(surface).show(late)
        assertEquals(listOf("fallback:LATE"), surface.calls)
    }

    @Test
    fun addFailureFallsBack() {
        val surface = object : OverlaySurface {
            override fun canShow() = true
            override fun show(request: InterventionRequest, title: String) = throw IllegalStateException("window revoked")
            override fun dismiss() {}
            override fun fallback(request: InterventionRequest) { throw AssertionError("fallback expected") }
        }
        // The gateway catches a failed surface and routes it to fallback.
        val calls = mutableListOf<String>()
        val safe = object : OverlaySurface by surface {
            override fun fallback(request: InterventionRequest) { calls += "fallback" }
        }
        try {
            OverlayGateway(safe).show(late)
        } catch (_: IllegalStateException) {
            calls += "threw"
        }
        assertFalse(calls.contains("threw"))
        assertTrue(calls.contains("fallback"))
    }

    @Test
    fun callDismissRemovesWindow() {
        val surface = FakeSurface()
        val gateway = OverlayGateway(surface)
        gateway.show(late)
        gateway.onCall(true)
        assertEquals(listOf("show:LATE:", "dismiss"), surface.calls)
        assertFalse(gateway.hasVisibleOverlay())
    }

    @Test
    fun snoozeAndExitBothDismiss() {
        val surface = FakeSurface()
        val gateway = OverlayGateway(surface)
        gateway.show(late)
        gateway.snooze()
        assertTrue(surface.calls.contains("dismiss"))
        surface.calls.clear()
        gateway.show(InterventionRequest(InterventionKind.STILL_SCROLLING, "hano"))
        gateway.hardwareExit()
        assertTrue(surface.calls.contains("dismiss"))
    }
}
