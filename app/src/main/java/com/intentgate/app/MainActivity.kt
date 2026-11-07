package com.intentgate.app

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.intentgate.app.ui.GateText
import com.intentgate.app.ui.GateColors
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.intentgate.app.data.*
import com.intentgate.app.ui.IntentGateApp
import com.intentgate.app.ui.IntentCheckScreen
import com.intentgate.app.ui.SessionExpiredScreen
import com.intentgate.app.ui.theme.IntentGateTheme

class MainActivity:ComponentActivity() {
    private var monitorJob:Job?=null
    private var catalogJob:Job?=null
    var foregroundMonitor:ManagedForegroundMonitor?=null
        private set
    private lateinit var usage:UsageDataSource
    private lateinit var dailyLimits:DailyLimitRepository
    private lateinit var limitNotifications:AndroidLimitReminderSink
    private var requestedLimitApp by mutableStateOf<String?>(null)
    private var pendingUsageRefreshAt=0L
    private var usageZone=java.time.ZoneId.systemDefault().id
    private fun requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && !notifications.allowed()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
    private fun syncLimits() {
        if(::dailyLimits.isInitialized) dailyLimits.refresh(usage.today.value,repository.apps.value,repository.catalog,hasUsageAccess(applicationContext))
    }
    private fun readLimitNavigation(intent:Intent) {
        requestedLimitApp=intent.getStringExtra("limit_app")?.let(AppIdentity::packageId)?.takeIf { id -> repository.catalog.any { it.id==id } }
    }
    private lateinit var repository:AppRepository
    lateinit var intentSessions:IntentSessionRepository
        private set
    private lateinit var sessionRuntime:SessionExpiryRuntime
    private var finishedUse by mutableStateOf(false)
    private var requestedExpiredId by mutableStateOf<String?>(null)
    private lateinit var launchCoordinator:IntentLaunchCoordinator
    private var launchFailed by mutableStateOf(false)
    private lateinit var notifications:IntentCheckNotifications
    private var notificationAllowed by mutableStateOf(false)
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationAllowed=notifications.allowed();syncLimits() }
    // Resolve saved app identities before judging restored requests. Catalog loading is not an uninstall.
    private fun validateIntentRequests() { if(repository.catalogReady) intentSessions.expireAndValidate(::allowed) }
    private fun allowed(request:IntentRequest)=launchCoordinator.isAllowed(request)
    private fun requestAppLaunch(id:String) {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        launchFailed=false;syncLimits()
        try {
            when(launchCoordinator.request(id)) {
                AppLaunchResult.Unavailable,AppLaunchResult.LaunchFailed -> launchFailed=true
                else -> notifications.dismiss()
            }
            lifecycleScope.launch {usage.refresh();syncLimits()}
        } catch(_:RuntimeException) {launchFailed=true}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        val frozen=applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0 && intent.getBooleanExtra("previewFrozen",false)
        repository=createAndroidAppRepository(applicationContext)
        usage=if(frozen) MockUsageDataSource() else AndroidUsageDataSource(applicationContext,repository)
        sessionRuntime=SessionExpiryRuntime.get(applicationContext)
        intentSessions=sessionRuntime.sessions
        requestedExpiredId=intent.getStringExtra("expired_session")
        launchCoordinator=IntentLaunchCoordinator(repository,intentSessions,TargetAppLauncher { pkg ->
            if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
            else try {
                val launch=packageManager.getLaunchIntentForPackage(pkg)
                if(launch==null) false else {startActivity(launch);true}
            } catch(_:RuntimeException) {false}
        }) {hasUsageAccess(applicationContext)}
        notifications=IntentCheckNotifications(this)
        notificationAllowed=notifications.allowed()
        limitNotifications=AndroidLimitReminderSink(applicationContext)
        dailyLimits=DailyLimitRepository(PreferencesDailyLimitStorage(applicationContext),limitNotifications)
        launchCoordinator.continuationAllowed={id,minutes ->
            syncLimits()
            canContinueWithinLimit(dailyLimits.state.value.apps[id],minutes,java.time.LocalDate.now()) &&
                hasUsageAccess(applicationContext)
        }
        readLimitNavigation(intent)
        if(!frozen) {
            lifecycleScope.launch { combine(usage.today,repository.apps) { _,_ -> Unit }.collect { syncLimits() } }
            lifecycleScope.launch { dailyLimits.state.collect { limitNotifications.reconcile(it) } }
        }
        if(!frozen) {
            foregroundMonitor=ManagedForegroundMonitor(AndroidForegroundEventSource(applicationContext), {
                if (intentSessions.intentCheckEnabled) enabledForegroundTargets(repository) else emptyList()
            }, AndroidDetectionClock)
            lifecycleScope.launch { foregroundMonitor!!.state.collect { detection ->
                if(detection.status!=DetectionStatus.Observing) return@collect
                validateIntentRequests()
                for(record in detection.records.filter { it.intentCheckEligible }) {
                    val app=repository.catalog.firstOrNull { it.id==record.appId } ?: continue
                    val enabled=repository.apps.value.any { it.appId==app.id && it.isManaged && it.intentCheckEnabled }
                    val request=try { intentSessions.offer(record,app,enabled && hasUsageAccess(applicationContext),detection.observedActiveApps) }
                        catch(_:RuntimeException) { null }
                    if(request!=null && !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) notifications.post(request)
                }
            } }
        }
        setContent {
            val book by intentSessions.state.collectAsState()
            val installedCatalog=repository.catalogState?.collectAsState()?.value ?: repository.catalog
            val limits by dailyLimits.state.collectAsState()
            val saved=rememberSaveableStateHolder()
            IntentGateTheme(dynamicColor=false,darkTheme=false) {
                val pending=if(frozen) null else book.pending
                val expired=if(frozen || pending?.continuationOf!=null) null else
                    book.sessions.firstOrNull {it.id==requestedExpiredId && it.expiryDecision==ExpiryDecision.Pending} ?: book.pendingExpiry
                if(expired!=null) {
                    val app=installedCatalog.firstOrNull {it.id==expired.appId} ?: AppDefinition(expired.appId,expired.appName,expired.packageName,expired.iconRes,installed=false)
                    LaunchedEffect(expired.id) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            sessionRuntime.showExpiry(expired.id)
                        }
                    }
                    SessionExpiredScreen(expired,app,onContinue={
                        if(repository.catalogReady && launchCoordinator.continueExpired(expired.id)) {
                            requestedExpiredId=null;sessionRuntime.dismissExpiry(expired.id)
                            lifecycleScope.launch {usage.refresh();syncLimits()};true
                        } else false
                    },onFinish={
                        if(intentSessions.finishExpired(expired.id)) {
                            requestedExpiredId=null;sessionRuntime.dismissExpiry(expired.id);finishedUse=true
                        }
                    },notificationsAllowed=notificationAllowed,onEnableNotifications=::requestNotificationPermission,
                        continueEnabled=intentSessions.intentCheckEnabled)
                }
                else if(pending==null) saved.SaveableStateProvider("main") { IntentGateApp(frozen,repository,usage,if(frozen) null else intentSessions,if(frozen) null else dailyLimits,
                    onLaunchApp=::requestAppLaunch,onEnableNotifications=::requestNotificationPermission,requestedLimitAppId=requestedLimitApp,onLimitNavigationConsumed={requestedLimitApp=null}) }
                else {
                    val app=installedCatalog.firstOrNull { it.id==pending.appId }
                        ?: AppDefinition(pending.appId, pending.appName, pending.packageName,
                            android.R.drawable.sym_def_app_icon, installed=false)
                    IntentCheckScreen(pending,app,
                        onStart={purpose,minutes -> try {
                            if(pending.continuationOf!=null) usage.refresh()
                            syncLimits()
                            val result=launchCoordinator.start(pending.key,purpose,minutes)
                            if(result==AppLaunchResult.LaunchFailed) launchFailed=true
                            if(result!=AppLaunchResult.StartFailed) notifications.dismiss()
                            result!=AppLaunchResult.StartFailed
                        } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                          catch(_:RuntimeException) {false} },
                        onCancel={launchCoordinator.cancel(pending.key);notifications.dismiss()},
                        notificationsAllowed=notificationAllowed,onEnableNotifications=::requestNotificationPermission,dailyLimit=limits.apps[pending.appId])
                }
                if(finishedUse) AlertDialog(onDismissRequest={finishedUse=false},containerColor=GateColors.Background.last(),
                    title={GateText(loc(R.string.ig_session_ended),22f)},text={GateText(loc(R.string.ig_finish_exit_guidance),15f)},
                    confirmButton={TextButton(onClick={finishedUse=false}) {GateText(loc(R.string.ig_return_to_intentgate),15f)}},
                    dismissButton={TextButton(onClick={
                        finishedUse=false
                        try {startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                        catch(_:RuntimeException) { /* User remains safely in IntentGate. */ }
                    }) {GateText(loc(R.string.ig_go_to_launcher),15f)}})
                if(launchFailed) AlertDialog(onDismissRequest={launchFailed=false},
                    containerColor=GateColors.Background.last(),title={GateText(loc(R.string.ig_open_app),22f)},
                    text={GateText(loc(R.string.ig_app_launch_failed),15f)},
                    confirmButton={TextButton(onClick={launchFailed=false}) {GateText(loc(R.string.ig_close),16f)}})
            }
        }
    }
    private fun routeOverlayContinuation() {
        if (!repository.catalogReady || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val id = sessionRuntime.takeOverlayContinuation() ?: return
        val continued = try { launchCoordinator.continueExpired(id) }
            catch (_: RuntimeException) { false }
        if (continued) {
            requestedExpiredId = null
            sessionRuntime.dismissExpiry(id)
            lifecycleScope.launch { usage.refresh(); syncLimits() }
        }
    }

    override fun onStart() {
        super.onStart()
        sessionRuntime.setVisible(this,true)
        catalogJob?.cancel()
        catalogJob=lifecycleScope.launch {
            withContext(Dispatchers.IO) { repository.refreshCatalog() }
            validateIntentRequests()
            routeOverlayContinuation()
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { usage.refresh();syncLimits() }
        }
        validateIntentRequests()
        foregroundMonitor?.let { monitor -> monitor.resume()
            monitorJob=lifecycleScope.launch { while(true) {
                monitor.poll();validateIntentRequests()
                val zone=java.time.ZoneId.systemDefault().id
                val elapsed=android.os.SystemClock.elapsedRealtime()
                if(usage.today.value.date!=java.time.LocalDate.now() || usageZone!=zone ||
                    ((intentSessions.state.value.pending!=null || intentSessions.state.value.pendingExpiry!=null) && elapsed-pendingUsageRefreshAt>=30_000)) {
                    usage.refresh();pendingUsageRefreshAt=elapsed;usageZone=zone
                }
                syncLimits();delay(2_000)
            } }
        }
    }
    override fun onResume() {
        super.onResume()
        window.decorView.post { routeOverlayContinuation() }
        if(::notifications.isInitialized) { notificationAllowed=notifications.allowed();notifications.dismiss();syncLimits() }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent);readLimitNavigation(intent)
        requestedExpiredId=intent.getStringExtra("expired_session")
        // The continuation action requires a fresh in-process overlay tap.
        validateIntentRequests()
        window.decorView.post { routeOverlayContinuation() }
        notifications.dismiss()
    }
    override fun onStop() {
        monitorJob?.cancel();monitorJob=null;foregroundMonitor?.pause()
        if(::notifications.isInitialized) intentSessions.state.value.pending?.let { notifications.post(it) }
        sessionRuntime.setVisible(this,false)
        super.onStop()
    }
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.syncSystem(this)
    }

}
