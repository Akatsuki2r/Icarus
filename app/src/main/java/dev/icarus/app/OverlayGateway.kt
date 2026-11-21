package dev.icarus.app

/** Android-free seam so the decision to fall back is unit-testable. */
interface OverlaySurface {
    fun canShow(): Boolean
    fun show(request: InterventionRequest, title: String)
    fun dismiss()
    fun fallback(request: InterventionRequest)
}

/**
 * Shows an intervention or falls back to a notification. All dismissal paths
 * (snooze, acknowledge, hardware exit, call) go through here.
 */
class OverlayGateway(
    private val surface: OverlaySurface,
    private val titleFor: (String) -> String = { "" },
) {
    var onSnooze: () -> Unit = {}
    var onAcknowledge: () -> Unit = {}
    var onExit: () -> Unit = {}

    private var visible = false

    fun show(request: InterventionRequest) {
        if (!surface.canShow()) {
            surface.fallback(request)
            return
        }
        visible = true
        IcarusTheme.overlayActive = true
        try {
            surface.show(request, titleFor(request.blockId))
        } catch (_: Exception) {
            visible = false
            IcarusTheme.overlayActive = false
            surface.fallback(request)
        }
    }

    fun dismiss() {
        visible = false
        IcarusTheme.overlayActive = false
        surface.dismiss()
    }

    fun onCall(active: Boolean) {
        if (active) dismiss()
    }

    fun snooze() {
        dismiss()
        onSnooze()
    }

    fun acknowledge() {
        dismiss()
        onAcknowledge()
    }

    fun hardwareExit() {
        dismiss()
        onExit()
    }

    fun hasVisibleOverlay(): Boolean = visible
}
