package dev.icarus.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.icarus.app.data.RoutineStore
import dev.icarus.engine.Block
import dev.icarus.engine.Moment
import dev.icarus.engine.Priority
import dev.icarus.engine.Recovery
import dev.icarus.engine.Routine
import dev.icarus.engine.Timeline
import dev.icarus.app.ui.Destination
import dev.icarus.app.ui.DockView
import dev.icarus.app.ui.HistoryView
import dev.icarus.app.ui.RoutineHomeView
import dev.icarus.app.ui.SettingsView
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

    private lateinit var shell: FrameLayout
    private lateinit var dock: DockView
    private lateinit var todayScreen: View
    private lateinit var routineScreen: RoutineHomeView
    private lateinit var historyScreen: HistoryView
    private lateinit var settingsScreen: SettingsView
    private var destination: Destination = Destination.TODAY

    private lateinit var routineStore: RoutineStore
    private var routine: Routine = Routine.EMPTY
    private var timeline: Timeline = Timeline(LocalDate.now(), emptyList())

    private lateinit var card: LinearLayout
    private lateinit var clock: TextView
    private lateinit var dateLine: TextView
    private lateinit var cardLabel: TextView
    private lateinit var cardTitle: TextView
    private lateinit var cardRange: TextView
    private lateinit var cardBar: ProgressBar
    private lateinit var cardRemaining: TextView
    private lateinit var cardProgressText: TextView
    private lateinit var nextStrip: LinearLayout
    private lateinit var nextText: TextView
    private lateinit var comingList: LinearLayout
    private lateinit var dayList: LinearLayout
    private lateinit var todayCount: TextView
    private lateinit var warningRow: LinearLayout
    private lateinit var warningText: TextView
    private lateinit var status: TextView
    private lateinit var resetButton: TextView
    private lateinit var skipButton: TextView
    private lateinit var doneButton: TextView
    private lateinit var recoverButton: TextView

    private var listSignature: String? = null
    private var cardKey: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routineStore = RoutineStore(this)
        routine = routineStore.load()

        // targetSdk 36 means the system already draws us edge to edge; we pad
        // ourselves so the clock never sits under the status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        buildShell()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { onBack() }
        }
    }

    private fun buildShell() {
        todayScreen = buildUi()
        routineScreen = RoutineHomeView(this).apply { render() }
        historyScreen = HistoryView(this).apply { render() }
        settingsScreen = SettingsView(this).apply { render() }

        shell = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        dock = DockView(this) { show(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colors.BG)
            addView(shell)
            addView(
                dock,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8) },
            )
            setOnApplyWindowInsetsListener { view, insets ->
                val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                view.setPadding(0, 0, 0, dp(8) + bottom)
                insets
            }
        }
        setContentView(root)
        show(Destination.TODAY)
    }

    private fun show(next: Destination) {
        destination = next
        when (next) {
            Destination.ROUTINE -> routineScreen.render()
            Destination.HISTORY -> historyScreen.render()
            Destination.SETTINGS -> settingsScreen.render()
            Destination.TODAY -> Unit
        }
        shell.removeAllViews()
        shell.addView(
            when (next) {
                Destination.TODAY -> todayScreen
                Destination.ROUTINE -> routineScreen
                Destination.HISTORY -> historyScreen
                Destination.SETTINGS -> settingsScreen
            },
        )
        dock.select(next)
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        onBack()
    }

    private fun onBack() {
        if (destination != Destination.TODAY) {
            show(Destination.TODAY)
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        routine = routineStore.load()
        val settings = dev.icarus.app.data.SettingsStore(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !settings.permissionAsked("notifications")
        ) {
            settings.markPermissionAsked("notifications")
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        if (::settingsScreen.isInitialized && destination == Destination.SETTINGS) settingsScreen.render()
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
            toast("Nothing to recover — you're on schedule.")
            return
        }
        val message = plan.summary.joinToString("\n") { "• $it" }

        AlertDialog.Builder(this)
            .setTitle("Recover the rest of today")
            .setMessage(message)
            .setPositiveButton("Apply") { _, _ ->
                Actions.recover(this)
                tapFeedback()
                toast("Recovered: ${plan.summary.size} change(s)")
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun skipCurrent() {
        val block = timeline.currentBlock(LocalTime.now()) ?: run {
            toast("Nothing is running.")
            return
        }
        Actions.skip(this, block.id)
        tapFeedback()
        toast("Skipped ${block.title}")
        reload()
    }

    private fun markDone() {
        val block = timeline.currentBlock(LocalTime.now()) ?: run {
            toast("Nothing is running.")
            return
        }
        if (block.id in timeline.completed) {
            Actions.uncomplete(this, block.id)
        } else {
            Actions.complete(this, block.id)
        }
        tapFeedback()
        reload()
    }

    private fun resetPlan() {
        Actions.onTrack(this)
        tapFeedback()
        toast("Back on the template.")
        reload()
    }

    private fun reload() {
        listSignature = null
        TimekeeperService.start(this)
        render()
    }

    // ----------------------------------------------------------------- render

    private fun render() {
        if (IcarusTheme.apply(this)) onThemeChanged()
        val now = LocalTime.now()
        clock.text = now.format(clockFormat)

        val date = LocalDate.now()
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

        var key: String
        when (val moment = timeline.moment(now)) {
            is Moment.Active -> {
                val block = moment.block
                key = "a:${block.id}"
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
                key = "w:${next?.id}"
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
                key = "f"
                cardLabel.text = "DONE"
                cardTitle.text = "Day finished"
                cardRange.text = "${timeline.activeBlocks().count { it.id in timeline.completed }} " +
                    "of ${timeline.activeBlocks().size} blocks done"
                cardBar.visibility = View.INVISIBLE
                cardProgressText.text = ""
                cardRemaining.text = "go to sleep"
                cardRemaining.setTextColor(Colors.DIM)
            }
        }

        if (key != cardKey) {
            val first = cardKey == null
            cardKey = key
            if (!first) animateCardIn()
        }

        // The strip only exists to show the transition while a block is running;
        // when idle the card already says what is next.
        if (timeline.moment(now) is Moment.Active) {
            nextStrip.visibility = View.VISIBLE
            val next = timeline.nextAfter(now)
            nextText.text = if (next != null) {
                "${next.start.format(hm)}  ${next.title}  ·  in " +
                    short(java.time.Duration.between(now, next.start).toMinutes())
            } else {
                "nothing left today"
            }
        } else {
            nextStrip.visibility = View.GONE
        }

        val active = timeline.currentBlock(now)
        skipButton.isEnabled = active != null
        skipButton.alpha = if (active != null) 1f else 0.35f
        doneButton.isEnabled = active != null
        doneButton.alpha = if (active != null) 1f else 0.35f
        doneButton.text = if (active != null && active.id in timeline.completed) "Undo" else "Mark done"
        resetButton.text = if (state.plan != null) "Undo recovery" else "Reset"
        resetButton.isEnabled = state.plan != null
        resetButton.alpha = if (state.plan != null) 1f else 0.35f
        recoverButton.alpha = if (timeline.activeBlocks().isEmpty()) 0.5f else 1f

        todayCount.text = "${timeline.activeBlocks().count { it.id in timeline.completed }} / " +
            "${timeline.activeBlocks().size}"

        updateStatus()

        val signature = timeline.blocks.joinToString("|") { block ->
            "${block.id}@${block.start}-${block.end}#${block.id in timeline.completed}#${block.id in timeline.skipped}"
        }

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
            missing += "notifications are off"
        }
        if (!Alarms.canScheduleExact(this)) missing += "exact alarms are off"

        if (missing.isEmpty()) {
            warningRow.visibility = View.GONE
            status.text = "timekeeper running · alerts: prepare, start, overdue"
            status.setTextColor(Colors.TEXT_DIM)
        } else {
            warningRow.visibility = View.VISIBLE
            warningText.text = missing.joinToString(" · ")
            status.text = "the timekeeper cannot reach you until this is fixed"
            status.setTextColor(Colors.MUTED)
        }
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
        if (timeline.blocks.isEmpty()) {
            dayList.addView(note("no blocks scheduled — tap Edit routine"))
            return
        }
        timeline.blocks.forEach { dayList.addView(row(it, now)) }
    }

    // ------------------------------------------------------------------- view

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(
            label("ICARUS", Colors.ACCENT).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = 0.3f
                typeface = monoBold
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        clock = label("--:--:--", Colors.DIM).apply {
            typeface = mono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        header.addView(clock)
        content.addView(header)

        dateLine = label("", Colors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(22))
        }
        content.addView(dateLine)

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Colors.CARD, 18, Colors.HAIRLINE)
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val cardTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cardLabel = label("NOW", Colors.ACCENT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.22f
            typeface = monoBold
        }
        cardProgressText = label("", Colors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.END
            typeface = mono
        }
        cardTop.addView(cardLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cardTop.addView(cardProgressText)
        card.addView(cardTop)

        cardTitle = label("", Colors.TEXT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            typeface = sansMedium
            setLineSpacing(0f, 1.05f)
            setPadding(0, dp(10), 0, dp(4))
        }
        card.addView(cardTitle)

        cardRange = label("", Colors.DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = mono
            setPadding(0, 0, 0, dp(16))
        }
        card.addView(cardRange)

        cardBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Colors.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(Colors.TRACK)
            minimumHeight = dp(4)
        }
        card.addView(cardBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))

        cardRemaining = label("", Colors.ACCENT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = mono
            setPadding(0, dp(14), 0, 0)
        }
        card.addView(cardRemaining)
        content.addView(card)

        nextStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Colors.CARD_ALT, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        nextStrip.addView(
            label("NEXT", Colors.MUTED).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = 0.2f
                typeface = monoBold
                setPadding(0, 0, dp(12), 0)
            },
        )
        nextText = label("", Colors.TEXT_DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = mono
        }
        nextStrip.addView(nextText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(
            nextStrip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }
        recoverButton = button("Recover day", filled = true) { recover() }
        row1.addView(recoverButton, LinearLayout.LayoutParams(0, dp(48), 1.75f))
        row1.addView(spacer(dp(8)))
        resetButton = button("Reset") { resetPlan() }
        row1.addView(resetButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(6))
        }
        skipButton = button("Skip now") { skipCurrent() }
        doneButton = button("Mark done") { markDone() }
        row2.addView(skipButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        row2.addView(spacer(dp(8)))
        row2.addView(doneButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(row2)

        val todayHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(24), 0, dp(4))
        }
        todayHeader.addView(
            label("COMING UP", Colors.MUTED).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = 0.2f
                typeface = monoBold
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        content.addView(todayHeader)

        comingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(comingList)

        val dayHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(26), 0, dp(4))
        }
        dayHeader.addView(
            label("TODAY", Colors.MUTED).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = 0.2f
                typeface = monoBold
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        todayCount = label("", Colors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = mono
        }
        dayHeader.addView(todayCount)
        content.addView(dayHeader)

        dayList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(dayList)

        warningRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Colors.WARN_SOFT, 12)
            setPadding(dp(14), dp(10), dp(10), dp(10))
            visibility = View.GONE
        }
        warningText = label("", Colors.WARN).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        warningRow.addView(warningText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        warningRow.addView(
            button("Fix", filled = false) { fixPermissions() }.apply {
                setTextColor(Colors.WARN)
                background = ripple(Colors.WARN_SOFT, 10, ColorStateList.valueOf(0x33FFFFFF))
            },
            LinearLayout.LayoutParams(dp(72), dp(44)),
        )
        content.addView(
            warningRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(22) },
        )

        status = label("", Colors.TEXT_DIM).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(14), 0, dp(4))
        }
        content.addView(status)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        footer.addView(
            button("Edit routine") {
                startActivity(Intent(this@MainActivity, RoutineEditorActivity::class.java))
            },
            LinearLayout.LayoutParams(0, dp(44), 1f),
        )
        footer.addView(spacer(dp(8)))
        footer.addView(
            button("Battery") { batterySettings() },
            LinearLayout.LayoutParams(0, dp(44), 1f),
        )
        content.addView(footer)

        return ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            isFillViewport = true
            clipToPadding = false
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
                // Bottom inset belongs to the shell's dock, not the scrolling content.
                view.setPadding(0, dp(22) + topInset, 0, dp(12))
                insets
            }
        }
    }

    private fun row(block: Block, now: LocalTime): View {
        val done = block.id in timeline.completed
        val skipped = block.id in timeline.skipped
        val current = timeline.currentBlock(now)?.id == block.id
        val past = block.end.isBefore(now) && !done && !current && !skipped

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            if (skipped) alpha = 0.55f
        }

        val marker = View(this).apply {
            setBackgroundColor(
                when {
                    done -> Colors.GOOD
                    current -> Colors.ACCENT
                    skipped || past -> Colors.TRACK
                    else -> Colors.HAIRLINE
                },
            )
        }
        wrap.addView(marker, LinearLayout.LayoutParams(dp(3), dp(18)))

        val time = label(block.start.format(hm), if (current) Colors.ACCENT else Colors.DIM).apply {
            typeface = mono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), 0, dp(12), 0)
        }
        wrap.addView(time, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = label(
            (if (done) "✓ " else "") + block.title,
            when {
                current -> Colors.TEXT
                done || skipped -> Colors.MUTED
                past -> Colors.DIM
                else -> Colors.TEXT_DIM
            },
        ).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            // Only a skipped block is a cancellation. A past block is just past.
            if (skipped) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            if (block.priority == Priority.ANCHOR) typeface = sansMedium
        }
        wrap.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val meta = label(
            when {
                current -> "now"
                skipped -> "skipped"
                done -> "done"
                else -> "${block.duration.toMinutes()}m"
            },
            when {
                current -> Colors.ACCENT
                done -> Colors.GOOD
                else -> Colors.MUTED
            },
        ).apply {
            typeface = mono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        wrap.addView(meta)

        return wrap
    }

    // ------------------------------------------------------------------ bits

    private fun onThemeChanged() {
        if (!::shell.isInitialized) return
        val current = destination
        listSignature = null
        buildShell()
        show(current)
        crossfadeTheme()
    }

    private fun crossfadeTheme() {
        if (!::shell.isInitialized || !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        shell.alpha = 0.6f
        shell.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(PathInterpolator(0.23f, 1f, 0.32f, 1f))
            .start()
    }

    private fun animateCardIn() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            card.alpha = 1f
            card.translationY = 0f
            return
        }
        card.animate().cancel()
        card.alpha = 0f
        card.translationY = dp(8).toFloat()
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(PathInterpolator(0.23f, 1f, 0.32f, 1f))
            .start()
    }

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
        tapFeedback()
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    private fun tapFeedback() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun label(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
    }

    private fun note(text: String): TextView = label(text, Colors.MUTED).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(15), dp(10), 0, dp(10))
    }

    private fun button(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = if (filled) sansMedium else sans
            isClickable = true
            isFocusable = true
            setTextColor(if (filled) Colors.BG else Colors.TEXT_DIM)
            background = if (filled) {
                ripple(Colors.ACCENT, 12, ColorStateList.valueOf(0x22000000))
            } else {
                ripple(Colors.CARD, 12, ColorStateList.valueOf(0x24FFFFFF), Colors.HAIRLINE)
            }
            setOnClickListener {
                tapFeedback()
                onClick()
            }
        }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun ripple(fill: Int, radiusDp: Int, color: ColorStateList, stroke: Int? = null): RippleDrawable =
        RippleDrawable(color, rounded(fill, radiusDp, stroke), null)

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

    private val sans: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val sansMedium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val mono: Typeface get() = Typeface.MONOSPACE
    private val monoBold: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

    private object Colors {
        val BG get() = dev.icarus.app.ui.IcarusColors.BG
        val CARD get() = dev.icarus.app.ui.IcarusColors.CARD
        val CARD_ALT get() = dev.icarus.app.ui.IcarusColors.CARD
        val HAIRLINE get() = dev.icarus.app.ui.IcarusColors.HAIRLINE
        val TRACK get() = dev.icarus.app.ui.IcarusColors.TRACK
        val TEXT get() = dev.icarus.app.ui.IcarusColors.TEXT
        val TEXT_DIM get() = dev.icarus.app.ui.IcarusColors.TEXT_DIM
        val DIM get() = dev.icarus.app.ui.IcarusColors.MUTED
        val MUTED get() = dev.icarus.app.ui.IcarusColors.MUTED
        val ACCENT get() = dev.icarus.app.ui.IcarusColors.ACCENT
        val GOOD get() = dev.icarus.app.ui.IcarusColors.GOOD
        val WARN get() = dev.icarus.app.ui.IcarusColors.WARN
        val WARN_SOFT get() = dev.icarus.app.ui.IcarusColors.CARD
    }
}
