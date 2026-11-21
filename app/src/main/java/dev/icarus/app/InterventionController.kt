package dev.icarus.app

import dev.icarus.app.data.AwarenessPolicy
import dev.icarus.app.data.Classification
import dev.icarus.app.data.InterventionPolicy
import dev.icarus.engine.Moment
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

enum class InterventionKind { LATE, STILL_SCROLLING }

data class InterventionRequest(val kind: InterventionKind, val blockId: String)

/** Pure signals from the awareness layer; no Android dependencies. */
data class UsageSignal(
    val foreground: String?,
    val dwellMillis: Long,
    val recentProductive: Boolean,
    val classifications: Map<String, Classification>,
)

/**
 * The only place that decides whether to interrupt. Every signal arrives here;
 * the service only executes the decision. No touch events are counted.
 */
class InterventionController(
    private val clock: () -> Pair<LocalTime, Instant> = { LocalTime.now() to Instant.now() },
) {
    var pending: InterventionRequest? = null
        private set

    private var callActive = false
    private var graceUntil: Instant? = null
    private var suppressUntil: Instant? = null
    private var lastBlockId: String? = null
    /** Each variant fires at most once per block per day; the two can coexist. */
    private val firedLate = mutableSetOf<String>()
    private val firedScroll = mutableSetOf<String>()

    fun onTick(
        now: LocalTime,
        moment: Moment,
        usage: UsageSignal,
        completed: Set<String> = emptySet(),
        skipped: Set<String> = emptySet(),
    ) {
        val instant = clock().second
        if (callActive) {
            pending = null
            return
        }
        if (suppressed(instant) || pending != null) return

        val block = (moment as? Moment.Active)?.block ?: return
        if (block.id in completed || block.id in skipped) return
        lastBlockId = block.id

        val late = block.id !in firedLate &&
            now.isAfter(block.start.plusMinutes(InterventionPolicy.LATE_AFTER_MIN))
        if (late) {
            issue(InterventionKind.LATE, block.id, instant)
            return
        }

        val dwellMinutes = usage.dwellMillis / 60_000L
        val kind = AwarenessPolicy.classify(usage.classifications, usage.foreground.orEmpty())
        val threshold = AwarenessPolicy.thresholdMinutes(kind, usage.recentProductive)
        val activeMinutes = Duration.between(block.start, now).toMinutes()
        if (dwellMinutes >= threshold &&
            block.id !in firedScroll &&
            activeMinutes >= InterventionPolicy.SCROLL_BLOCK_ACTIVE_MIN
        ) {
            issue(InterventionKind.STILL_SCROLLING, block.id, instant)
        }
    }

    fun onCall(active: Boolean) {
        if (active == callActive) return
        val instant = clock().second
        callActive = active
        if (active) {
            pending = null
            lastBlockId?.let { firedLate += it }
        } else {
            graceUntil = instant.plusSeconds(InterventionPolicy.CALL_GRACE_SEC)
        }
    }

    fun snooze() = suppress(InterventionPolicy.SNOOZE_MIN)

    fun acknowledge() = suppress(InterventionPolicy.ACKNOWLEDGE_MIN)

    fun hardwareExit() = suppress(InterventionPolicy.HARDWARE_EXIT_MIN)

    fun clear() {
        pending = null
    }

    /** Midnight rollover: a new day gets a fresh intervention budget. */
    fun resetDay() {
        pending = null
        graceUntil = null
        suppressUntil = null
        callActive = false
        firedLate.clear()
        firedScroll.clear()
    }

    fun shouldIntervene(now: LocalTime): Boolean = !suppressed(clock().second)

    private fun issue(kind: InterventionKind, blockId: String, instant: Instant) {
        pending = InterventionRequest(kind, blockId)
        if (kind == InterventionKind.LATE) firedLate += blockId else firedScroll += blockId
        suppressUntil = instant.plus(Duration.ofMinutes(InterventionPolicy.AFTER_INTERVENTION_MIN))
    }

    private fun suppress(minutes: Long) {
        pending = null
        suppressUntil = clock().second.plus(Duration.ofMinutes(minutes))
        lastBlockId?.let { firedLate += it }
    }

    private fun suppressed(instant: Instant): Boolean =
        callActive ||
            graceUntil?.let { instant.isBefore(it) } == true ||
            suppressUntil?.let { instant.isBefore(it) } == true
}
