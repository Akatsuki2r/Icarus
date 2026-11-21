package dev.icarus.app.data

import android.content.Context
import org.json.JSONObject

enum class Classification { UNCLASSIFIED, DISTRACTION, PRODUCTIVE }

enum class AppTheme { ICARUS, TIDE, EMBER }

enum class ThemeMode { AUTO, FOCUS, RECOVERY, WIND_DOWN }

object SettingsDefaults {
    val theme = AppTheme.ICARUS
    val mode = ThemeMode.AUTO
    const val chime = "system"
}

/** Pure, testable rules for turning app classification into a policy decision. */
object AwarenessPolicy {
    fun classify(map: Map<String, Classification>, pkg: String): Classification =
        map[pkg] ?: Classification.UNCLASSIFIED

    fun messageForAccess(granted: Boolean): String = if (granted) {
        "Usage access is on. Icarus can see which app is in front."
    } else {
        "Usage access is off. Icarus cannot detect app context until you enable it."
    }

    /** Minutes of dwell before intervention. MAX_VALUE means never. */
    fun thresholdMinutes(kind: Classification, productiveContext: Boolean): Int = when (kind) {
        Classification.UNCLASSIFIED, Classification.PRODUCTIVE -> Int.MAX_VALUE
        Classification.DISTRACTION -> if (productiveContext) 4 else 10
    }
}

/** Local settings: app classification, theme choice and chime selection. */
class SettingsStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("icarus.settings", Context.MODE_PRIVATE)

    fun classifications(): Map<String, Classification> {
        val raw = prefs.getString(KEY_CLASSIFICATIONS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associate { key ->
                key to runCatching { Classification.valueOf(json.getString(key)) }
                    .getOrDefault(Classification.UNCLASSIFIED)
            }
        }.getOrDefault(emptyMap())
    }

    fun setClassification(pkg: String, kind: Classification) {
        val updated = classifications() + (pkg to kind)
        val json = JSONObject()
        updated.forEach { (key, value) -> json.put(key, value.name) }
        prefs.edit().putString(KEY_CLASSIFICATIONS, json.toString()).apply()
    }

    fun appTheme(): AppTheme = runCatching {
        prefs.getString(KEY_THEME, null)?.let { AppTheme.valueOf(it) }
    }.getOrNull() ?: SettingsDefaults.theme

    fun setAppTheme(theme: AppTheme) = prefs.edit().putString(KEY_THEME, theme.name).apply()

    fun themeMode(): ThemeMode = runCatching {
        prefs.getString(KEY_MODE, null)?.let { ThemeMode.valueOf(it) }
    }.getOrNull() ?: SettingsDefaults.mode

    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_MODE, mode.name).apply()

    fun chimeFor(kind: String): String = prefs.getString("$KEY_CHIME.$kind", SettingsDefaults.chime)
        ?: SettingsDefaults.chime

    fun setChime(kind: String, value: String) = prefs.edit().putString("$KEY_CHIME.$kind", value).apply()

    private companion object {
        const val KEY_CLASSIFICATIONS = "classifications"
        const val KEY_THEME = "theme"
        const val KEY_MODE = "theme_mode"
        const val KEY_CHIME = "chime"
    }
}
