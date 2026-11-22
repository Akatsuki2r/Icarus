package dev.icarus.app

import android.content.Context
import dev.icarus.app.data.DayStateStore
import dev.icarus.app.data.SettingsStore
import dev.icarus.app.data.ThemeMode
import dev.icarus.engine.Moment
import java.time.LocalDate

enum class ThemeState { FOCUS, RECOVERY, WIND_DOWN }

data class Tokens(
    val bg: Int,
    val surface: Int,
    val raised: Int,
    val outline: Int,
    val track: Int,
    val text: Int,
    val textDim: Int,
    val muted: Int,
    val accent: Int,
    val accentSoft: Int,
    val good: Int,
    val warn: Int,
)

object IcarusTheme {

    var overlayActive: Boolean = false

    /** Incremented whenever the active tokens change, even if another view consumed the change. */
    var generation: Int = 0
        private set

    private val TABLE: Map<dev.icarus.app.data.AppTheme, Map<ThemeState, Tokens>> = mapOf(
        dev.icarus.app.data.AppTheme.MOSS to mapOf(
            ThemeState.FOCUS to Tokens(
                0xFF10140F.toInt(), 0xFF171C15.toInt(), 0xFF1F261C.toInt(), 0xFF2E3828.toInt(),
                0xFF262E22.toInt(), 0xFFEDF0E6.toInt(), 0xFFC3CBB8.toInt(), 0xFF94A089.toInt(),
                0xFFB7C98A.toInt(), 0xFF6E7F52.toInt(), 0xFF9BC98A.toInt(), 0xFFD9A07A.toInt()),
            ThemeState.RECOVERY to Tokens(
                0xFF0F1412.toInt(), 0xFF161D1A.toInt(), 0xFF1E2722.toInt(), 0xFF283A31.toInt(),
                0xFF223029.toInt(), 0xFFEAF0E9.toInt(), 0xFFBECBC0.toInt(), 0xFF8FA396.toInt(),
                0xFF8FC2AE.toInt(), 0xFF4F7E6A.toInt(), 0xFF9BC98A.toInt(), 0xFFD9A07A.toInt()),
            ThemeState.WIND_DOWN to Tokens(
                0xFF0C100E.toInt(), 0xFF131815.toInt(), 0xFF1A211C.toInt(), 0xFF242E27.toInt(),
                0xFF1E2620.toInt(), 0xFFE6E9DE.toInt(), 0xFFB9C1B0.toInt(), 0xFF8B957F.toInt(),
                0xFFA8B89A.toInt(), 0xFF616E57.toInt(), 0xFF9BC98A.toInt(), 0xFFD9A07A.toInt()),
        ),
        dev.icarus.app.data.AppTheme.CLAY to mapOf(
            ThemeState.FOCUS to Tokens(
                0xFF161210.toInt(), 0xFF201A16.toInt(), 0xFF2A211B.toInt(), 0xFF3A2C24.toInt(),
                0xFF31261F.toInt(), 0xFFF5EDE4.toInt(), 0xFFDCC9BA.toInt(), 0xFFB39C8B.toInt(),
                0xFFD89B72.toInt(), 0xFF8A5E46.toInt(), 0xFF9DBE8E.toInt(), 0xFFE0A384.toInt()),
            ThemeState.RECOVERY to Tokens(
                0xFF141311.toInt(), 0xFF1D1B17.toInt(), 0xFF26231D.toInt(), 0xFF363027.toInt(),
                0xFF2D2921.toInt(), 0xFFF2EDE2.toInt(), 0xFFD8CCB8.toInt(), 0xFFAC9C82.toInt(),
                0xFFC9AE82.toInt(), 0xFF7E6B4C.toInt(), 0xFF9DBE8E.toInt(), 0xFFE0A384.toInt()),
            ThemeState.WIND_DOWN to Tokens(
                0xFF100D0C.toInt(), 0xFF191513.toInt(), 0xFF211B18.toInt(), 0xFF2E2420.toInt(),
                0xFF271E1A.toInt(), 0xFFEEE4DE.toInt(), 0xFFD2BFB4.toInt(), 0xFFA38A7C.toInt(),
                0xFFC48F85.toInt(), 0xFF78554F.toInt(), 0xFF9DBE8E.toInt(), 0xFFE0A384.toInt()),
        ),
        dev.icarus.app.data.AppTheme.DUSK to mapOf(
            ThemeState.FOCUS to Tokens(
                0xFF0D1116.toInt(), 0xFF141A21.toInt(), 0xFF1C2530.toInt(), 0xFF283341.toInt(),
                0xFF222B37.toInt(), 0xFFE8EEF5.toInt(), 0xFFC2CFDC.toInt(), 0xFF90A1B2.toInt(),
                0xFF8FB6D9.toInt(), 0xFF4E7398.toInt(), 0xFF8FC7B0.toInt(), 0xFFD9A383.toInt()),
            ThemeState.RECOVERY to Tokens(
                0xFF0C1117.toInt(), 0xFF131B24.toInt(), 0xFF1B2530.toInt(), 0xFF273644.toInt(),
                0xFF202C38.toInt(), 0xFFE5ECF4.toInt(), 0xFFBDCAD8.toInt(), 0xFF8A9DAE.toInt(),
                0xFF9BB8CE.toInt(), 0xFF566F86.toInt(), 0xFF8FC7B0.toInt(), 0xFFD9A383.toInt()),
            ThemeState.WIND_DOWN to Tokens(
                0xFF090C11.toInt(), 0xFF111720.toInt(), 0xFF18202B.toInt(), 0xFF212C3A.toInt(),
                0xFF1B2430.toInt(), 0xFFE2E8F0.toInt(), 0xFFB6C3D1.toInt(), 0xFF8496A8.toInt(),
                0xFFA7A6C9.toInt(), 0xFF64638C.toInt(), 0xFF8FC7B0.toInt(), 0xFFD9A383.toInt()),
        ),
    )

    fun tokens(app: dev.icarus.app.data.AppTheme, state: ThemeState): Tokens =
        TABLE[app]?.get(state) ?: TABLE.getValue(dev.icarus.app.data.AppTheme.MOSS).getValue(ThemeState.FOCUS)

    fun resolveFrom(
        app: dev.icarus.app.data.AppTheme,
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
        if (changed) {
            dev.icarus.app.ui.IcarusColors.update(next)
            generation++
        }
        return changed
    }

    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double =
        0.2126 * channel(color shr 16) + 0.7152 * channel(color shr 8) + 0.0722 * channel(color)

    private fun channel(value: Int): Double {
        val c = (value and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
