package dev.icarus.app

import dev.icarus.app.data.AppTheme
import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import dev.icarus.app.data.SettingsDefaults
import dev.icarus.app.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun unclassifiedByDefault() {
        assertEquals(Classification.UNCLASSIFIED, AwarenessPolicy.classify(emptyMap(), "com.tiktok"))
    }

    @Test
    fun hasUsageAccessFalseIsReported() {
        assertTrue(AwarenessPolicy.messageForAccess(false).contains("Usage access is off"))
    }

    @Test
    fun defaultsAreSafe() {
        assertEquals(AppTheme.MOSS, SettingsDefaults.theme)
        assertEquals(ThemeMode.AUTO, SettingsDefaults.mode)
    }
}
