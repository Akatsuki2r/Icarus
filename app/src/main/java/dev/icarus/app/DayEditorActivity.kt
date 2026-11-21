package dev.icarus.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import dev.icarus.engine.Block
import dev.icarus.engine.Category
import dev.icarus.engine.Interrupt
import dev.icarus.engine.Priority
import dev.icarus.engine.Routine
import dev.icarus.engine.nextFreeHour
import dev.icarus.engine.removeBlock
import dev.icarus.engine.withBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One day: a 24-hour rail, the block list, and a single add action. */
class DayEditorActivity : Activity() {

    private lateinit var store: RoutineStore
    private lateinit var day: DayOfWeek
    private lateinit var rail: DayRailView
    private lateinit var list: LinearLayout
    private lateinit var subtitle: TextView
    private var sheet: BlockEditSheet? = null
    private var shownTokens: dev.icarus.app.Tokens? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RoutineStore(this)
        day = runCatching { DayOfWeek.valueOf(intent.getStringExtra(Contract.EXTRA_DAY).orEmpty()) }
            .getOrDefault(LocalDate.now().dayOfWeek)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        IcarusTheme.apply(this)
        shownTokens = IcarusColors.current()
        setContentView(buildUi())


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { finish() }
        }
    }

    private fun buildUi(): View {
        rail = DayRailView(this).apply { minimumHeight = dp(44) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        subtitle = label("", IcarusColors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(16))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(80))
            addView(label("‹  ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}", IcarusColors.TEXT).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(4), 0, 0)
            })
            addView(subtitle)
            addView(rail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            addView(TextView(this@DayEditorActivity).apply {
                text = "06                    12                    18"
                setTextColor(IcarusColors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setPadding(0, dp(6), 0, dp(10))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(IcarusColors.BG)
            addView(ScrollView(this@DayEditorActivity).apply {
                addView(content)
                setOnApplyWindowInsetsListener { view, insets ->
                    val top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.getInsets(WindowInsets.Type.systemBars()).top
                    } else {
                        @Suppress("DEPRECATION")
                        insets.systemWindowInsetTop
                    }
                    view.setPadding(0, dp(24) + top, 0, 0)
                    insets
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            val fab = TextView(this@DayEditorActivity).apply {
                text = "+"
                gravity = Gravity.CENTER
                setTextColor(IcarusColors.BG)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                background = RippleDrawable(
                    ColorStateList.valueOf(0x22000000),
                    GradientDrawable().apply {
                        setColor(IcarusColors.ACCENT)
                        cornerRadius = dp(28).toFloat()
                    },
                    null,
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { addBlock() }
            }
            addView(fab, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.END
                rightMargin = dp(20)
                bottomMargin = dp(20)
            })
            setOnApplyWindowInsetsListener { view, insets ->
                val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                view.setPadding(0, 0, 0, bottom)
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
        val blocks = routine.blocksFor(day)
        val minutes = blocks.sumOf { it.duration.toMinutes() }
        subtitle.text = "${blocks.size} blocks · ${minutes / 60}h ${minutes % 60}m planned"
        rail.setBlocks(blocks)

        list.removeAllViews()
        if (blocks.isEmpty()) {
            list.addView(label("Nothing planned for this day. Use + to add a block.", IcarusColors.MUTED).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14), dp(12), 0, dp(12))
            })
            return
        }
        blocks.forEach { list.addView(blockRow(it)) }
    }

    private fun blockRow(block: Block): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            background = rounded(IcarusColors.CARD, 12, IcarusColors.HAIRLINE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { openSheet(block) }
        }
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        row.addView(label(block.start.format(hm), IcarusColors.ACCENT).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(label(block.title, IcarusColors.TEXT).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label(block.priority.name.lowercase(), IcarusColors.MUTED).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        })
        return row
    }

    private fun addBlock() {
        val routine = store.load()
        val start = routine.nextFreeHour(day)
        val block = Block(
            id = "new-${System.currentTimeMillis()}",
            title = "New block",
            start = start,
            end = start.plusHours(1),
            category = Category.PERSONAL,
            priority = Priority.FIRM,
            interrupt = Interrupt.NUDGE,
        )
        openSheet(block)
    }

    private fun openSheet(block: Block) {
        sheet?.dismiss()
        sheet = BlockEditSheet(
            this,
            block,
            onSave = { candidate -> save(block, candidate) },
            onDelete = { toDelete -> confirmDelete(toDelete) },
        ).also { it.show() }
    }

    private fun save(original: Block, candidate: Block) {
        if (candidate.title.isBlank()) {
            sheet?.setError("Give the block a title.")
            return
        }
        val routine = store.load()
        val allIds = routine.allBlocks().map { it.id }.toSet()
        val unique = if (candidate.id in allIds && candidate.id != original.id) {
            candidate.copy(id = "${candidate.id}-${System.currentTimeMillis()}")
        } else {
            candidate
        }
        val edited = routine.withBlock(day, unique)
        val problems = edited.problems()
        if (problems.isNotEmpty()) {
            sheet?.setError(problems.first())
            return
        }
        store.save(edited)
        Actions.onTrack(this)
        sheet?.dismiss()
        render()
    }

    private fun confirmDelete(block: Block) {
        AlertDialog.Builder(this)
            .setTitle("Delete this block?")
            .setMessage(block.title)
            .setPositiveButton("Delete") { _, _ ->
                store.save(store.load().removeBlock(day, block.id))
                Actions.onTrack(this)
                sheet?.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = finish()

    private fun label(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
