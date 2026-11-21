package dev.icarus.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.icarus.app.data.RoutineStore

/**
 * The routine is data, so editing it is a text box. No drag-and-drop grid,
 * no wizard. Change a time, save, done. This also makes the schedule
 * greppable, diffable and copy-pasteable, which matters for a personal tool.
 */
class RoutineEditorActivity : Activity() {

    private lateinit var store: RoutineStore
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RoutineStore(this)
        IcarusTheme.apply(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colors.BG)
            setPadding(dp(18), 0, dp(18), 0)
        }

        root.addView(
            TextView(this).apply {
                text = "ROUTINE"
                setTextColor(Colors.ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = 0.3f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4) },
        )

        root.addView(
            TextView(this).apply {
                text = "24h times. priority: ANCHOR is fixed, FIRM should happen, FLEX is " +
                    "what recovery may spend.\ninterrupt: SILENT, NUDGE, STRICT (adds an overdue check)."
                setTextColor(Colors.MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(8), 0, dp(14))
            },
        )

        editor = EditText(this).apply {
            setText(store.raw())
            setTextColor(Colors.TEXT_DIM)
            background = GradientDrawable().apply {
                setColor(Colors.CARD)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Colors.HAIRLINE)
            }
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isSingleLine = false
            setHorizontallyScrolling(true)
            setSelection(0)
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }

        actions.addView(
            action("Save", filled = true) { save() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) },
        )
        actions.addView(
            action("Reset") {
                store.reset()
                editor.setText(store.raw())
                editor.setSelection(0)
                toast("Routine reset to default.")
            },
            LinearLayout.LayoutParams(0, dp(48), 1f),
        )
        root.addView(actions)

        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            // The keyboard counts as an inset here: without it the action row
            // disappears behind the IME on an edge-to-edge window.
            val (topInset, bottomInset) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                bars.top to bars.bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            view.setPadding(dp(18), dp(20) + topInset, dp(18), dp(10) + bottomInset)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { confirmDiscard() }
        }
    }

    private fun save() {
        val error = store.replaceFromRaw(editor.text.toString())
        if (error != null) {
            toast(error)
            return
        }
        TimekeeperService.start(this)
        toast("Saved.")
        finish()
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        confirmDiscard()
    }

    private fun confirmDiscard() {
        if (editor.text.toString() == store.raw()) {
            finish()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("The routine was not saved.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    private fun action(text: String, filled: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            setTextColor(if (filled) Colors.BG else Colors.TEXT_DIM)
            background = if (filled) {
                RippleDrawable(
                    ColorStateList.valueOf(0x22000000),
                    GradientDrawable().apply {
                        setColor(Colors.ACCENT)
                        cornerRadius = dp(12).toFloat()
                    },
                    null,
                )
            } else {
                RippleDrawable(
                    ColorStateList.valueOf(0x24FFFFFF),
                    GradientDrawable().apply {
                        setColor(Colors.CARD)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), Colors.HAIRLINE)
                    },
                    null,
                )
            }
            setOnClickListener {
                window.decorView.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                )
                onClick()
            }
        }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object Colors {
        val BG get() = dev.icarus.app.ui.IcarusColors.BG
        val CARD get() = dev.icarus.app.ui.IcarusColors.CARD
        val HAIRLINE get() = dev.icarus.app.ui.IcarusColors.HAIRLINE
        val TEXT_DIM get() = dev.icarus.app.ui.IcarusColors.TEXT_DIM
        val MUTED get() = dev.icarus.app.ui.IcarusColors.MUTED
        val ACCENT get() = dev.icarus.app.ui.IcarusColors.ACCENT
    }
}
