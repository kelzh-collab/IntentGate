package com.intentgate.app.data

internal enum class ExpiryOverlayPhase { PENDING_ATTACH, ATTACHED, VISIBLE, CLOSED }

/** Window submission is asynchronous. Only actual callbacks advance attachment/display state. */
internal class ExpiryOverlayMount(private val now: () -> Long) {
    var phase = ExpiryOverlayPhase.PENDING_ATTACH
        private set
    private val submittedAt = now()
    private var attachedAt = 0L
    private var visibleAt = 0L
    private var closedReason: String? = null

    val awaitingDisplay: Boolean
        get() = phase == ExpiryOverlayPhase.PENDING_ATTACH || phase == ExpiryOverlayPhase.ATTACHED

    fun attached(): Boolean {
        if (phase != ExpiryOverlayPhase.PENDING_ATTACH) return false
        attachedAt = now()
        phase = ExpiryOverlayPhase.ATTACHED
        return true
    }

    fun drawn(): Boolean {
        if (phase != ExpiryOverlayPhase.ATTACHED) return false
        visibleAt = now()
        phase = ExpiryOverlayPhase.VISIBLE
        return true
    }

    fun detached() {
        if (phase != ExpiryOverlayPhase.CLOSED) close("unexpected_detach")
    }

    fun close(reason: String): Boolean {
        if (phase == ExpiryOverlayPhase.CLOSED) return false
        closedReason = reason
        phase = ExpiryOverlayPhase.CLOSED
        return true
    }

    fun failureReason(): String? = when (phase) {
        ExpiryOverlayPhase.PENDING_ATTACH -> if (now() - submittedAt >= 5_000L) "attach_timeout" else null
        ExpiryOverlayPhase.ATTACHED -> if (now() - attachedAt >= 5_000L) "first_draw_timeout" else null
        ExpiryOverlayPhase.VISIBLE -> if (now() - visibleAt >= 20_000L) "window_timeout" else null
        ExpiryOverlayPhase.CLOSED -> closedReason
    }
}
