package dev.icarus.app

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.icarus.app.ui.IcarusColors
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Interrupt
import dev.icarus.engine.Priority
import java.time.LocalTime

/** Bottom sheet for one block. Save applies through the caller; validation stays visible. */
class BlockEditSheet(
    private val activity: Activity,
    private val original: Block,
    private val onSave: (Block) -> Unit,
    private val onDelete: (Block) -> Unit,
) {
    private val dialog = Dialog(activity).apply {
        setContentView(buildContent())
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { dimAmount = 0.6f }
        }
    }

    private var start = original.start
    private var end = original.end
    private var priority = original.priority
    private var interrupt = original.interrupt
    private var category = original.category

    private lateinit var startText: TextView
    private lateinit var endText: TextView
    private lateinit var errorText: TextView
    private lateinit var titleField: EditText
    private val priorityChips = mutableMapOf<Priority, TextView>()
    private val interruptChips = mutableMapOf<Interrupt, TextView>()
    private val categoryChips = mutableMapOf<Category, TextView>()

    fun show() = dialog.show()
    fun dismiss() = dialog.dismiss()
    fun setError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun buildContent(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(IcarusColors.CARD)
                cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), 0f, 0f, 0f, 0f, dp(18).toFloat(), dp(18).toFloat())
            }
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }

        val heading = TextView(activity).apply {
            text = "Edit block"
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(heading)

        titleField = EditText(activity).apply {
            setText(original.title)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(IcarusColors.BG)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), IcarusColors.HAIRLINE)
            }
        }
        root.addView(titleField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        startText = timeRow(root, "Start", original.start) { dir -> stepTime(true, dir) }
        endText = timeRow(root, "End", original.end) { dir -> stepTime(false, dir) }

        root.addView(TextView(activity).apply {
            text = "Steps of 15 min"
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.END
            setPadding(0, dp(2), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        priorityChips.putAll(chipRow(root, "Priority", Priority.entries.map { it.name.lowercase() to it }, priority) {
            priority = it
            select(priorityChips, priority)
        })
        interruptChips.putAll(chipRow(root, "Alerts", Interrupt.entries.map { it.name.lowercase() to it }, interrupt) {
            interrupt = it
            select(interruptChips, interrupt)
        })
        categoryChips.putAll(chipRow(root, "Category", Category.entries.map { it.label to it }, category) {
            category = it
            select(categoryChips, category)
        })

        errorText = TextView(activity).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#E58D6F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(errorText)

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        actions.addView(action("Delete", filled = false) { onDelete(original) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        actions.addView(action("Save", filled = true) {
            val candidate = original.copy(
                title = titleField.text.toString().trim(),
                start = start,
                end = end,
                priority = priority,
                interrupt = interrupt,
                category = category,
            )
            onSave(candidate)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)
        return root
    }

    private fun timeRow(parent: LinearLayout, label: String, time: LocalTime, onStep: (Int) -> Unit): TextView {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        row.addView(TextView(activity).apply {
            text = label
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val value = TextView(activity).apply {
            text = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            setTextColor(IcarusColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), 0, dp(10), 0)
        }
        row.addView(value)
        row.addView(action("−", filled = false) { onStep(-1) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(action("+", filled = false) { onStep(1) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return value
    }

    private fun <T : Enum<T>> chipRow(
        parent: LinearLayout,
        label: String,
        entries: List<Pair<String, T>>,
        selected: T,
        onSelect: (T) -> Unit,
    ): Map<T, TextView> {
        parent.addView(TextView(activity).apply {
            text = label
            setTextColor(IcarusColors.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(12), 0, dp(2))
        })
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableMapOf<T, TextView>()
        entries.forEach { (text, value) ->
            val chip = action(text, filled = false) { onSelect(value) }
            chip.setPadding(dp(10), dp(8), dp(10), dp(8))
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { rightMargin = dp(6) })
            chips[value] = chip
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val result = chips.toMap()
        select(result, selected)
        return result
    }

    private fun <T> select(chips: Map<T, TextView>, selected: T) {
        chips.forEach { (value, chip) ->
            val active = value == selected
            chip.setTextColor(if (active) IcarusColors.BG else IcarusColors.TEXT_DIM)
            chip.background = actionBackground(active)
        }
    }

    private fun stepTime(isStart: Boolean, direction: Int) {
        val current = if (isStart) start else end
        val minutes = current.hour * 60 + current.minute + direction * 15
        if (minutes !in 0..1425) return
        val next = LocalTime.of(minutes / 60, minutes % 60)
        if (isStart && !next.isBefore(end) || !isStart && !next.isAfter(start)) {
            setError("End must be after start")
            return
        }
        if (isStart) {
            start = next
            startText.text = next.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            end = next
            endText.text = next.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        errorText.visibility = View.GONE
    }

    private fun action(text: String, filled: Boolean, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isClickable = true
            isFocusable = true
            setTextColor(if (filled) IcarusColors.BG else IcarusColors.TEXT_DIM)
            background = actionBackground(filled)
            setOnClickListener { onClick() }
        }

    private fun actionBackground(filled: Boolean): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(0x24FFFFFF),
        GradientDrawable().apply {
            setColor(if (filled) IcarusColors.ACCENT else IcarusColors.TRACK)
            cornerRadius = dp(10).toFloat()
        },
        null,
    )

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
