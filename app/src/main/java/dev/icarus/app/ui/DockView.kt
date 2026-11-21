package dev.icarus.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.icarus.app.R

/** Persistent four-destination dock. One selected item, 48dp targets, Icarus styling. */
class DockView(context: Context, onSelect: (Destination) -> Unit) : LinearLayout(context) {

    private val items = mutableMapOf<Destination, Pair<ImageView, TextView>>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = RippleDrawable(
            ColorStateList.valueOf(0x00000000),
            GradientDrawable().apply {
                setColor(IcarusColors.CARD)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), IcarusColors.HAIRLINE)
            },
            null,
        )
        setPadding(dp(6), dp(8), dp(6), dp(8))

        val specs = listOf(
            Destination.TODAY to Pair(R.drawable.ic_nav_today, "Today"),
            Destination.ROUTINE to Pair(R.drawable.ic_nav_routine, "Routine"),
            Destination.HISTORY to Pair(R.drawable.ic_nav_history, "History"),
            Destination.SETTINGS to Pair(R.drawable.ic_nav_settings, "Settings"),
        )
        specs.forEach { (destination, spec) ->
            val item = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = true
                background = RippleDrawable(
                    ColorStateList.valueOf(0x24FFFFFF),
                    GradientDrawable().apply { cornerRadius = dp(10).toFloat() },
                    null,
                )
                setOnClickListener {
                    rootView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSelect(destination)
                }
            }
            val icon = ImageView(context).apply {
                setImageResource(spec.first)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val label = TextView(context).apply {
                text = spec.second
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            item.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            item.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            items[destination] = icon to label
        }
    }

    fun select(destination: Destination) {
        items.forEach { (key, pair) ->
            val selected = key == destination
            val color = if (selected) IcarusColors.ACCENT else IcarusColors.MUTED
            pair.first.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            pair.second.setTextColor(color)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
