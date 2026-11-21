package dev.icarus.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import dev.icarus.app.ui.IcarusColors
import dev.icarus.engine.Block
import dev.icarus.engine.Priority
import java.time.LocalTime

/** A 24-hour band: filled segments are blocks, gaps are empty time. */
class DayRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var blocks: List<Block> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBlocks(next: List<Block>) {
        blocks = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        paint.color = IcarusColors.TRACK
        canvas.drawRect(0f, 0f, w, h, paint)

        blocks.forEach { block ->
            val left = fraction(block.start) * w
            val right = fraction(block.end) * w
            paint.color = when (block.priority) {
                Priority.ANCHOR -> IcarusColors.ACCENT
                Priority.FIRM -> IcarusColors.TEXT_DIM
                Priority.FLEX -> IcarusColors.MUTED
            }
            canvas.drawRect(left, 0f, right, h, paint)
        }
    }

    companion object {
        fun fraction(time: LocalTime): Float =
            (time.hour * 60 + time.minute) / 1440f
    }
}
