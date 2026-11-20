package dev.icarus.engine

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AlertKind { PREPARE, START, OVERDUE }

data class Alert(
    val key: String,
    val kind: AlertKind,
    val at: LocalTime,
    val blockId: String,
    val title: String,
    val text: String,
) {
    val urgent: Boolean get() = kind != AlertKind.PREPARE
}

/**
 * The notification policy, deliberately small.
 *
 * Three touches per block, at most:
 *   PREPARE  ten minutes before  - "wrap up, this is next"
 *   START    on the transition   - "this is now"
 *   OVERDUE  ten minutes late    - one question, then silence
 *
 * No repeating reminders, no streaks, no guilt. If you ignore the overdue
 * nudge the system assumes you have made a decision and waits for you.
 */
object NotifyPolicy {

    val PREPARE_LEAD: Duration = Duration.ofMinutes(10)
    val OVERDUE_AFTER: Duration = Duration.ofMinutes(10)

    /** Ignore alerts older than this; a phone that was off should not dump a backlog. */
    val STALE_AFTER: Duration = Duration.ofMinutes(3)

    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun schedule(
        blocks: List<Block>,
        prepareLead: Duration = PREPARE_LEAD,
        overdueAfter: Duration = OVERDUE_AFTER,
    ): List<Alert> = buildList {
        for (block in blocks) {
            val prepareAt = block.start.minus(prepareLead)
            if (block.duration >= Duration.ofMinutes(20) && block.interrupt != Interrupt.SILENT) {
                add(
                    Alert(
                        key = "${block.id}:prepare",
                        kind = AlertKind.PREPARE,
                        at = prepareAt,
                        blockId = block.id,
                        title = "${prepareLead.toMinutes()} min to ${block.title}",
                        text = "${block.start.format(clock)}–${block.end.format(clock)}. Start wrapping up.",
                    ),
                )
            }

            add(
                Alert(
                    key = "${block.id}:start",
                    kind = AlertKind.START,
                    at = block.start,
                    blockId = block.id,
                    title = "${block.title} starts now",
                    text = "Until ${block.end.format(clock)} · ${block.duration.toMinutes()}m",
                ),
            )

            if (block.interrupt == Interrupt.STRICT) {
                add(
                    Alert(
                        key = "${block.id}:overdue",
                        kind = AlertKind.OVERDUE,
                        at = block.start.plus(overdueAfter),
                        blockId = block.id,
                        title = "${block.title} hasn't started",
                        text = "Started · Running late · Skip it",
                    ),
                )
            }
        }
    }

    /**
     * Alerts that should fire at [now] and have not been sent yet.
     * [sent] holds previously delivered keys.
     */
    fun due(
        now: LocalTime,
        blocks: List<Block>,
        sent: Set<String>,
        staleAfter: Duration = STALE_AFTER,
    ): List<Alert> = schedule(blocks)
        .filter { alert ->
            alert.key !in sent &&
                !alert.at.isAfter(now) &&
                Duration.between(alert.at, now) <= staleAfter
        }
        .sortedBy { it.at }

    /** The next scheduled alert strictly after [now] - used to set the alarm. */
    fun next(now: LocalTime, blocks: List<Block>): Alert? =
        schedule(blocks).filter { it.at.isAfter(now) }.minByOrNull { it.at }
}
