package com.intentgate.app.data

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.util.Log
import android.app.usage.UsageEvents
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.WindowManager
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import com.intentgate.app.R
import com.intentgate.app.loc
import kotlinx.coroutines.CancellationException

internal fun expiryTargetIsForeground(events: List<ForegroundEvent>, target: String): Boolean =
    latestForegroundResume(events)?.packageName == target

internal fun latestForegroundResume(events: List<ForegroundEvent>): ForegroundEvent? {
    var resumed: ForegroundEvent? = null
    for (event in events.sortedBy { it.time }) {
        when (event.type) {
            UsageEvents.Event.ACTIVITY_RESUMED -> resumed = event
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                val previous = resumed
                if (previous != null && previous.packageName == event.packageName &&
                    (previous.activity == event.activity || event.activity.isEmpty())) resumed = null
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> resumed = null
        }
    }
    return resumed
}

/** One short-lived full-screen confirmation, owned by the process rather than an Activity. */
internal class SessionExpiryOverlay(private val context: Context) {
    private val events = AndroidForegroundEventSource(context, includeOtherResumes = true)
    private var manager: WindowManager? = null
    private var view: LinearLayout? = null
    private var mount: ExpiryOverlayMount? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var accessibilityWindow = false
    val awaitingDisplay: Boolean get() = mount?.awaitingDisplay == true
    var sessionId: String? = null
        private set

    private fun unavailableReason(usingAccessibility: Boolean): String? = try {
        when {
            !usingAccessibility && !Settings.canDrawOverlays(context) -> "application_overlay_permission_missing"
            !context.getSystemService(PowerManager::class.java).isInteractive -> "screen_off"
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked -> "device_locked"
            else -> null
        }
    } catch (error: RuntimeException) { error.javaClass.simpleName }

    fun permitted(): Boolean = unavailableReason(accessibilityWindow) == null

    suspend fun targetInForeground(session: IntentSession): Boolean {
        if (!permitted()) return false
        return try {
            val now = System.currentTimeMillis()
            val start = maxOf(session.startedAt - 86_400_000L, now - 2 * 86_400_000L)
            val batch = events.read(start, now, setOf(session.packageName))
            batch.status == DetectionStatus.Observing && permitted() &&
                expiryTargetIsForeground(batch.events, session.packageName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            false
        }
    }

    fun visibilityProblem(): String? {
        val state = mount ?: return "window_missing"
        val failure = unavailableReason(accessibilityWindow) ?: state.failureReason()
        if (failure != null || state.awaitingDisplay) return failure
        return if (view?.windowVisibility != View.VISIBLE) "window_hidden" else null
    }

    fun show(session: IntentSession, service: AccessibilityService?, onContinue: (() -> Unit)?, onFinish: () -> Unit): Boolean {
        // The bound service supplies the same WindowManager and token as DesktopIntentOverlay.
        val reason = if (sessionId != null) "window_already_open" else unavailableReason(service != null)
        if (reason != null) {
            Log.w(EXPIRY_LOG, "overlay_failed reason=" + reason)
            return false
        }
        return try {
            val type = if (service != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val windowContext = service ?: if (Build.VERSION.SDK_INT >= 30) {
                val display = context.getSystemService(DisplayManager::class.java)
                    .getDisplay(Display.DEFAULT_DISPLAY) ?: run {
                        Log.w(EXPIRY_LOG, "overlay_failed reason=no_display")
                        return false
                    }
                context.createDisplayContext(display).createWindowContext(type, null)
            } else context
            val wm = windowContext.getSystemService(WindowManager::class.java)
            val themed = ContextThemeWrapper(windowContext, android.R.style.Theme_Material_Light)
            val card = FullScreenConfirmation(themed) { dismiss("user_back") }.apply {
                header(session.appName, session.packageName)
                label(loc(R.string.ig_session_expired_title, session.appName), prominent = true)
                label(loc(R.string.ig_continue_question))
                if (onContinue != null) action(loc(R.string.ig_continue), primary = true, onClick = onContinue)
                else label(loc(R.string.ig_intent_check_disabled_expiry))
                action(loc(R.string.ig_finish_use), primary = false, onClick = onFinish)
            }
            val params = fullScreenConfirmationParams(type, "IntentGate session expiry")
            val state = ExpiryOverlayMount(SystemClock::elapsedRealtime)
            val attachment = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attachedView: View) {
                    if (view === card && state.attached()) {
                        Log.i(EXPIRY_LOG, "overlay_attach_callback phase=" + state.phase)
                    }
                }

                override fun onViewDetachedFromWindow(detachedView: View) {
                    state.detached()
                    Log.i(EXPIRY_LOG, "overlay_detach_callback phase=" + state.phase + " reason=" + state.failureReason())
                }
            }
            val drawing = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (view === card && card.isShown && card.windowVisibility == View.VISIBLE && state.drawn()) {
                        Log.i(EXPIRY_LOG, "overlay_visible phase=" + state.phase)
                        // Removing an OnDrawListener from inside onDraw is prohibited.
                        card.post {
                            if (card.viewTreeObserver.isAlive) card.viewTreeObserver.removeOnDrawListener(this)
                        }
                    }
                }
            }
            manager = wm
            view = card
            mount = state
            attachListener = attachment
            drawListener = drawing
            accessibilityWindow = service != null
            sessionId = session.id
            card.addOnAttachStateChangeListener(attachment)
            card.viewTreeObserver.addOnDrawListener(drawing)
            Log.i(EXPIRY_LOG, "overlay_add_request phase=" + state.phase + " windowType=" + type)
            wm.addView(card, params)
            Log.i(EXPIRY_LOG, "overlay_submitted phase=" + state.phase + " windowType=" + type)
            true
        } catch (error: RuntimeException) {
            val route = if (service != null) "accessibility" else "application"
            Log.w(EXPIRY_LOG, "overlay_failed route=" + route + " reason=" + error.javaClass.simpleName)
            dismiss("add_view_exception:" + error.javaClass.simpleName)
            false
        }
    }

    fun dismiss(reason: String) {
        val card = view ?: return
        val wm = manager
        val state = mount
        val phase = state?.phase
        state?.close(reason)
        // Clear ownership before removeView so reentrant callbacks cannot remove a second time.
        view = null
        manager = null
        sessionId = null
        mount = null
        accessibilityWindow = false
        val drawing = drawListener
        val attachment = attachListener
        drawListener = null
        attachListener = null
        try {
            if (drawing != null && card.viewTreeObserver.isAlive) card.viewTreeObserver.removeOnDrawListener(drawing)
            Log.i(EXPIRY_LOG, "overlay_remove_request from=" + phase +
                " reason=" + reason + " caller=SessionExpiryOverlay.dismiss")
            wm?.removeViewImmediate(card)
        } catch (error: RuntimeException) {
            Log.w(EXPIRY_LOG, "overlay_remove_failed reason=" + error.javaClass.simpleName)
        } finally {
            if (attachment != null) card.removeOnAttachStateChangeListener(attachment)
        }
    }
}
