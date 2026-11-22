package dev.icarus.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.icarus.app.Actions
import dev.icarus.app.BlockEditSheet
import dev.icarus.app.DayEditorActivity
import dev.icarus.app.WeekEditorActivity
import dev.icarus.app.data.RoutineStore
import dev.icarus.app.data.SettingsStore
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.EditEntry
import dev.icarus.engine.EditKind
import dev.icarus.engine.Interrupt
import dev.icarus.engine.Priority
import dev.icarus.engine.nextFreeHour
import dev.icarus.engine.withBlock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The planning surface: pick a day, see and edit its blocks, add new ones. */
class PlanView(context: Context) : LinearLayout(context) {

    private val store = RoutineStore(context)
    private val settings = SettingsStore(context)
    private var selected: DayOfWeek = LocalDate.now().dayOfWeek
    private var sheet: BlockEditSheet? = null

    fun render() {
        removeAllViews()
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(IcarusColors.BG)
        setPadding(dp(20), dp(28), dp(20), dp(20))

        addView(TextView(context).apply {
            text = "Plan"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        addView(TextView(context).apply {
            text = "Block your week, then shape a day."
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(16))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        DayOfWeek.entries.forEach { day ->
            strip.addView(action(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) {
                selected = day
                render()
            }, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        }
        body.addView(strip)

        val routine = store.load()
        val blocks = routine.blocksFor(selected)
        val minutes = blocks.sumOf { it.duration.toMinutes() }
        body.addView(TextView(context).apply {
            text = "${selected.getDisplayName(TextStyle.FULL, Locale.getDefault())}  ·  " +
                "${blocks.size} blocks  ·  ${minutes / 60}h ${minutes % 60}m"
            setTextColor(IcarusColors.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(18), 0, dp(8))
        })

        val hm = DateTimeFormatter.ofPattern("HH:mm")
        if (blocks.isEmpty()) {
            body.addView(TextView(context).apply {
                text = "Nothing planned for ${selected.getDisplayName(TextStyle.FULL, Locale.getDefault())} yet."
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = card()
            })
        } else {
            blocks.forEach { block ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(56)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = card()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        context.startActivity(
                            Intent(context, DayEditorActivity::class.java)
                                .putExtra("day", selected.name),
                        )
                    }
                }
                val times = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                times.addView(TextView(context).apply {
                    text = block.start.format(hm)
                    setTextColor(IcarusColors.ACCENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.MONOSPACE
                })
                times.addView(TextView(context).apply {
                    text = "→ ${block.end.format(hm)} · ${dev.icarus.app.ui.TimeEdit.durationLabel(block.start, block.end)}"
                    setTextColor(IcarusColors.MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.MONOSPACE
                })
                row.addView(times, LayoutParams(dp(94), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(TextView(context).apply {
                    text = block.title
                    setTextColor(IcarusColors.TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(context).apply {
                    text = block.priority.name.lowercase()
                    setTextColor(IcarusColors.MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                })
                body.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
        }

        body.addView(action("Plan something", filled = true) { quickAdd() },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        body.addView(action("Open full week editor") {
            context.startActivity(Intent(context, WeekEditorActivity::class.java))
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        addView(ScrollView(context).apply { addView(body) }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun quickAdd() {
        val routine = store.load()
        val start = routine.nextFreeHour(selected)
        val draft = Block(
            id = "new-${System.currentTimeMillis()}",
            title = "New block",
            start = start,
            end = start.plusHours(1),
            category = Category.PERSONAL,
            priority = Priority.FIRM,
            interrupt = Interrupt.NUDGE,
        )
        val activity = context as Activity
        sheet?.dismiss()
        val editor = BlockEditSheet(
            activity,
            draft,
            onSave = { candidate ->
                if (candidate.title.isBlank()) {
                    sheet?.setError("Give the block a title.")
                    return@BlockEditSheet
                }
                val edited = store.load().withBlock(selected, candidate)
                val problems = edited.problems()
                if (problems.isNotEmpty()) {
                    sheet?.setError(problems.first())
                    return@BlockEditSheet
                }
                store.save(edited)
                Actions.onTrack(context)
                settings.recordEdit(
                    EditEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        at = Instant.now(),
                        kind = EditKind.CREATE,
                        day = selected,
                        before = null,
                        after = candidate,
                        note = "Added ${candidate.title}",
                    ),
                )
                sheet?.dismiss()
                render()
            },
            onDelete = {},
        )
        sheet = editor
        editor.show()
    }

    private fun action(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (filled) IcarusColors.BG else IcarusColors.TEXT_DIM)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(48)
            background = RippleDrawable(
                ColorStateList.valueOf(if (filled) 0x22000000 else 0x24FFFFFF),
                GradientDrawable().apply {
                    setColor(if (filled) IcarusColors.ACCENT else IcarusColors.CARD)
                    cornerRadius = dp(12).toFloat()
                    if (!filled) setStroke(dp(1), IcarusColors.HAIRLINE)
                },
                null,
            )
            setOnClickListener { onClick() }
        }

    private fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(IcarusColors.CARD)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), IcarusColors.HAIRLINE)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
