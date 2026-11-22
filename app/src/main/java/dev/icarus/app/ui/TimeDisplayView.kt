package dev.icarus.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Large, editable start/end times with 15-minute stepping and a duration chip. */
class TimeDisplayView(context: Context) : LinearLayout(context) {

    var onTimesChanged: ((LocalTime, LocalTime) -> Unit)? = null

    private val format = DateTimeFormatter.ofPattern("HH:mm")
    private var start: LocalTime = LocalTime.of(9, 0)
    private var end: LocalTime = LocalTime.of(10, 0)

    private val startField = timeField(28f)
    private val endField = timeField(20f)
    private val duration = TextView(context).apply {
        setTextColor(IcarusColors.MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.MONOSPACE
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = GradientDrawable().apply {
            setColor(IcarusColors.CARD)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), IcarusColors.HAIRLINE)
        }
    }
    private val error = TextView(context).apply {
        visibility = View.GONE
        setTextColor(IcarusColors.WARN)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(0, dp(6), 0, 0)
    }

    init {
        orientation = VERTICAL
        setPadding(0, dp(10), 0, dp(6))

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(stepper("-") { adjust(true, -15) })
        row.addView(startField, LayoutParams(dp(94), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(stepper("+") { adjust(true, 15) })
        row.addView(TextView(context).apply {
            text = "→"
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(8), 0, dp(8), 0)
        }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(stepper("-") { adjust(false, -15) })
        row.addView(endField, LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(stepper("+") { adjust(false, 15) })
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(duration, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        addView(error, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setTimes(from: LocalTime, to: LocalTime) {
        start = from
        end = to
        startField.setText(from.format(format))
        endField.setText(to.format(format))
        duration.text = TimeEdit.durationLabel(from, to)
        error.visibility = View.GONE
    }

    private fun adjust(isStart: Boolean, minutes: Int) {
        val current = if (isStart) start else end
        val next = TimeEdit.shift(current, minutes) ?: return
        val problem = if (isStart) TimeEdit.validate(next, end) else TimeEdit.validate(start, next)
        if (problem != null) {
            error.text = problem
            error.visibility = View.VISIBLE
            return
        }
        if (isStart) start = next else end = next
        setTimes(start, end)
        onTimesChanged?.invoke(start, end)
    }

    private fun commitField(isStart: Boolean) {
        val field = if (isStart) startField else endField
        val parsed = runCatching { LocalTime.parse(field.text.toString().trim(), format) }.getOrNull()
        if (parsed == null) {
            field.setText((if (isStart) start else end).format(format))
            return
        }
        val problem = if (isStart) TimeEdit.validate(parsed, end) else TimeEdit.validate(start, parsed)
        if (problem != null) {
            error.text = problem
            error.visibility = View.VISIBLE
            field.setText((if (isStart) start else end).format(format))
            return
        }
        if (isStart) start = parsed else end = parsed
        setTimes(start, end)
        onTimesChanged?.invoke(start, end)
    }

    private fun timeField(size: Float): EditText = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        setTextColor(IcarusColors.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply {
            setColor(IcarusColors.RAISED)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), IcarusColors.HAIRLINE)
        }
        setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) commitField(view === startField)
        }
    }

    private fun stepper(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(IcarusColors.ACCENT)
        isClickable = true
        isFocusable = true
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        background = RippleDrawable(
            ColorStateList.valueOf(0x24FFFFFF),
            GradientDrawable().apply {
                setColor(IcarusColors.CARD)
                cornerRadius = dp(10).toFloat()
            },
            null,
        )
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
