package dev.icarus.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.icarus.app.data.RoutineStore
import dev.icarus.app.ui.IcarusColors
import dev.icarus.engine.Routine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Week overview: seven rows, tap one to edit that day's blocks. */
class WeekEditorActivity : Activity() {

    private lateinit var store: RoutineStore
    private lateinit var list: LinearLayout
    private lateinit var range: TextView
    private var shownTokens: dev.icarus.app.Tokens? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RoutineStore(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        IcarusTheme.apply(this)
        shownTokens = IcarusColors.current()
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        range = label("", IcarusColors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(20))
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(label("Your week", IcarusColors.TEXT).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(4), 0, 0)
            })
            addView(range)
            addView(list)
            addView(
                button("Edit JSON directly") {
                    startActivity(Intent(this@WeekEditorActivity, RoutineEditorActivity::class.java))
                }.apply { setPadding(dp(14), dp(12), dp(14), dp(12)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(18) },
            )
        }

        val root = ScrollView(this).apply {
            setBackgroundColor(IcarusColors.BG)
            clipToPadding = false
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                val top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
                view.setPadding(0, dp(24) + top, 0, dp(24))
                insets
            }
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        IcarusTheme.apply(this)
        if (shownTokens != IcarusColors.current()) {
            shownTokens = IcarusColors.current()
            setContentView(buildUi())
        }
        render()
    }

    private fun render() {
        val routine = store.load()
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val sunday = monday.plusDays(6)
        range.text = "${monday.dayOfMonth} – ${sunday.dayOfMonth} ${sunday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"

        list.removeAllViews()
        DayOfWeek.entries.forEach { day -> list.addView(dayRow(day, routine, day == today.dayOfWeek)) }
    }

    private fun dayRow(day: DayOfWeek, routine: Routine, isToday: Boolean): View {
        val blocks = routine.blocksFor(day)
        val minutes = blocks.sumOf { it.duration.toMinutes() }
        val hours = minutes / 60
        val rest = minutes % 60
        val count = "$hours" + if (rest > 0) "h ${rest}m" else "h"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            background = if (isToday) rounded(IcarusColors.CARD, 12, IcarusColors.HAIRLINE) else rounded(Color.TRANSPARENT, 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(
                    Intent(this@WeekEditorActivity, DayEditorActivity::class.java)
                        .putExtra(Contract.EXTRA_DAY, day.name),
                )
            }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(
            if (isToday) "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())} · today"
            else day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            if (isToday) IcarusColors.ACCENT else IcarusColors.TEXT,
        ).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val span = if (blocks.isEmpty()) {
            "no blocks"
        } else {
            "${blocks.first().start.format(hm)} – ${blocks.last().end.format(hm)}"
        }
        texts.addView(label("$span  ·  ${blocks.size} blocks · $count", IcarusColors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val trackWidth = dp(40)
        val track = View(this).apply {
            background = rounded(IcarusColors.TRACK, 2)
        }
        val fill = View(this).apply {
            background = rounded(if (isToday) IcarusColors.ACCENT else IcarusColors.MUTED, 2)
        }
        val bar = LinearLayout(this).apply { addView(fill, LinearLayout.LayoutParams((trackWidth * (minutes / 1440f)).toInt().coerceAtLeast(2), dp(4))) }
        row.addView(bar, LinearLayout.LayoutParams(trackWidth, dp(4)))
        return row
    }

    private fun label(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
    }

    private fun button(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(IcarusColors.TEXT_DIM)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(0x24FFFFFF),
            rounded(IcarusColors.CARD, 12, IcarusColors.HAIRLINE),
            null,
        )
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
