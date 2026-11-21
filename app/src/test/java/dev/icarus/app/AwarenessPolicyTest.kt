package dev.icarus.app

import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwarenessPolicyTest {

    @Test
    fun productiveContextChangesPolicy() {
        assertTrue(
            AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, productiveContext = true) <
                AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, productiveContext = false),
        )
    }

    @Test
    fun unclassifiedAndProductiveNeverIntervene() {
        assertEquals(Int.MAX_VALUE, AwarenessPolicy.thresholdMinutes(Classification.UNCLASSIFIED, false))
        assertEquals(Int.MAX_VALUE, AwarenessPolicy.thresholdMinutes(Classification.PRODUCTIVE, true))
    }

    @Test
    fun distractionThresholds() {
        assertEquals(10, AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, false))
        assertEquals(4, AwarenessPolicy.thresholdMinutes(Classification.DISTRACTION, true))
    }
}
