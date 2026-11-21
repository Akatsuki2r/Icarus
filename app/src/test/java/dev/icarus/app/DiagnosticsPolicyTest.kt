package dev.icarus.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsPolicyTest {

    @Test
    fun `heartbeat describes a healthy service`() {
        assertEquals("running · just now", DiagnosticsPolicy.heartbeat(10_000L, 12_000L))
        assertEquals("running · 45s ago", DiagnosticsPolicy.heartbeat(10_000L, 55_000L))
    }

    @Test
    fun `heartbeat warns when the service stopped ticking`() {
        assertEquals("not reporting", DiagnosticsPolicy.heartbeat(0L, 60_000L))
        assertEquals("stalled · 3m ago", DiagnosticsPolicy.heartbeat(10_000L, 190_000L))
    }

    @Test
    fun `interventions off is honest`() {
        assertEquals("interventions are off", DiagnosticsPolicy.interventionStatus(false, 10))
        assertEquals("observe 10m, then ask", DiagnosticsPolicy.interventionStatus(true, 10))
    }
}
