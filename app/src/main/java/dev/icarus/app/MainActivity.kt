package dev.icarus.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.icarus.app.data.RoutineStore
import dev.icarus.engine.Block
import dev.icarus.engine.Moment
import dev.icarus.engine.Recovery
import dev.icarus.engine.Timeline
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The screen is a status panel, not the product. The product is the shade
 * notification and the three alerts. This exists for the ten seconds a day
 * when you need to change something.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var routineStore: RoutineStore
    private var timeline: Timeline = Timeline(LocalDate.now(), emptyList())

    private lateinit var clock: TextView
    private lateinit var dateLine: TextView
    private lateinit var cardLabel: TextView
    private lateinit var cardTitle: TextView
    private lateinit var cardRange: TextView
    private lateinit var cardBar: ProgressBar
    private lateinit var cardRemaining: TextView
    private lateinit var cardProgressText: TextView
    private lateinit var nextLabel: TextView
    private lateinit var nextText: TextView
    private lateinit var comingList: LinearLayout
    private lateinit var dayList: LinearLayout
    private lateinit var status: TextView
    private lateinit var skipButton: TextView
    private lateinit var doneButton: TextView

    private var listSignature: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routineStore = RoutineStore(this)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        TimekeeperService.start(this)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------------------------------------------------------------- actions

    private fun recover() {
        val plan = Recovery.plan(LocalTime.now(), timeline.activeBlocks(), timeline.completed)
        if (plan.isEmpty) {
            toast("Nothing to recover - you are on schedule.")
            return
        }
        val message = buildString {
            plan.summary.forEach { appendLine("• $it") }
        }.trim()

        AlertDialog.Builder(this)
            .setTitle("Recover the rest of today")
            .setMessage(message.ifBlank { "No changes needed." })
            .setPositiveButton("Apply") { _, _ ->
                val applied = Actions.recover(this)
                toast("Recovered: ${applied.changes.count { it.type != Recovery.ChangeType.KEPT }} change(s)")
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun skipCurrent() {
        val now = LocalTime.now()
        val block = timeline.currentBlock(now) ?: run {
            toast("Nothing is running.")
            return
        }
        Actions.skip(this, block.id)
        toast("Skipped ${block.title}")
        reload()
    }

    private fun markDone() {
        val now = LocalTime.now()
        val block = timeline.currentBlock(now) ?: run {
            toast("Nothing is running.")
            return
        }
        if (block.id in timeline.completed) {
            Actions.skip(this, block.id)
        } else {
            Actions.complete(this, block.id)
        }
        reload()
    }

    private fun reload() {
        listSignature = null
        TimekeeperService.start(this)
        render()
    }

    // ----------------------------------------------------------------- render

    private fun render() {
        val now = LocalTime.now()
        clock.text = now.format(clockFormat)

        val date = LocalDate.now()
        val routine = routineStore.load()
        val state = Actions.state(this, date)
        timeline = Timeline.of(date.dayOfWeek, date, routine, state)

        dateLine.text = buildString {
            append(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            append(" ")
            append(date.dayOfMonth)
            append(" ")
            append(date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
            append("  ·  week ")
            append(date.get(WeekFields.ISO.weekOfWeekBasedYear()))
        }

        when (val moment = timeline.moment(now)) {
            is Moment.Active -> {
                val block = moment.block
                cardLabel.text = "NOW"
                cardTitle.text = block.title
                cardRange.text = "${block.start.format(hm)} – ${block.end.format(hm)}  ·  ${block.category.label}"
                cardBar.visibility = View.VISIBLE
                cardBar.progress = (moment.fraction * 100).toInt()
                cardProgressText.text = "${(moment.fraction * 100).toInt()}% elapsed"
                cardRemaining.text = "${short(moment.remaining.toMinutes())} left"
                cardRemaining.setTextColor(Colors.ACCENT)
            }

            is Moment.Waiting -> {
                val next = moment.next
                cardLabel.text = "NEXT"
                cardTitle.text = next?.title ?: "Nothing scheduled"
                cardRange.text = if (next != null) {
                    "${next.start.format(hm)} – ${next.end.format(hm)}  ·  ${next.category.label}"
                } else {
                    ""
                }
                cardBar.visibility = View.INVISIBLE
                cardProgressText.text = ""
                cardRemaining.text = if (next != null) {
                    "starts in ${short(moment.untilNext.toMinutes())}"
                } else {
                    "day is clear"
                }
                cardRemaining.setTextColor(Colors.DIM)
            }

            Moment.Finished -> {
                cardLabel.text = "DONE"
                cardTitle.text = "Day finished"
                cardRange.text = "${timeline.activeBlocks().count { it.id in timeline.completed }} of ${timeline.activeBlocks().size} blocks done"
                cardBar.visibility = View.INVISIBLE
                cardProgressText.text = ""
                cardRemaining.text = "go to sleep"
                cardRemaining.setTextColor(Colors.DIM)
            }
        }

        val next = timeline.nextAfter(now)
        if (next != null) {
            nextLabel.text = "NEXT TRANSITION"
            nextText.text = "${next.start.format(hm)}  ${next.title}  ·  in ${short(java.time.Duration.between(now, next.start).toMinutes())}"
        } else {
            nextLabel.text = "NEXT TRANSITION"
            nextText.text = "nothing left today"
        }

        val active = timeline.currentBlock(now)
        skipButton.isEnabled = active != null
        skipButton.alpha = if (active != null) 1f else 0.4f
        doneButton.isEnabled = active != null
        doneButton.alpha = if (active != null) 1f else 0.4f
        doneButton.text = if (active != null && active.id in timeline.completed) "Undo done" else "Mark done"

        updateStatus()

        val signature = timeline.blocks.joinToString("|") {
            "${it.id}@${it.start}-${it.end}#${it.id in timeline.completed}#${it.id in timeline.skipped}"
        } + "c${timeline.completed.size}s${timeline.skipped.size}"

        if (signature != listSignature) {
            listSignature = signature
            rebuildLists(now)
        }
    }

    private fun updateStatus() {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += "notifications off"
        }
        if (!Alarms.canScheduleExact(this)) missing += "exact alarms off"

        status.text = if (missing.isEmpty()) {
            "timekeeper running · alerts: prepare, start, overdue"
        } else {
            "attention: ${missing.joinToString(", ")} — tap to fix"
        }
        status.setTextColor(if (missing.isEmpty()) Colors.DIM else Colors.WARN)
        status.setOnClickListener { if (missing.isEmpty()) batterySettings() else fixPermissions() }
    }

    private fun rebuildLists(now: LocalTime) {
        comingList.removeAllViews()
        val upcoming = timeline.upcoming(now, 4)
        if (upcoming.isEmpty()) {
            comingList.addView(note("nothing else today"))
        } else {
            upcoming.forEach { comingList.addView(row(it, now)) }
        }

        dayList.removeAllViews()
        timeline.activeBlocks().forEach { dayList.addView(row(it, now)) }
        if (timeline.blocks.size > timeline.activeBlocks().size) {
            dayList.addView(note("${timeline.blocks.size - timeline.activeBlocks().size} block(s) skipped today"))
        }
    }

    // ------------------------------------------------------------------- view

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(Colors.BG)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label("ICARUS", Colors.ACCENT).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                letterSpacing = 0.28f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        clock = label("--:--:--", Colors.DIM).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        header.addView(clock)
        root.addView(header)

        dateLine = label("", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(18))
        }
        root.addView(dateLine)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Colors.CARD, 16)
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val cardTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cardLabel = label("NOW", Colors.ACCENT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.2f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        cardProgressText = label("", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.END
        }
        cardTop.addView(cardLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cardTop.addView(cardProgressText)
        card.addView(cardTop)

        cardTitle = label("", Colors.TEXT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, dp(8), 0, dp(2))
        }
        card.addView(cardTitle)

        cardRange = label("", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(cardRange)

        cardBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Colors.ACCENT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Colors.TRACK)
            minimumHeight = dp(6)
        }
        card.addView(cardBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)))

        cardRemaining = label("", Colors.ACCENT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(12), 0, 0)
            typeface = Typeface.MONOSPACE
        }
        card.addView(cardRemaining)
        root.addView(card)

        val nextStrip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Colors.CARD_ALT, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        nextLabel = label("NEXT TRANSITION", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.18f
        }
        nextText = label("", Colors.TEXT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        nextStrip.addView(nextLabel)
        nextStrip.addView(nextText)
        root.addView(nextStrip)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        row1.addView(
            button("Recover day", filled = true) { recover() },
            LinearLayout.LayoutParams(0, dp(44), 1.6f),
        )
        row1.addView(spacer(dp(8)))
        row1.addView(
            button("Reset") {
                Actions.onTrack(this)
                toast("Back on the template.")
                reload()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f),
        )
        root.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(22))
        }
        skipButton = button("Skip now") { skipCurrent() }
        doneButton = button("Mark done") { markDone() }
        row2.addView(skipButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        row2.addView(spacer(dp(8)))
        row2.addView(doneButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(row2)

        root.addView(section("COMING UP"))
        comingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(comingList)

        root.addView(section("TODAY"))
        dayList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(dayList)

        status = label("", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(24), 0, dp(6))
        }
        root.addView(status)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        footer.addView(
            button("Edit routine") {
                startActivity(Intent(this@MainActivity, RoutineEditorActivity::class.java))
            },
            LinearLayout.LayoutParams(0, dp(40), 1f),
        )
        footer.addView(spacer(dp(8)))
        footer.addView(
            button("Battery") { batterySettings() },
            LinearLayout.LayoutParams(0, dp(40), 1f),
        )
        root.addView(footer)

        return ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            isFillViewport = true
            addView(root)
        }
    }

    private fun row(block: Block, now: LocalTime): View {
        val done = block.id in timeline.completed
        val current = timeline.currentBlock(now)?.id == block.id
        val past = block.end.isBefore(now) && !done && !current

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }

        val marker = View(this).apply {
            setBackgroundColor(
                when {
                    done -> Colors.GOOD
                    current -> Colors.ACCENT
                    past -> Colors.MUTED
                    else -> Colors.TRACK
                },
            )
        }
        wrap.addView(marker, LinearLayout.LayoutParams(dp(3), dp(20)))

        val time = label(block.start.format(hm), if (current) Colors.ACCENT else Colors.DIM).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), 0, dp(12), 0)
        }
        wrap.addView(time, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = label(
            (if (done) "✓ " else "") + block.title,
            when {
                done -> Colors.MUTED
                current -> Colors.TEXT
                past -> Colors.MUTED
                else -> Colors.TEXT_DIM
            },
        ).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            if (done || past) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }
        wrap.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val meta = label(
            if (current) "now" else "${block.duration.toMinutes()}m",
            if (current) Colors.ACCENT else Colors.MUTED,
        ).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        wrap.addView(meta)

        return wrap
    }

    // ------------------------------------------------------------------ bits

    private fun fixPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Alarms.canScheduleExact(this)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun batterySettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun label(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
    }

    private fun section(text: String): TextView = label(text, Colors.DIM).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        letterSpacing = 0.18f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun note(text: String): TextView = label(text, Colors.MUTED).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(15), dp(8), 0, dp(8))
    }

    private fun button(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isClickable = true
            isFocusable = true
            setTextColor(if (filled) Colors.BG else Colors.TEXT_DIM)
            background = if (filled) rounded(Colors.ACCENT, 10) else rounded(Colors.CARD, 10, Colors.TRACK)
            setOnClickListener { onClick() }
        }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun spacer(width: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun short(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        return when {
            h > 0 && m % 60 > 0 -> "${h}h ${m % 60}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private object Colors {
        val BG = Color.parseColor("#0B0C0E")
        val CARD = Color.parseColor("#15181B")
        val CARD_ALT = Color.parseColor("#101315")
        val TRACK = Color.parseColor("#242A2F")
        val TEXT = Color.parseColor("#EDEEF0")
        val TEXT_DIM = Color.parseColor("#C3C7CC")
        val DIM = Color.parseColor("#878D95")
        val MUTED = Color.parseColor("#5A6068")
        val ACCENT = Color.parseColor("#F2B84B")
        val GOOD = Color.parseColor("#6FCF8A")
        val WARN = Color.parseColor("#E08163")
    }
}
