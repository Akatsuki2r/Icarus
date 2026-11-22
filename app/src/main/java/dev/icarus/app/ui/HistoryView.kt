package dev.icarus.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.icarus.app.data.DayStateStore
import dev.icarus.engine.DaySummary
import dev.icarus.engine.History
import java.time.format.DateTimeFormatter

class HistoryView(context: Context) : LinearLayout(context) {

    private val store = DayStateStore(context)
    private val longFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

    fun render() {
        removeAllViews()
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(IcarusColors.BG)
        setPadding(dp(20), dp(28), dp(20), dp(20))

        addView(TextView(context).apply {
            text = "History"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        addView(TextView(context).apply {
            text = "What actually happened, from the last two weeks."
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(18))
        })

        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val summaries = History.summarize(store.recent(14))
        if (summaries.isEmpty()) {
            rows.addView(TextView(context).apply {
                text = "Nothing recorded yet. Check off a block or recover a day and it will show up here."
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(IcarusColors.CARD, 12, IcarusColors.HAIRLINE)
            })
        } else {
            summaries.forEach { rows.addView(row(it)) }
        }

        rows.addView(TextView(context).apply {
            text = "Changes"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(18), 0, dp(8))
        })
        val edits = dev.icarus.app.data.SettingsStore(context).editLog().take(8)
        if (edits.isEmpty()) {
            rows.addView(TextView(context).apply {
                text = "No routine edits recorded yet."
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(14), dp(8), dp(14), dp(12))
            })
        } else {
            edits.forEach { edit ->
                rows.addView(TextView(context).apply {
                    text = "${edit.day} · ${edit.note}"
                    setTextColor(IcarusColors.TEXT_DIM)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                })
            }
        }

        addView(ScrollView(context).apply { addView(rows) })
    }

    private fun row(summary: DaySummary): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(IcarusColors.CARD, 12, IcarusColors.HAIRLINE)

        addView(TextView(context).apply {
            text = summary.date.format(longFormat)
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        addView(TextView(context).apply {
            val reclaimed = if (summary.reclaimed) " · recovered" else ""
            text = "${summary.completed} done · ${summary.skipped} skipped" + reclaimed
            setTextColor(if (summary.completed > 0) IcarusColors.GOOD else IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
