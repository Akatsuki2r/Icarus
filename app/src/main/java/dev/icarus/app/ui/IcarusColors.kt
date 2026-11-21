package dev.icarus.app.ui

import dev.icarus.app.Tokens

/**
 * Views read these properties at render time, so changing the active token
 * set re-themes every screen without recreating Activities.
 */
object IcarusColors {

    private var current: Tokens = Tokens(
        0xFF0B0C0E.toInt(), 0xFF15181B.toInt(), 0xFF242A30.toInt(), 0xFF2C333A.toInt(),
        0xFFF2F3F5.toInt(), 0xFFC7CCD2.toInt(), 0xFF7B838D.toInt(), 0xFFF2B84B.toInt(),
        0xFF6FCF8A.toInt(), 0xFFE58D6F.toInt(),
    )

    fun update(tokens: Tokens) {
        current = tokens
    }

    fun current(): Tokens = current

    val BG get() = current.bg
    val CARD get() = current.surface
    val HAIRLINE get() = current.outline
    val TRACK get() = current.track
    val TEXT get() = current.text
    val TEXT_DIM get() = current.textDim
    val MUTED get() = current.muted
    val ACCENT get() = current.accent
    val GOOD get() = current.good
    val WARN get() = current.warn
}
