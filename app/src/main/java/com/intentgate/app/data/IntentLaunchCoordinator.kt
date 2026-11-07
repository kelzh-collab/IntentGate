package com.intentgate.app.data

/** Only called by foreground user actions. The detector creates requests, never calls this launcher. */
fun interface TargetAppLauncher {fun launch(packageName:String):Boolean}
enum class AppLaunchResult {CheckOpened,Opened,Unavailable,StartFailed,StartedAndOpened,AlreadyStarted,LaunchFailed}
class IntentLaunchCoordinator(private val apps:AppRepository,private val sessions:IntentSessionRepository,
                              private val launcher:TargetAppLauncher,private val usageGranted:()->Boolean) {
    // Continuations must use the current shared real daily balance; no implicit extension.
    var continuationAllowed:(String,Int)->Boolean={_,_ -> false}
    @Synchronized fun continueExpired(id:String):Boolean {
        val old=sessions.state.value.sessions.firstOrNull {it.id==id} ?: return false
        val app=apps.catalog.firstOrNull {it.id==old.appId} ?: return false
        val enabled=apps.apps.value.any {it.appId==old.appId && it.isManaged && it.intentCheckEnabled}
        return sessions.offerContinuation(id,app,enabled)!=null
    }
    fun isAllowed(request:IntentRequest):Boolean = request.origin!=IntentOrigin.Simulated &&
        (request.origin==IntentOrigin.UserLaunch || usageGranted()) &&
        apps.apps.value.any {it.appId==request.appId && it.isManaged && it.intentCheckEnabled} &&
        apps.catalog.any {it.id==request.appId && it.packageName==request.packageName && it.isAvailable}
    private fun launch(packageName:String)=try {launcher.launch(packageName)} catch(_:RuntimeException) {false}
    @Synchronized fun request(appId:String):AppLaunchResult {
        val config=apps.apps.value.firstOrNull {it.appId==appId} ?: return AppLaunchResult.Unavailable
        val app=apps.catalog.firstOrNull {it.id==appId && it.isAvailable} ?: return AppLaunchResult.Unavailable
        if(!sessions.intentCheckEnabled || !config.isManaged || !config.intentCheckEnabled)
            return if(launch(app.packageName)) AppLaunchResult.Opened else AppLaunchResult.LaunchFailed
        if(sessions.state.value.unresolvedExpiry(appId)!=null) return AppLaunchResult.CheckOpened
        return if(sessions.offerLaunch(app,true)!=null) AppLaunchResult.CheckOpened else AppLaunchResult.Unavailable
    }
    @Synchronized fun start(key:String,purpose:String,minutes:Int):AppLaunchResult {
        // A persisted Started request is also the durable launch-once guard after process reconstruction.
        val request=sessions.state.value.requests.firstOrNull {it.key==key && it.status==IntentRequestStatus.Pending}
            ?: return if(sessions.state.value.sessions.any {it.requestKey==key}) AppLaunchResult.AlreadyStarted else AppLaunchResult.StartFailed
        if(!isAllowed(request) || (request.continuationOf!=null && !continuationAllowed(request.appId,minutes))) return AppLaunchResult.StartFailed
        val session=sessions.start(key,purpose,minutes,::isAllowed) ?: return AppLaunchResult.StartFailed
        if(launch(request.packageName)) return AppLaunchResult.StartedAndOpened
        sessions.suspendTiming("@string/ig_target_launch_failed_paused",session.appId)
        return AppLaunchResult.LaunchFailed
    }
    fun cancel(key:String) {sessions.cancel(key)}
}

/** Both fields are explicit choices; none of the duration chips is selected by default. */
fun intentCheckMinutes(selection:String,custom:String):Int? =
    (if(selection=="Custom") custom else selection.takeIf {it in listOf("5","10","15","30")} )
        ?.toIntOrNull()?.takeIf {it in 1..1440}
