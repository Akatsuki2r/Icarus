package dev.icarus.app

object Contract {

    const val SERVICE_TICK = "dev.icarus.app.TICK"
    const val SERVICE_STOP = "dev.icarus.app.STOP"

    const val ACTION_STARTED = "dev.icarus.app.STARTED"
    const val ACTION_LATE = "dev.icarus.app.LATE"
    const val ACTION_SKIP = "dev.icarus.app.SKIP"
    const val ACTION_DEFER = "dev.icarus.app.DEFER"
    const val ACTION_RECOVER = "dev.icarus.app.RECOVER"

    const val EXTRA_BLOCK = "block_id"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_DAY = "day"

    const val CHANNEL_LIVE = "live"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_PREPARE = "alert.prepare"
    const val CHANNEL_START = "alert.start"
    const val CHANNEL_OVERDUE = "alert.overdue"

    const val NOTIFICATION_LIVE = 1001
    const val ALARM_REQUEST = 2001
}
