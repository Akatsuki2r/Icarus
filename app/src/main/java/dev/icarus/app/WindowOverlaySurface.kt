package dev.icarus.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import dev.icarus.app.ui.InterventionOverlayView

/** TYPE_APPLICATION_OVERLAY full-screen window with auto-dismiss and call escape. */
class WindowOverlaySurface(
    private val context: Context,
    private val callMonitor: CallMonitor,
    private val onSnooze: () -> Unit,
    private val onAcknowledge: () -> Unit,
    private val onExit: () -> Unit,
    private val onFallback: (InterventionRequest) -> Unit,
    private val titleFor: (String) -> String,
) : OverlaySurface {

    private val manager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: android.view.View? = null
    private var dismissAt = 0L

    private val autoDismiss = Runnable {
        if (view != null && System.currentTimeMillis() >= dismissAt) dismiss()
    }

    private val callPoll = object : Runnable {
        override fun run() {
            if (callMonitor.isActive()) {
                dismiss()
            } else if (view != null) {
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun canShow(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    override fun show(request: InterventionRequest, title: String) {
        dismiss()
        val overlay = InterventionOverlayView(
            context,
            request.kind,
            title,
            onSnooze = { onSnooze(); dismiss() },
            onAcknowledge = { onAcknowledge(); dismiss() },
            onExit = { onExit(); dismiss() },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.FILL }

        manager?.addView(overlay, params) ?: error("WindowManager unavailable")
        view = overlay
        dismissAt = System.currentTimeMillis() + if (request.kind == InterventionKind.LATE) 10_000L else 8_000L
        handler.postDelayed(autoDismiss, 1_000)
        handler.post(callPoll)
    }

    override fun dismiss() {
        view?.let { runCatching { manager?.removeView(it) } }
        view = null
        handler.removeCallbacks(autoDismiss)
        handler.removeCallbacks(callPoll)
    }

    override fun fallback(request: InterventionRequest) = onFallback(request)

    fun titleForBlock(id: String): String = titleFor(id)
}
