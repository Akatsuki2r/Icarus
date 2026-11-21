package dev.icarus.app

import dev.icarus.app.data.AppTheme
import dev.icarus.app.data.ThemeMode
import dev.icarus.engine.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcarusThemeTest {

    @Test
    fun `all nine token rows have the specified accent and ground`() {
        val expected = mapOf(
            AppTheme.ICARUS to listOf(0xFFF2B84B.toInt(), 0xFF9BA6B2.toInt(), 0xFFC9A86A.toInt()),
            AppTheme.TIDE to listOf(0xFF5FD4C4.toInt(), 0xFF8FB8FF.toInt(), 0xFFB39AE8.toInt()),
            AppTheme.EMBER to listOf(0xFFFF7A66.toInt(), 0xFFE8B86D.toInt(), 0xFFC98BA0.toInt()),
        )
        val states = listOf(ThemeState.FOCUS, ThemeState.RECOVERY, ThemeState.WIND_DOWN)
        expected.forEach { (app, accents) ->
            states.forEachIndexed { index, state ->
                assertEquals(accents[index], IcarusTheme.tokens(app, state).accent)
            }
        }
        assertEquals(0xFF0B0C0E.toInt(), IcarusTheme.tokens(AppTheme.ICARUS, ThemeState.FOCUS).bg)
        assertEquals(0xFF0A1220.toInt(), IcarusTheme.tokens(AppTheme.TIDE, ThemeState.RECOVERY).bg)
        assertEquals(0xFF100A0F.toInt(), IcarusTheme.tokens(AppTheme.EMBER, ThemeState.WIND_DOWN).bg)
    }

    @Test
    fun `resolve picks state from plan and finish with manual override`() {
        assertEquals(
            IcarusTheme.tokens(AppTheme.TIDE, ThemeState.RECOVERY),
            IcarusTheme.resolveFrom(AppTheme.TIDE, ThemeMode.AUTO, Moment.Waiting(null, java.time.Duration.ZERO), true, false),
        )
        assertEquals(
            IcarusTheme.tokens(AppTheme.EMBER, ThemeState.WIND_DOWN),
            IcarusTheme.resolveFrom(AppTheme.EMBER, ThemeMode.AUTO, Moment.Finished, false, false),
        )
        assertEquals(
            IcarusTheme.tokens(AppTheme.ICARUS, ThemeState.RECOVERY),
            IcarusTheme.resolveFrom(AppTheme.ICARUS, ThemeMode.RECOVERY, Moment.Finished, false, false),
        )
    }

    @Test
    fun `contrastFloor holds for every app theme and state`() {
        AppTheme.entries.forEach { app ->
            ThemeState.entries.forEach { state ->
                val t = IcarusTheme.tokens(app, state)
                assertTrue(
                    "$app/$state accent contrast ${IcarusTheme.contrastRatio(t.accent, t.bg)}",
                    IcarusTheme.contrastRatio(t.accent, t.bg) >= 4.5,
                )
            }
        }
    }
}
