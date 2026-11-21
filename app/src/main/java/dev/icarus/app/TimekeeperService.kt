package dev.icarus.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.icarus.app.data.DayStateStore
import dev.icarus.app.data.SettingsStore
import dev.icarus.app.ui.IcarusColors
import dev.icarus.app.data.Classification
import dev.icarus.engine.Alert
import dev.icarus.engine.Moment
import dev.icarus.engine.NotifyPolicy
import java.time.LocalDate
import java.time.LocalTime

/**
 * The timekeeper itself: a low-importance foreground service that keeps the
 * current block in the shade, fires the three permitted alerts, and re-arms
 * the next wake-up.
 *
 * It is intentionally boring. All the thinking is in the engine.
 */
class TimekeeperService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notifier: Notifier
    private lateinit var awareness: AppUsageReader
    private lateinit var settings: SettingsStore
    private lateinit var callMonitor: CallMonitor
    private lateinit var overlay: OverlayGateway
    private var blockTitles: Map<String, String> = emptyMap()
    private val interventions = InterventionController()
    private var lastSignature: String? = null
    private var servingDate: LocalDate = LocalDate.now()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifier = Notifier(this)
        notifier.ensureChannels()
        awareness = AppUsageReader(this)
        settings = SettingsStore(this)
        callMonitor = CallMonitor(this)
        val surface = WindowOverlaySurface(
            this,
            callMonitor,
            { overlay.snooze() },
            { overlay.acknowledge() },
            { overlay.hardwareExit() },
            { notifier.intervention(it) },
        )
        overlay = OverlayGateway(surface) { id -> blockTitles[id] ?: id }
        overlay.onSnooze = { interventions.snooze() }
        overlay.onAcknowledge = { interventions.acknowledge() }
        overlay.onExit = { interventions.hardwareExit() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Contract.SERVICE_STOP) {
            handler.removeCallbacks(ticker)
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately; the OS gives us seconds, not minutes.
        val timeline = Actions.timeline(this)
        val now = LocalTime.now()
        val notification = notifier.live(timeline, timeline.moment(now), now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Contract.NOTIFICATION_LIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Contract.NOTIFICATION_LIVE, notification)
        }

        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::overlay.isInitialized) overlay.dismiss()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tick() {
        val today = LocalDate.now()
        if (today != servingDate) {
            // Midnight rollover: yesterday's alerts and plan are irrelevant.
            notifier.cancelAllAlerts()
            lastSignature = null
            interventions.resetDay()
            servingDate = today
        }

        val now = LocalTime.now()
        val timeline = Actions.timeline(this, today)
        val moment = timeline.moment(now)
        IcarusTheme.apply(this)

        deliverAlerts(today, now, timeline)
        evaluateInterventions(now, moment, timeline)

        val signature = signatureOf(moment, IcarusColors.ACCENT)
        if (signature != lastSignature) {
            notifier.ensureChannels()
            notifier.live(timeline, moment, now).let {
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(Contract.NOTIFICATION_LIVE, it)
            }
            lastSignature = signature
        }

        val tomorrow = today.plusDays(1)
        val wake = Alarms.nextWake(
            timeline,
            now,
            today,
            Actions.timeline(this, tomorrow),
        )
        if (wake != null) Alarms.scheduleNext(this, wake)
    }

    private fun deliverAlerts(date: LocalDate, now: LocalTime, timeline: dev.icarus.engine.Timeline) {
        val store = DayStateStore(this)
        val sent = store.sentAlerts(date)
        val due: List<Alert> = NotifyPolicy.due(now, timeline.activeBlocks(), sent)
        for (alert in due) {
            notifier.alert(alert, alert.title)
            store.markSent(date, alert.key)
        }
    }

    private fun evaluateInterventions(
        now: LocalTime,
        moment: Moment,
        timeline: dev.icarus.engine.Timeline,
    ) {
        val callActive = callMonitor.isActive()
        interventions.onCall(callActive)
        if (callActive) {
            overlay.onCall(true)
            interventions.clear()
            return
        }
        blockTitles = timeline.activeBlocks().associate { it.id to it.title }
        val classifications = settings.classifications()
        val foreground = awareness.foreground()
        val productive = classifications.filter { it.value == Classification.PRODUCTIVE }.keys.toSet()
        val signal = UsageSignal(
            foreground = foreground,
            dwellMillis = foreground?.let { awareness.dwellMillis(it) } ?: 0L,
            recentProductive = awareness.productiveSeenWithin(productive, 15 * 60 * 1000L),
            classifications = classifications,
            interventionsEnabled = (moment as? Moment.Active)?.block?.interventionEnabled ?: true,
            dwellLimitMinutes = (moment as? Moment.Active)?.block?.dwellLimitMinutes?.takeIf { it > 0 },
            productiveContext = (moment as? Moment.Active)?.block?.productiveContext ?: false,
        )
        val state = DayStateStore(this).load(LocalDate.now())
        interventions.onTick(now, moment, signal, state.completed, state.skipped)
        interventions.pending?.let { overlay.show(it) }
        interventions.clear()
    }

    private fun signatureOf(moment: Moment, accent: Int): String = when (moment) {
        is Moment.Active -> "a:${moment.block.id}:$accent"
        is Moment.Waiting -> "w:${moment.next?.id}:$accent"
        Moment.Finished -> "f:$accent"
    }

    companion object {
        private const val TICK_MS = 20_000L

        fun start(context: Context) {
            val intent = Intent(context, TimekeeperService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (_: IllegalStateException) {
                // Battery manager blocked the foreground start. Retry shortly so
                // the event that woke us is not skipped; the alarm is the backstop.
                Alarms.scheduleRetry(context)
            } catch (_: SecurityException) {
                Alarms.scheduleRetry(context)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimekeeperService::class.java).setAction(Contract.SERVICE_STOP),
            )
        }
    }
}
