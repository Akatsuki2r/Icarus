package dev.icarus.app

import android.content.Context
import dev.icarus.app.data.AppTheme
import dev.icarus.app.data.DayStateStore
import dev.icarus.app.data.SettingsStore
import dev.icarus.app.data.ThemeMode
import dev.icarus.engine.Moment
import java.time.LocalDate

enum class ThemeState { FOCUS, RECOVERY, WIND_DOWN }

data class Tokens(
    val bg: Int,
    val surface: Int,
    val outline: Int,
    val track: Int,
    val text: Int,
    val textDim: Int,
    val muted: Int,
    val accent: Int,
    val good: Int,
    val warn: Int,
)

object IcarusTheme {

    /** Set by the overlay gateway while an intervention is visible. */
    var overlayActive: Boolean = false

    private val TABLE: Map<AppTheme, Map<ThemeState, Tokens>> = mapOf(
        AppTheme.ICARUS to mapOf(
            ThemeState.FOCUS to Tokens(0xFF0B0C0E.toInt(), 0xFF15181B.toInt(), 0xFF242A30.toInt(), 0xFF2C333A.toInt(), 0xFFF2F3F5.toInt(), 0xFFC7CCD2.toInt(), 0xFF7B838D.toInt(), 0xFFF2B84B.toInt(), 0xFF6FCF8A.toInt(), 0xFFE58D6F.toInt()),
            ThemeState.RECOVERY to Tokens(0xFF0E1114.toInt(), 0xFF181D21.toInt(), 0xFF273039.toInt(), 0xFF303B44.toInt(), 0xFFEFF2F4.toInt(), 0xFFBFC8CF.toInt(), 0xFF84919B.toInt(), 0xFF9BA6B2.toInt(), 0xFF6FCF8A.toInt(), 0xFFE58D6F.toInt()),
            ThemeState.WIND_DOWN to Tokens(0xFF08090B.toInt(), 0xFF111316.toInt(), 0xFF20242A.toInt(), 0xFF292E35.toInt(), 0xFFE8E4DE.toInt(), 0xFFBDb6AC.toInt(), 0xFF7E7A74.toInt(), 0xFFC9A86A.toInt(), 0xFF6FCF8A.toInt(), 0xFFE58D6F.toInt()),
        ),
        AppTheme.TIDE to mapOf(
            ThemeState.FOCUS to Tokens(0xFF081418.toInt(), 0xFF102226.toInt(), 0xFF1D3A42.toInt(), 0xFF1A333A.toInt(), 0xFFE8F6F4.toInt(), 0xFFB9D3D0.toInt(), 0xFF7FA3A0.toInt(), 0xFF5FD4C4.toInt(), 0xFF6FCF9A.toInt(), 0xFFE59A7A.toInt()),
            ThemeState.RECOVERY to Tokens(0xFF0A1220.toInt(), 0xFF121E32.toInt(), 0xFF1E3450.toInt(), 0xFF1B2E45.toInt(), 0xFFE8F0FA.toInt(), 0xFFB6C9DE.toInt(), 0xFF7E9BB8.toInt(), 0xFF8FB8FF.toInt(), 0xFF6FCF9A.toInt(), 0xFFE59A7A.toInt()),
            ThemeState.WIND_DOWN to Tokens(0xFF0E0B1C.toInt(), 0xFF19142E.toInt(), 0xFF2B2347.toInt(), 0xFF261F3E.toInt(), 0xFFF0EAFA.toInt(), 0xFFC6B8DE.toInt(), 0xFF8D7FA8.toInt(), 0xFFB39AE8.toInt(), 0xFF6FCF9A.toInt(), 0xFFE59A7A.toInt()),
        ),
        AppTheme.EMBER to mapOf(
            ThemeState.FOCUS to Tokens(0xFF140D10.toInt(), 0xFF24161C.toInt(), 0xFF3B242D.toInt(), 0xFF36212A.toInt(), 0xFFFBECEC.toInt(), 0xFFDCC2C6.toInt(), 0xFFA9828C.toInt(), 0xFFFF7A66.toInt(), 0xFF86C99A.toInt(), 0xFFF0A07E.toInt()),
            ThemeState.RECOVERY to Tokens(0xFF14100C.toInt(), 0xFF241D16.toInt(), 0xFF3B2E22.toInt(), 0xFF362A20.toInt(), 0xFFFAF0E4.toInt(), 0xFFDEC9AF.toInt(), 0xFFA88D71.toInt(), 0xFFE8B86D.toInt(), 0xFF86C99A.toInt(), 0xFFF0A07E.toInt()),
            ThemeState.WIND_DOWN to Tokens(0xFF100A0F.toInt(), 0xFF1F131C.toInt(), 0xFF35212F.toInt(), 0xFF2F1D2A.toInt(), 0xFFF6E9F0.toInt(), 0xFFD9BFCB.toInt(), 0xFFA37E8E.toInt(), 0xFFC98BA0.toInt(), 0xFF86C99A.toInt(), 0xFFF0A07E.toInt()),
        ),
    )

    fun tokens(app: AppTheme, state: ThemeState): Tokens =
        TABLE.getValue(app).getValue(state)

    fun resolveFrom(
        app: AppTheme,
        mode: ThemeMode,
        moment: Moment,
        hasPlan: Boolean,
        overlayActive: Boolean,
    ): Tokens {
        val state = if (mode != ThemeMode.AUTO) {
            when (mode) {
                ThemeMode.FOCUS -> ThemeState.FOCUS
                ThemeMode.RECOVERY -> ThemeState.RECOVERY
                ThemeMode.WIND_DOWN -> ThemeState.WIND_DOWN
                ThemeMode.AUTO -> ThemeState.FOCUS
            }
        } else when {
            overlayActive -> ThemeState.FOCUS
            hasPlan -> ThemeState.RECOVERY
            moment is Moment.Finished -> ThemeState.WIND_DOWN
            else -> ThemeState.FOCUS
        }
        return tokens(app, state)
    }

    fun resolve(context: Context): Tokens {
        val settings = SettingsStore(context)
        val today = LocalDate.now()
        val timeline = Actions.timeline(context, today)
        val hasPlan = DayStateStore(context).load(today).plan != null
        return resolveFrom(
            settings.appTheme(),
            settings.themeMode(),
            timeline.moment(java.time.LocalTime.now()),
            hasPlan,
            overlayActive,
        )
    }

    fun apply(context: Context): Boolean {
        val next = resolve(context)
        val changed = next != dev.icarus.app.ui.IcarusColors.current()
        if (changed) dev.icarus.app.ui.IcarusColors.update(next)
        return changed
    }

    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Int): Double {
        val r = channel(color shr 16)
        val g = channel(color shr 8)
        val b = channel(color)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val c = (value and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
