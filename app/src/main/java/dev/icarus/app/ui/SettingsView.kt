package dev.icarus.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.icarus.app.AppUsageReader
import dev.icarus.app.IcarusTheme
import dev.icarus.app.R
import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.AppTheme
import dev.icarus.app.data.Classification
import dev.icarus.app.data.SettingsStore
import dev.icarus.app.data.ThemeMode

class SettingsView(context: Context) : LinearLayout(context) {

    private val store = SettingsStore(context)
    private val reader = AppUsageReader(context)

    fun render() {
        IcarusTheme.apply(context)
        removeAllViews()
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(IcarusColors.BG)
        setPadding(dp(20), dp(28), dp(20), dp(20))

        addView(TextView(context).apply {
            text = "Settings"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })

        val granted = reader.hasAccess()
        addView(TextView(context).apply {
            text = AwarenessPolicy.messageForAccess(granted)
            setTextColor(if (granted) IcarusColors.GOOD else Color.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(16), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (!granted) {
            addView(action("Open usage access settings") {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            return
        }

        val canOverlay = android.provider.Settings.canDrawOverlays(context)
        addView(TextView(context).apply {
            text = if (canOverlay) "Full-screen interventions: on" else "Full-screen interventions: off — alerts fall back to the shade"
            setTextColor(if (canOverlay) IcarusColors.GOOD else Color.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!canOverlay) {
            addView(action("Open overlay settings") {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }

        addView(TextView(context).apply {
            text = "Appearance"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(16), 0, dp(8))
        })
        val themeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Icarus" to AppTheme.ICARUS, "Tide" to AppTheme.TIDE, "Ember" to AppTheme.EMBER)
            .forEach { (name, value) ->
                themeRow.addView(action(name, store.appTheme() == value) {
                    store.setAppTheme(value)
                    render()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { rightMargin = dp(6) })
            }
        addView(themeRow)

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        listOf("Auto" to ThemeMode.AUTO, "Focus" to ThemeMode.FOCUS, "Recovery" to ThemeMode.RECOVERY, "Wind down" to ThemeMode.WIND_DOWN)
            .forEach { (name, value) ->
                modeRow.addView(action(name, store.themeMode() == value) {
                    store.setThemeMode(value)
                    render()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { rightMargin = dp(6) })
            }
        addView(modeRow)

        addView(TextView(context).apply {
            text = "Alert sounds"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(16), 0, dp(8))
        })
        listOf("prepare", "start", "overdue").forEach { kind ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(context).apply {
                text = kind
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
            listOf("Default" to "system", "Soft" to "prepare", "Clear" to "start", "Firm" to "overdue")
                .forEach { (name, value) ->
                    row.addView(action(name, store.chimeFor(kind) == value) {
                        store.setChime(kind, value)
                        dev.icarus.app.Notifier(context).ensureChannels()
                        render()
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { rightMargin = dp(4) })
                }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        addView(TextView(context).apply {
            text = "App classification"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(16), 0, dp(8))
        })

        val packages = reader.recentPackages()
        if (packages.isEmpty()) {
            addView(TextView(context).apply {
                text = "No recently used apps to classify yet."
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            return
        }
        val classifications = store.classifications()
        packages.forEach { pkg ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(TextView(context).apply {
                text = pkg
                setTextColor(IcarusColors.TEXT_DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            val chips = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            val options = listOf(
                "distraction" to Classification.DISTRACTION,
                "productive" to Classification.PRODUCTIVE,
                "off" to Classification.UNCLASSIFIED,
            )
            options.forEach { (label, kind) ->
                val selected = AwarenessPolicy.classify(classifications, pkg) == kind
                chips.addView(action(label, selected) {
                    store.setClassification(pkg, kind)
                    render()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { rightMargin = dp(6) })
            }
            row.addView(chips)
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun action(text: String, selected: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(if (selected) IcarusColors.BG else IcarusColors.TEXT_DIM)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = RippleDrawable(
                ColorStateList.valueOf(0x24FFFFFF),
                GradientDrawable().apply {
                    setColor(if (selected) IcarusColors.ACCENT else IcarusColors.TRACK)
                    cornerRadius = dp(9).toFloat()
                },
                null,
            )
            setOnClickListener { onClick() }
        }

    private object Color {
        val WARN = android.graphics.Color.parseColor("#E58D6F")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
