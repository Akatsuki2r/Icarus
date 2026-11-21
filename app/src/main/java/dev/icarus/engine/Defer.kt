package dev.icarus.engine

import java.time.LocalTime

/**
 * A short defer ("not now, +15") applied to one block and the blocks that
 * follow it. Anchors never move; a later block is trimmed rather than allowed
 * to cross an anchor. Returns null when the request cannot be honoured.
 */
object Defer {

    fun apply(blocks: List<Block>, id: String, minutes: Long): List<Block>? {
        if (minutes <= 0) return null
        val sorted = blocks.sortedBy { it.start }
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0) return null

        val targetEnd = minuteOf(sorted[index].end)
        val nextAnchor = sorted.drop(index + 1).firstOrNull { it.priority == Priority.ANCHOR }
        val room = (nextAnchor?.start?.let(::minuteOf) ?: LAST_MINUTE) - targetEnd
        val shift = minOf(minutes, room.toLong())
        if (shift <= 0) return null

        return sorted.mapIndexed { idx, block ->
            when {
                idx < index -> block
                idx > index && block.priority == Priority.ANCHOR -> block
                else -> {
                    val start = (minuteOf(block.start) + shift).toInt().coerceAtMost(LAST_MINUTE)
                    val anchorAt = sorted.drop(idx + 1)
                        .firstOrNull { it.priority == Priority.ANCHOR }
                        ?.start
                        ?.let(::minuteOf)
                    val end = (minuteOf(block.end) + shift).toInt().coerceAtMost(LAST_MINUTE)
                    val capped = if (anchorAt != null) minOf(end, anchorAt) else end
                    val finalEnd = if (capped <= start) (start + 1).coerceAtMost(LAST_MINUTE) else capped
                    block.copy(start = timeOf(start), end = timeOf(finalEnd))
                }
            }
        }
    }

    private const val LAST_MINUTE = 1439
    private fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute
    private fun timeOf(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)
}
