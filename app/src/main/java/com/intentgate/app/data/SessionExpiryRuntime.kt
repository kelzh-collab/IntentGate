package com.intentgate.app.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.intentgate.app.R
import com.intentgate.app.loc
import android.content.Intent
import android.os.SystemClock
import com.intentgate.app.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.intentgate.app.session.SessionStatus
import java.time.LocalDate

internal const val INTENT_CHECK_ENABLED_KEY = "intent"

/** Reuse the real daily-limit projection, including today's explicit extensions. Never grant extra time here. */
fun canContinueWithinLimit(state:DailyLimitState?,minutes:Int,today:LocalDate):Boolean =
    minutes in 1..1440 && state!=null && state.date==today && state.managed &&
        state.availability==LimitAvailability.Ready && state.remainingSeconds?.let {minutes*60L<=it}==true

/** Verifies actual UsageEvents when awakened by a window transition or a candidate deadline. */
class SessionExpiryRuntime private constructor(context:Context) {
    private val preferences = context.applicationContext.getSharedPreferences("gate_preferences", Context.MODE_PRIVATE)
    val sessions = IntentSessionRepository(
        PreferencesIntentSessionStorage(context.applicationContext),
        checksEnabled = { preferences.getBoolean(INTENT_CHECK_ENABLED_KEY, true) },
    )
    private val timing=AndroidIntentSessionTiming(context.applicationContext,sessions)
    private val notifications=IntentCheckNotifications(context.applicationContext)
    private val visible=mutableSetOf<Any>()
    private val appContext = context.applicationContext
    private val overlay = SessionExpiryOverlay(appContext)
    private var continueTap: Pair<String, Long>? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var observer: Any? = null
    private var observedPackage: String? = null
    private var boundService: AccessibilityService? = null
    private var checks: Job? = null
    val isVisible: Boolean get() = visible.isNotEmpty()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == INTENT_CHECK_ENABLED_KEY) reconcileIntentCheckPreference()
    }

    private fun reconcileIntentCheckPreference() {
        if (!sessions.intentCheckEnabled) {
            continueTap = null
            notifications.dismiss()
            sessions.expireAndValidate { true }
        }
        wake.trySend(Unit)
    }

    fun bindExpiryChecks(service: AccessibilityService, serviceScope: CoroutineScope) {
        boundService = service
        observeBackground(service, null)
        startChecks(serviceScope)
        Log.i(EXPIRY_LOG, "scheduler owner=accessibility_service")
    }

    fun observeBackground(owner: Any, packageName: String?) {
        observer = owner
        observedPackage = packageName
        wake.trySend(Unit)
    }

    fun stopObservingBackground(owner: Any) {
        if (observer !== owner) return
        observer = null
        observedPackage = null
        if (boundService === owner) {
            boundService = null
            overlay.dismiss("service_disconnected")
            startChecks(scope)
            Log.i(EXPIRY_LOG, "scheduler owner=process_fallback")
        }
        wake.trySend(Unit)
    }

    private fun nextCheckDelay(): Long {
        if (observer == null || isVisible || overlay.sessionId != null || freshContinueTap() != null ||
            sessions.state.value.pendingExpiry != null) return 2_000L
        return backgroundExpiryDelay(sessions.state.value.sessions, observedPackage)
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        reconcileIntentCheckPreference()
        scope.launch {
            var runningIds = emptySet<String>()
            sessions.state.map { book ->
                book.sessions.filter { it.status != SessionStatus.Ended && it.origin != IntentOrigin.Simulated }
                    .map { Triple(it.id, it.status, it.windows.size) }
            }.distinctUntilChanged().collect { active ->
                val nextRunningIds = active.filter { it.second == SessionStatus.Running }.map { it.first }.toSet()
                if (nextRunningIds.any { it !in runningIds }) Log.i(EXPIRY_LOG, "session_running")
                runningIds = nextRunningIds
                wake.trySend(Unit)
            }
        }
        startChecks(scope)
    }

    private fun startChecks(ownerScope: CoroutineScope) {
        checks?.cancel()
        checks = ownerScope.launch { while(isActive) {
            sessions.state.first {book -> book.sessions.any {(it.origin!=IntentOrigin.Simulated && it.status!=SessionStatus.Ended &&
                (it.status==SessionStatus.Running || it.windows.any {w -> w.endAt!=null && w.verifiedThrough<w.endAt})) ||
                (it.expiryDecision==ExpiryDecision.Pending && !it.expiryNotified)} }
            try {
                val before = sessions.state.value.sessions
                    .filter { it.status != SessionStatus.Ended && it.origin != IntentOrigin.Simulated }
                    .mapTo(mutableSetOf()) { it.id }
                val verifiedForeground = timing.refresh()
                reconcileOverlay(before, verifiedForeground)
                notifyPending()
            }
            catch(cancelled:CancellationException) {throw cancelled}
            catch(error:RuntimeException) { Log.w(EXPIRY_LOG, "check_failed type=" + error.javaClass.simpleName) }
            withTimeoutOrNull(nextCheckDelay()) { wake.receive() }
        } }
    }
    fun setVisible(owner: Any, value: Boolean) {
        wake.trySend(Unit)
        if (value) {
            visible.add(owner)
            overlay.dismiss("intentgate_visible")
        } else {
            visible.remove(owner)
            notifyPending()
        }
    }

    private fun pendingExpiry(id: String): IntentSession? = sessions.state.value.sessions.firstOrNull {
        it.id == id && it.expiryDecision == ExpiryDecision.Pending && !it.expiryNotified &&
            it.origin != IntentOrigin.Simulated && it.status == SessionStatus.Ended && it.remainingMillis == 0L
    }

    private suspend fun reconcileOverlay(previouslyActive: Set<String>, verifiedForeground: String?) {
        val expired = sessions.state.value.sessions.filter {
            it.id in previouslyActive && it.expiryDecision == ExpiryDecision.Pending && !it.expiryNotified
        }
        val showing = overlay.sessionId
        if (showing != null) {
            val session = pendingExpiry(showing)
            val reason = when {
                isVisible || session == null -> "confirmation_moved_or_resolved"
                else -> overlay.visibilityProblem() ?: if (overlay.awaitingDisplay) null
                    else if (!overlay.targetInForeground(session)) "target_left_foreground" else null
            }
            if (reason != null) {
                overlay.dismiss("reconcile:" + reason)
                Log.i(EXPIRY_LOG, "overlay_closed reason=" + reason)
            }
        }
        if (expired.isEmpty()) return
        val candidate = expired.firstOrNull { it.packageName == verifiedForeground }
        if (isVisible || candidate == null || overlay.sessionId != null) {
            val reason = when {
                isVisible -> "intentgate_visible"
                verifiedForeground == null -> "foreground_evidence_unavailable"
                candidate == null -> "foreground_package_mismatch"
                else -> "another_overlay_visible"
            }
            Log.i(EXPIRY_LOG, "overlay_skipped reason=" + reason)
            return
        }
        val current = pendingExpiry(candidate.id) ?: return
        val onContinue: (() -> Unit)? =
            if (sessions.intentCheckEnabled) ({ continueFromOverlay(current.id) }) else null
        overlay.show(current, boundService, onContinue = onContinue,
            onFinish = { finishFromOverlay(current.id) })
    }

    private fun freshContinueTap(): String? {
        val tap = continueTap ?: return null
        if (SystemClock.elapsedRealtime() - tap.second < 10_000L) return tap.first
        continueTap = null
        return null
    }

    fun takeOverlayContinuation(): String? = freshContinueTap().also { continueTap = null }

    private fun continueFromOverlay(id: String) {
        if (overlay.sessionId != id || !sessions.intentCheckEnabled) return
        if (overlay.permitted() && pendingExpiry(id) != null) {
            continueTap = id to SystemClock.elapsedRealtime()
            try {
                // Only a user tap on the visible overlay can request this navigation.
                (boundService ?: appContext).startActivity(Intent(appContext, MainActivity::class.java)
                    .putExtra("expired_session", id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (_: RuntimeException) {
                continueTap = null
            }
        }
        overlay.dismiss("user_continue")
        notifyPending()
    }

    private fun finishFromOverlay(id: String) {
        if (overlay.sessionId != id) return
        val finished = try {
            pendingExpiry(id) != null && sessions.finishExpired(id)
        } catch (error: RuntimeException) {
            Log.w(EXPIRY_LOG, "finish_save_failed reason=" + error.javaClass.simpleName)
            false
        }
        overlay.dismiss("user_finish")
        if (!finished) return

        try {
            notifications.dismissExpiry(id)
        } catch (error: RuntimeException) {
            Log.w(EXPIRY_LOG, "finish_notification_cleanup_failed reason=" + error.javaClass.simpleName)
        }
        // This action is reachable only from the overlay's Finish button, after a durable finish.
        val wentHome = try {
            boundService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        } catch (error: RuntimeException) {
            Log.w(EXPIRY_LOG, "finish_home_failed reason=" + error.javaClass.simpleName)
            false
        }
        if (!wentHome) {
            Log.i(EXPIRY_LOG, "finish_home_unavailable")
            Toast.makeText(appContext, loc(R.string.ig_finish_return_home_manually), Toast.LENGTH_LONG).show()
        }
    }

    private fun notifyPending() {
        if (visible.isNotEmpty() || !notifications.canPostExpiry()) return
        val continuing = freshContinueTap()
        sessions.state.value.sessions.filter {
            it.expiryDecision == ExpiryDecision.Pending && !it.expiryNotified &&
                it.id != overlay.sessionId && it.id != continuing
        }.forEach {
            if (sessions.deliverExpiry(it.id, notifications::postExpiry))
                Log.i(EXPIRY_LOG, "notification_posted")
        }
    }

    fun showExpiry(id:String) {
        if (!presentExpiryInForeground(sessions, id, isVisible,
                dismissOverlay = { if (overlay.sessionId == id) overlay.dismiss("foreground_confirmation") },
                dismissNotification = { notifications.dismissExpiry(id) })) {
            Log.i(EXPIRY_LOG, "foreground_confirmation_skipped reason=activity_not_visible")
        }
    }
    fun dismissExpiry(id:String) {
        if (overlay.sessionId == id) overlay.dismiss("decision_navigation")
        notifications.dismissExpiry(id)
    }
    companion object {
        @Volatile private var shared:SessionExpiryRuntime?=null
        fun get(context:Context):SessionExpiryRuntime=shared ?: synchronized(this) {
            shared ?: SessionExpiryRuntime(context.applicationContext).also {shared=it}
        }
    }
}

/** A deadline only wakes verification. Leaving the target removes its deadline until a new entry. */
internal fun sessionCheckDelay(sessions: List<IntentSession>, foregroundPackage: String?): Long =
    sessions.asSequence().filter {
        it.status == SessionStatus.Running && it.origin != IntentOrigin.Simulated &&
            it.packageName == foregroundPackage
    }.minOfOrNull { it.remainingMillis.coerceIn(1_000L, 30_000L) } ?: 30_000L

/** Bound service rechecks even without another window event or a usable foreground hint. */
internal fun backgroundExpiryDelay(sessions: List<IntentSession>, foregroundPackage: String?): Long =
    sessionCheckDelay(sessions, foregroundPackage).coerceAtMost(2_000L)

/** A stopped Activity may still recompose; it must not consume a background reminder. */
internal fun presentExpiryInForeground(
    sessions: IntentSessionRepository,
    id: String,
    isVisible: Boolean,
    dismissOverlay: () -> Unit,
    dismissNotification: () -> Unit
): Boolean {
    if (!isVisible) return false
    dismissOverlay()
    sessions.acknowledgeExpiry(id)
    dismissNotification()
    return true
}
