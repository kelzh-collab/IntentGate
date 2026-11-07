package com.intentgate.app.data

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** The OS owns binding. Only package names from window state events are inspected. */
class IntentGateWindowService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: SessionExpiryRuntime
    private lateinit var settings: PreferencesAppSettingsStorage
    private lateinit var events: AndroidForegroundEventSource
    private lateinit var prompt: DesktopIntentOverlay
    private lateinit var notifications: IntentCheckNotifications
    private val entries = DesktopWindowEntries()
    private var checking: Job? = null
    private var cleanup: Job? = null
    private var promptKey: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!consented()) {
            disableSelf()
            return
        }
        runtime = SessionExpiryRuntime.get(this)
        settings = PreferencesAppSettingsStorage(this)
        events = AndroidForegroundEventSource(this, includeOtherResumes = true)
        prompt = DesktopIntentOverlay(this)
        notifications = IntentCheckNotifications(this)
        runtime.bindExpiryChecks(this, scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::runtime.isInitialized || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !consented()) return
        val book = runtime.sessions.state.value
        if (!runtime.sessions.intentCheckEnabled && book.active == null && book.pendingExpiry == null) {
            checking?.cancel()
            closePrompt()
            entries.transition(null)
            return
        }
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // All observed package names are transient. Other packages only indicate that a target was left.
        if (pkg == entries.current && checking?.isActive != true) return
        checking?.cancel()
        checking = scope.launch {
            // UsageEvents can arrive after the accessibility callback. Retry only this recent transition.
            repeat(3) { attempt ->
                delay(if (attempt == 0) 180L else 400L)
                val resume = readRecentForeground(pkg) ?: return@repeat
                if (resume.packageName != pkg) return@repeat
                if (entries.transition(pkg)) {
                    closePrompt()
                    runtime.observeBackground(this@IntentGateWindowService, pkg)
                    try { offerCheck(resume) } catch (_: RuntimeException) { closePrompt() }
                }
                return@launch
            }
        }
    }

    private suspend fun readRecentForeground(pkg: String): ForegroundEvent? = try {
        val now = System.currentTimeMillis()
        val batch = events.read(now - 5_000L, now, setOf(pkg))
        if (batch.status == DetectionStatus.Observing) latestForegroundResume(batch.events) else null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }

    private fun offerCheck(resume: ForegroundEvent) {
        val pkg = resume.packageName
        if (pkg == packageName || runtime.isVisible || !screenAvailable() || !managedCheckEnabled(pkg)) return
        val name = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { return }
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        if (!entries.canOffer(pkg, elapsed)) return
        val app = AppDefinition(pkg, name.ifBlank { pkg }, pkg, android.R.drawable.sym_def_app_icon, installed = true)
        val record = ForegroundDetectionRecord("desktop:$pkg", DetectionKind.Enter, pkg, pkg,
            resume.time, now, elapsed, now - resume.time, "Window transition", intentCheckEligible = true)
        val request = runtime.sessions.offer(record, app, hasUsageAccess(this), setOf(pkg)) ?: return
        entries.markOffered(pkg, elapsed)
        promptKey = request.key
        if (!prompt.show(app, onCancel = ::closePrompt, onStart = { purpose, minutes ->
            startInTarget(request, purpose, minutes)
        })) {
            promptKey = null
            notifications.post(request)
            return
        }
        cleanup = scope.launch {
            while (isActive) {
                withTimeoutOrNull(1_000L) { runtime.sessions.state.first { it.pending?.key != request.key } }
                if (runtime.isVisible || !consented() || !screenAvailable() || !hasUsageAccess(this@IntentGateWindowService) ||
                    System.currentTimeMillis() > request.expiresAt || !managedCheckEnabled(pkg) ||
                    runtime.sessions.state.value.pending?.key != request.key || !prompt.visible()) {
                    closePrompt()
                    break
                }
            }
        }
    }

    private fun startInTarget(request: IntentRequest, purpose: String, minutes: Int): Boolean {
        if (promptKey != request.key || !stillAllowed(request)) return false
        return try {
            val session = runtime.sessions.start(request.key, purpose, minutes, ::stillAllowed) ?: return false
            closePrompt()
            runtime.observeBackground(this, session.packageName)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun stillAllowed(request: IntentRequest): Boolean =
        !runtime.isVisible && entries.current == request.packageName && consented() && screenAvailable() &&
            hasUsageAccess(this) && managedCheckEnabled(request.packageName)

    private fun managedCheckEnabled(pkg: String): Boolean = try {
        runtime.sessions.intentCheckEnabled &&
            settings.read().orEmpty().any { it.appId == pkg && it.isManaged && it.intentCheckEnabled }
    } catch (_: RuntimeException) { false }

    private fun screenAvailable(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun consented(): Boolean = getSharedPreferences("gate_preferences", MODE_PRIVATE)
        .getBoolean("desktop_window_consent", false)

    private fun closePrompt() {
        cleanup?.cancel()
        cleanup = null
        val key = promptKey
        promptKey = null
        if (::prompt.isInitialized) prompt.dismiss()
        if (key != null && runtime.sessions.state.value.pending?.key == key) {
            try { runtime.sessions.cancel(key) } catch (_: RuntimeException) {}
        }
    }

    override fun onInterrupt() {
        checking?.cancel()
        closePrompt()
        if (::runtime.isInitialized) runtime.observeBackground(this, null)
        entries.transition(null)
    }

    override fun onDestroy() {
        scope.cancel()
        closePrompt()
        if (::runtime.isInitialized) runtime.stopObservingBackground(this)
        super.onDestroy()
    }
}

/** Page changes in a single app are not new entries. Brief returns share a prompt cooldown. */
internal class DesktopWindowEntries {
    var current: String? = null
        private set
    private val lastOffered = mutableMapOf<String, Long>()

    fun transition(pkg: String?): Boolean {
        if (current == pkg) return false
        current = pkg
        return true
    }

    fun canOffer(pkg: String, elapsed: Long): Boolean =
        lastOffered[pkg]?.let { elapsed - it >= 10_000L } ?: true

    fun markOffered(pkg: String, elapsed: Long) {
        lastOffered.entries.removeAll { elapsed - it.value >= 10_000L }
        lastOffered[pkg] = elapsed
    }
}
