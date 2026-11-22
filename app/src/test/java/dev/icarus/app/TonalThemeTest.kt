package dev.icarus.app

import dev.icarus.app.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonalThemeTest {

    private fun hex(value: String): Int = ("FF" + value.removePrefix("#")).toLong(16).toInt()

    private data class Case(val app: AppTheme, val state: ThemeState, val bg: String, val accent: String)

    private val expectations = listOf(
        Case(AppTheme.MOSS, ThemeState.FOCUS, "#10140F", "#B7C98A"),
        Case(AppTheme.MOSS, ThemeState.RECOVERY, "#0F1412", "#8FC2AE"),
        Case(AppTheme.MOSS, ThemeState.WIND_DOWN, "#0C100E", "#A8B89A"),
        Case(AppTheme.CLAY, ThemeState.FOCUS, "#161210", "#D89B72"),
        Case(AppTheme.CLAY, ThemeState.RECOVERY, "#141311", "#C9AE82"),
        Case(AppTheme.CLAY, ThemeState.WIND_DOWN, "#100D0C", "#C48F85"),
        Case(AppTheme.DUSK, ThemeState.FOCUS, "#0D1116", "#8FB6D9"),
        Case(AppTheme.DUSK, ThemeState.RECOVERY, "#0C1117", "#9BB8CE"),
        Case(AppTheme.DUSK, ThemeState.WIND_DOWN, "#090C11", "#A7A6C9"),
    )

    @Test
    fun `every theme has the specified ground and accent in every state`() {
        expectations.forEach { case ->
            val t = IcarusTheme.tokens(case.app, case.state)
            assertEquals("bg ${case.app}/${case.state}", hex(case.bg), t.bg)
            assertEquals("accent ${case.app}/${case.state}", hex(case.accent), t.accent)
        }
    }

    @Test
    fun `each row has three surface levels and a soft accent`() {
        AppTheme.entries.forEach { app ->
            ThemeState.entries.forEach { state ->
                val t = IcarusTheme.tokens(app, state)
                assertNotEquals(t.bg, t.surface)
                assertNotEquals(t.surface, t.raised)
                assertNotEquals(0, t.accentSoft)
                assertNotEquals(t.accent, t.accentSoft)
            }
        }
    }

    @Test
    fun `muted text is tinted from the theme, not generic grey`() {
        AppTheme.entries.forEach { app ->
            ThemeState.entries.forEach { state ->
                assertNotEquals(hex("#7B838D"), IcarusTheme.tokens(app, state).muted)
            }
        }
    }

    @Test
    fun `contrast floor holds for all nine rows`() {
        AppTheme.entries.forEach { app ->
            ThemeState.entries.forEach { state ->
                val t = IcarusTheme.tokens(app, state)
                assertTrue("$app/$state", IcarusTheme.contrastRatio(t.accent, t.bg) >= 4.5)
            }
        }
    }
}
