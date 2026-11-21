package dev.icarus.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.icarus.app.InterventionKind

/** Full-screen, calm, always escapable. The draining line is the only motion. */
class InterventionOverlayView(
    context: Context,
    kind: InterventionKind,
    title: String,
    private val onSnooze: () -> Unit,
    private val onAcknowledge: () -> Unit,
    private val onExit: () -> Unit,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        setBackgroundColor(IcarusColors.BG)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(28), dp(64), dp(28), dp(32))
        setOnLongClickListener { onExit(); true }
        setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            ) {
                onExit()
                true
            } else {
                false
            }
        }

        addView(TextView(context).apply {
            text = title.ifBlank { "Current block" }
            setTextColor(IcarusColors.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        addView(TextView(context).apply {
            text = if (kind == InterventionKind.LATE) "Started one minute ago." else "This block is running."
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(12), 0, dp(10))
        })
        addView(TextView(context).apply {
            text = if (kind == InterventionKind.LATE) {
                "What would you like to do?"
            } else {
                "You're still on your phone. Decide when you're ready."
            }
            setTextColor(IcarusColors.TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })

        val spacer = View(context).apply { layoutParams = LayoutParams(1, 0, 1f) }
        addView(spacer)

        addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(IcarusColors.TRACK)
                cornerRadius = dp(2).toFloat()
            }
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        val seconds = if (kind == InterventionKind.LATE) 10 else 8
        addView(TextView(context).apply {
            text = "closes in ${seconds}s"
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(6), 0, 0)
        })

        if (kind == InterventionKind.LATE) {
            val actions = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, dp(16), 0, 0)
            }
            actions.addView(button("Snooze 5 min") { onSnooze() }, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
            actions.addView(button("I'm on it", filled = true) { onAcknowledge() }, LayoutParams(0, dp(48), 1f))
            addView(actions)
        } else {
            addView(TextView(context).apply {
                text = "No snooze · volume or long-press to exit"
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            })
        }
    }

    private fun button(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (filled) IcarusColors.BG else IcarusColors.TEXT_DIM)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(0x24FFFFFF),
                GradientDrawable().apply {
                    setColor(if (filled) IcarusColors.ACCENT else IcarusColors.CARD)
                    cornerRadius = dp(10).toFloat()
                },
                null,
            )
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
