package dev.icarus.app.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import dev.icarus.app.Tokens

/** Views read these at render time, so a token change re-themes every surface. */
object IcarusColors {

    private var current: Tokens = Tokens(
        0xFF10140F.toInt(), 0xFF171C15.toInt(), 0xFF1F261C.toInt(), 0xFF2E3828.toInt(),
        0xFF262E22.toInt(), 0xFFEDF0E6.toInt(), 0xFFC3CBB8.toInt(), 0xFF94A089.toInt(),
        0xFFB7C98A.toInt(), 0xFF6E7F52.toInt(), 0xFF9BC98A.toInt(), 0xFFD9A07A.toInt(),
    )

    fun update(tokens: Tokens) { current = tokens }
    fun current(): Tokens = current

    val BG get() = current.bg
    val CARD get() = current.surface
    val RAISED get() = current.raised
    val HAIRLINE get() = current.outline
    val TRACK get() = current.track
    val TEXT get() = current.text
    val TEXT_DIM get() = current.textDim
    val MUTED get() = current.muted
    val ACCENT get() = current.accent
    val ACCENT_SOFT get() = current.accentSoft
    val GOOD get() = current.good
    val WARN get() = current.warn

    /** Three stepped surfaces plus a same-hue top edge give depth without decoration. */
    fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(IcarusColors.current().surface)
        cornerRadius = 18f
        setStroke(1, IcarusColors.current().outline)
    }

    fun ripple(fill: Int = IcarusColors.current().surface, tint: Int = 0x24FFFFFF): android.graphics.drawable.RippleDrawable =
        android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(tint), GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 14f
        }, null)
}
