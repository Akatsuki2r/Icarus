package dev.icarus.app.data

/** Every intervention threshold lives here so it can be tuned and tested in one place. */
object InterventionPolicy {
    const val LATE_AFTER_MIN = 1L
    const val SCROLL_BLOCK_ACTIVE_MIN = 2L
    const val CALL_GRACE_SEC = 90L
    const val SNOOZE_MIN = 5L
    const val ACKNOWLEDGE_MIN = 10L
    const val HARDWARE_EXIT_MIN = 2L
    const val AFTER_INTERVENTION_MIN = 10L
}
