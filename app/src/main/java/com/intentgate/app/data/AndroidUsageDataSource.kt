package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class UsageAccess { Demo, Loading, PermissionRequired, Locked, NoData, Ready, Error }
data class UsageSnapshot(val today: DailyUsage, val history: List<DatedUsage> = emptyList())

fun hasUsageAccess(context: Context): Boolean = try {
    val ops=context.getSystemService(AppOpsManager::class.java)
    val mode=if(Build.VERSION.SDK_INT>=29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)
    mode==AppOpsManager.MODE_ALLOWED
} catch(_:RuntimeException) { false }

/** User action only; no permissions are granted by this function. */
fun openUsageAccess(context:Context): Boolean {
    val intents=listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:${context.packageName}")),Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    for(intent in intents) try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true }
        catch(_:RuntimeException) {}
    return false
}

/** One atomic snapshot for all screens. Reads occur only while the UI is started; no background service. */
class AndroidUsageDataSource(private val context:Context,private val repository:AppRepository):UsageDataSource {
    private fun empty(access:UsageAccess,date:LocalDate=LocalDate.now(),note:String="")=DailyUsage(
        emptyMap(),0,emptyList(),false,access=access,intentStatsAvailable=false,intentionsAreDemo=false,date=date,note=note)
    private val state=MutableStateFlow(UsageSnapshot(empty(UsageAccess.Loading)))
    override val snapshot=state.asStateFlow()
    private val todayState=MutableStateFlow(state.value.today)
    private val historyState=MutableStateFlow<List<DatedUsage>>(emptyList())
    override val today:StateFlow<DailyUsage> = todayState.asStateFlow()
    override val history:StateFlow<List<DatedUsage>> = historyState.asStateFlow()
    override val referenceDate get()=state.value.today.date
    private val mutex=Mutex()
    private fun publish(value:UsageSnapshot) { state.value=value;todayState.value=value.today;historyState.value=value.history }
    override suspend fun refresh()=mutex.withLock {
        val now=System.currentTimeMillis()
        val zone=ZoneId.systemDefault()
        val date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if(!hasUsageAccess(context)) { publish(UsageSnapshot(empty(UsageAccess.PermissionRequired,date)));return@withLock }
        if(!context.getSystemService(UserManager::class.java).isUserUnlocked) { publish(UsageSnapshot(empty(UsageAccess.Locked,date)));return@withLock }
        try {
            val result=withContext(Dispatchers.IO) {
                val from=date.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
                val stream=context.getSystemService(UsageStatsManager::class.java).queryEvents(from,now)
                if(stream==null) return@withContext UsageSnapshot(empty(UsageAccess.Locked,date))
                val events=mutableListOf<ForegroundEvent>()
                var earliest:Long?=null
                val buffer=UsageEvents.Event()
                val ids=repository.apps.value.map { it.appId }.toSet()
                // Paused management does not erase measured history; totals cover the selected list.
                val packages=repository.catalog.filter { it.id in ids && it.isAvailable }.associate { it.packageName to it.id }
                while(stream.hasNextEvent()) {
                    stream.getNextEvent(buffer)
                    if(earliest==null) earliest=buffer.timeStamp
                    if(buffer.packageName in packages || buffer.eventType in setOf(16,17,26,27))
                        events.add(ForegroundEvent(buffer.timeStamp,buffer.packageName.orEmpty(),buffer.className.orEmpty(),buffer.eventType))
                }
                if(earliest==null) return@withContext UsageSnapshot(empty(UsageAccess.NoData,date))
                val days=foregroundUsageDays(events,packages,from,now,zone)
                val firstFull=Instant.ofEpochMilli(earliest!!).atZone(zone).toLocalDate().plusDays(1)
                fun usage(day:LocalDate)=empty(UsageAccess.Ready,day).copy(
                    secondsByApp=packages.values.associateWith { days[day]?.get(it) ?: 0 },
                    note=if(packages.isEmpty()) loc(R.string.ig_no_selected_apps_installed) else if(day==date && day<firstFull) loc(R.string.ig_limited_system_history) else loc(R.string.ig_selected_apps_android_foreground_events))
                val history=(29 downTo 1).map { date.minusDays(it.toLong()) }.filter { it>=firstFull }.map { DatedUsage(it,usage(it)) }
                UsageSnapshot(usage(date),history)
            }
            // Revocation during a query must never republish stale permission-protected data.
            publish(if(hasUsageAccess(context)) result else UsageSnapshot(empty(UsageAccess.PermissionRequired,date)))
        } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
          catch(_:SecurityException) { publish(UsageSnapshot(empty(UsageAccess.PermissionRequired,date))) }
          catch(_:RuntimeException) { publish(UsageSnapshot(empty(UsageAccess.Error,date))) }
    }
}

internal data class PickerUsage(val access: UsageAccess, val secondsByPackage: Map<String, Int> = emptyMap())

/** A separate read for the picker, so unselected apps never enter managed totals. */
internal suspend fun readPickerUsage(context: Context, packages: Set<String>): PickerUsage =
    withContext(Dispatchers.Default) {
        if (!hasUsageAccess(context)) return@withContext PickerUsage(UsageAccess.PermissionRequired)
        if (packages.isEmpty()) return@withContext PickerUsage(UsageAccess.Ready)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val previousMidnight = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        try {
            val batch = AndroidForegroundEventSource(context).read(previousMidnight, now, packages)
            when {
                batch.status == DetectionStatus.PermissionRequired || !hasUsageAccess(context) ->
                    PickerUsage(UsageAccess.PermissionRequired)
                batch.status == DetectionStatus.Locked -> PickerUsage(UsageAccess.Locked)
                batch.status != DetectionStatus.Observing -> PickerUsage(UsageAccess.Error)
                batch.events.isEmpty() -> PickerUsage(UsageAccess.NoData)
                else -> {
                    val totals = foregroundUsageDays(batch.events, packages.associateWith { it },
                        midnight, now, zone)[date].orEmpty()
                    PickerUsage(UsageAccess.Ready, packages.associateWith { totals[it] ?: 0 })
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_:RuntimeException) {
            PickerUsage(if (hasUsageAccess(context)) UsageAccess.Error else UsageAccess.PermissionRequired)
        }
    }
