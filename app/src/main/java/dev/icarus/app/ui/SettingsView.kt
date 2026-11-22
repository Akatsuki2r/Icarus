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
import dev.icarus.app.DiagnosticsPolicy
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

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }

        body.addView(TextView(context).apply {
            text = "Settings"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })

        body.addView(section("Timekeeper"))
        body.addView(TextView(context).apply {
            text = DiagnosticsPolicy.heartbeat(store.lastTick, System.currentTimeMillis())
            setTextColor(if (store.lastTick > 0L) IcarusColors.GOOD else IcarusColors.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        })
        body.addView(action("Send test alert") {
            dev.icarus.app.Notifier(context).ensureChannels()
            dev.icarus.app.Notifier(context).testAlert()
            android.widget.Toast.makeText(context, "Check the shade.", android.widget.Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        body.addView(section("Notifications"))
        val notificationsOn = android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        body.addView(TextView(context).apply {
            text = if (notificationsOn) "Notifications: allowed" else "Notifications: blocked — alerts are silent"
            setTextColor(if (notificationsOn) IcarusColors.GOOD else IcarusColors.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(6))
        })
        if (!notificationsOn && android.os.Build.VERSION.SDK_INT >= 33) {
            body.addView(action("Allow notifications") {
                (context as? android.app.Activity)?.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42,
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        val canAlarm = dev.icarus.app.Alarms.canScheduleExact(context)
        body.addView(TextView(context).apply {
            text = if (canAlarm) "Exact alarms: allowed" else "Exact alarms: off — wake-ups may be delayed"
            setTextColor(if (canAlarm) IcarusColors.GOOD else IcarusColors.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(6))
        })
        if (!canAlarm && android.os.Build.VERSION.SDK_INT >= 31) {
            body.addView(action("Allow exact alarms") {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }

        body.addView(section("Interventions"))
        body.addView(TextView(context).apply {
            text = DiagnosticsPolicy.interventionStatus(store.interventionsEnabled(), 10)
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        })
        val interventionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("on" to true, "off" to false).forEach { (name, value) ->
            interventionRow.addView(action(name, store.interventionsEnabled() == value) {
                store.setInterventionsEnabled(value)
                render()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { rightMargin = dp(8) })
        }
        body.addView(interventionRow)

        val granted = reader.hasAccess()
        body.addView(TextView(context).apply {
            text = AwarenessPolicy.messageForAccess(granted)
            setTextColor(if (granted) IcarusColors.GOOD else IcarusColors.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(16), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (!granted) {
            body.addView(action("Open usage access settings") {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }

        val canOverlay = android.provider.Settings.canDrawOverlays(context)
        body.addView(TextView(context).apply {
            text = if (canOverlay) "Full-screen interventions: on" else "Full-screen interventions: off — alerts fall back to the shade"
            setTextColor(if (canOverlay) IcarusColors.GOOD else IcarusColors.WARN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(4))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(TextView(context).apply {
            text = "Calls suppress interventions. Cellular calls are detected reliably; WhatsApp, " +
                "Snap and other VoIP calls are best-effort because Android does not expose them."
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dp(10))
        })
        if (!canOverlay) {
            body.addView(action("Open overlay settings") {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }

        body.addView(TextView(context).apply {
            text = "Appearance"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(16), 0, dp(8))
        })
        val themeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Moss" to AppTheme.MOSS, "Clay" to AppTheme.CLAY, "Dusk" to AppTheme.DUSK)
            .forEach { (name, value) ->
                themeRow.addView(action(name, store.appTheme() == value) {
                    store.setAppTheme(value)
                    render()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { rightMargin = dp(8) })
            }
        body.addView(themeRow)

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        listOf("Auto" to ThemeMode.AUTO, "Focus" to ThemeMode.FOCUS, "Recovery" to ThemeMode.RECOVERY, "Wind down" to ThemeMode.WIND_DOWN)
            .forEach { (name, value) ->
                modeRow.addView(action(name, store.themeMode() == value) {
                    store.setThemeMode(value)
                    render()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { rightMargin = dp(8) })
            }
        body.addView(modeRow)

        body.addView(TextView(context).apply {
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
                        dev.icarus.app.Notifier(context).refreshSoundChannels()
                        render()
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { rightMargin = dp(8) })
                }
            body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (granted) {
            body.addView(TextView(context).apply {
                text = "App classification"
                setTextColor(IcarusColors.TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(16), 0, dp(8))
            })

            val packages = reader.recentPackages()
            if (packages.isEmpty()) {
                body.addView(TextView(context).apply {
                    text = "No recently used apps to classify yet."
                    setTextColor(IcarusColors.MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                })
            } else {
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
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { rightMargin = dp(8) })
                    }
                    row.addView(chips)
                    body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
        }

        addView(android.widget.ScrollView(context).apply { addView(body) })
    }

    private fun section(title: String): TextView = TextView(context).apply {
        text = title
        setTextColor(IcarusColors.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(18), 0, dp(8))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
