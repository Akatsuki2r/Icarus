package dev.icarus.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(Color.parseColor("#0B0C0E"))
        }

        root.addView(
            TextView(this).apply {
                text = "ROUTINE"
                setTextColor(Color.parseColor("#F2B84B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                letterSpacing = 0.28f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            },
        )

        root.addView(
            TextView(this).apply {
                text = "Times are 24h. priority: ANCHOR (never moves), FIRM, FLEX.\n" +
                    "interrupt: SILENT, NUDGE, STRICT (STRICT adds an overdue check)."
                setTextColor(Color.parseColor("#878D95"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(6), 0, dp(12))
            },
        )

        editor = EditText(this).apply {
            setText(store.raw())
            setTextColor(Color.parseColor("#DDE1E6"))
            setBackgroundColor(Color.parseColor("#101315"))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isSingleLine = false
            setHorizontallyScrolling(true)
        }
        root.addView(
            editor,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        actions.addView(
            Button(this).apply {
                text = "Save"
                setOnClickListener { save() }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            Button(this).apply {
                text = "Reset"
                setOnClickListener {
                    store.reset()
                    editor.setText(store.raw())
                    toast("Routine reset to default.")
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(actions)

        setContentView(root)
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

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
