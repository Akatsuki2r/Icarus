package dev.icarus.app

import dev.icarus.app.data.AppTheme
import dev.icarus.app.data.ThemeMode
import dev.icarus.engine.Moment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class IcarusThemeTest {

    @Test
    fun `state resolves from plan and finish with manual override`() {
        assertEquals(
            IcarusTheme.tokens(AppTheme.CLAY, ThemeState.RECOVERY),
            IcarusTheme.resolveFrom(
                AppTheme.CLAY, ThemeMode.AUTO,
                Moment.Waiting(null, Duration.ZERO), true, false,
            ),
        )
        assertEquals(
            IcarusTheme.tokens(AppTheme.DUSK, ThemeState.WIND_DOWN),
            IcarusTheme.resolveFrom(
                AppTheme.DUSK, ThemeMode.AUTO, Moment.Finished, false, false,
            ),
        )
        assertEquals(
            IcarusTheme.tokens(AppTheme.MOSS, ThemeState.FOCUS),
            IcarusTheme.resolveFrom(
                AppTheme.MOSS, ThemeMode.FOCUS, Moment.Finished, false, false,
            ),
        )
    }

    @Test
    fun `themes have distinct identities`() {
        val moss = IcarusTheme.tokens(AppTheme.MOSS, ThemeState.FOCUS)
        val clay = IcarusTheme.tokens(AppTheme.CLAY, ThemeState.FOCUS)
        val dusk = IcarusTheme.tokens(AppTheme.DUSK, ThemeState.FOCUS)
        org.junit.Assert.assertNotEquals(moss.bg, clay.bg)
        org.junit.Assert.assertNotEquals(clay.bg, dusk.bg)
        org.junit.Assert.assertNotEquals(moss.accent, dusk.accent)
    }
}
