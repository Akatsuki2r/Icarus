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
import dev.icarus.app.R
import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import dev.icarus.app.data.SettingsStore

class SettingsView(context: Context) : LinearLayout(context) {

    private val store = SettingsStore(context)
    private val reader = AppUsageReader(context)

    fun render() {
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
