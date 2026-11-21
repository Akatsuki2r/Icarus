package dev.icarus.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.icarus.app.R
import dev.icarus.app.WeekEditorActivity

class RoutineHomeView(context: Context) : LinearLayout(context) {

    fun render() {
        removeAllViews()
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(IcarusColors.BG)
        setPadding(dp(20), dp(28), dp(20), dp(20))
        addView(TextView(context).apply {
            text = "Routine"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })
        addView(TextView(context).apply {
            text = "Your weekly schedule. Edit times, priorities and how Icarus intervenes."
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(12), 0, dp(20))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            text = "Rules"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(20), 0, dp(8))
        })
        addView(TextView(context).apply {
            text = "Anchors never move. If a FIRM block is missed, flexible time absorbs it. " +
                "Interventions wait 1 minute after a late start, then ask once per block; " +
                "calls suppress them. Per-block limits live in the editor."
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(IcarusColors.CARD)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), IcarusColors.HAIRLINE)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = "Open weekly editor"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(IcarusColors.BG)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(0x22000000),
                GradientDrawable().apply {
                    setColor(IcarusColors.ACCENT)
                    cornerRadius = dp(12).toFloat()
                },
                null,
            )
            setOnClickListener { context.startActivity(Intent(context, WeekEditorActivity::class.java)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
