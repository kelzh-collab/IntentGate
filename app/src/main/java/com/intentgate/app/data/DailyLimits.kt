package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.time.LocalDate

interface LimitClock { fun today():LocalDate;fun wallMillis():Long }
object SystemLimitClock:LimitClock {
    override fun today()=LocalDate.now()
    override fun wallMillis()=System.currentTimeMillis()
}
enum class LimitAvailability { Ready, Demo, PermissionRequired, NoData, Stale, NotInstalled, Locked, Loading, Error }
data class DailyLimitExtension(val actionId:String,val appId:String,val date:LocalDate,val addedMinutes:Int,
    val createdAtMillis:Long,val baseLimitMinutes:Int)
data class LimitReminderKey(val appId:String,val date:LocalDate,val threshold:Int)
data class DailyLimitLedger(val extensions:List<DailyLimitExtension> = emptyList(),val satisfied:Set<LimitReminderKey> = emptySet())
interface DailyLimitStorage { fun read():DailyLimitLedger;fun write(ledger:DailyLimitLedger) }
class MemoryDailyLimitStorage(var ledger:DailyLimitLedger=DailyLimitLedger()):DailyLimitStorage {
    override fun read()=ledger
    override fun write(ledger:DailyLimitLedger) { this.ledger=ledger }
}
data class DailyLimitState(val appId:String,val date:LocalDate,val baseMinutes:Int,val extraMinutes:Long,
    val usedSeconds:Long?,val availability:LimitAvailability,val managed:Boolean) {
    val limitSeconds:Long get()=baseMinutes*60L+extraMinutes*60L
    val remainingSeconds:Long? get()=usedSeconds?.let { (limitSeconds-it).coerceAtLeast(0) }
    val ratio:Double? get()=usedSeconds?.let { it.toDouble()/limitSeconds }
    val percent:Int? get()=usedSeconds?.let { (it*100/limitSeconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    val reached:Boolean get()=usedSeconds?.let { it>=limitSeconds } ?: false
    val statusLabel:String get()=when {
        !managed -> loc(R.string.ig_paused)
        availability==LimitAvailability.Demo -> loc(R.string.ig_demo_data_no_real_limit_status)
        usedSeconds==null -> when(availability) {
            LimitAvailability.PermissionRequired -> loc(R.string.ig_usage_access_required)
            LimitAvailability.NotInstalled -> loc(R.string.ig_not_installed)
            LimitAvailability.Stale -> loc(R.string.ig_refreshing_today_s_usage)
            LimitAvailability.Loading -> loc(R.string.ig_loading_usage)
            LimitAvailability.Locked -> loc(R.string.ig_unlock_device)
            else -> loc(R.string.ig_usage_unavailable)
        }
        reached -> loc(R.string.ig_limit_reached)
        else -> loc(R.string.ig_managed)
    }
    val summary:String get()=usedSeconds?.let {
        loc(R.string.ig_used_left, limitDuration(it), limitDuration(remainingSeconds!!), percent)
    } ?: statusLabel
}
data class DailyLimitSnapshot(val date:LocalDate,val apps:Map<String,DailyLimitState> = emptyMap())
data class DailyLimitReminder(val key:LimitReminderKey,val appName:String,val usedSeconds:Long,val limitSeconds:Long)
fun interface LimitReminderSink { fun deliver(reminder:DailyLimitReminder):Boolean }
fun limitDuration(seconds:Long):String=when {
    seconds==0L -> loc(R.string.ig_0m)
    seconds<60 -> loc(R.string.ig_s, seconds)
    seconds<3600 -> loc(R.string.ig_m, seconds/60)
    else -> loc(R.string.ig_h_m, seconds/3600, seconds%3600/60)
}
fun sessionDailyLimitWarning(state:DailyLimitState?,minutes:Int):String? {
    if(state?.remainingSeconds==null) return loc(R.string.ig_daily_remaining_time_is_unavailable_no_real_usage_estimate_can)
    return if(minutes*60L>state.remainingSeconds!!) loc(R.string.ig_selected_session_exceeds_today_s_remaining_limit_you_may_adjus, limitDuration(state.remainingSeconds!!))
        else null
}
fun calculateDailyLimits(usage:DailyUsage,apps:List<ManagedApp>,catalog:List<AppDefinition>,ledger:DailyLimitLedger,
                        today:LocalDate,permissionGranted:Boolean=true):DailyLimitSnapshot {
    val states=apps.associate { settings ->
        val definition=catalog.firstOrNull { it.id==settings.appId }
        val availability=when {
            !permissionGranted || usage.access==UsageAccess.PermissionRequired -> LimitAvailability.PermissionRequired
            usage.isMock || usage.access==UsageAccess.Demo -> LimitAvailability.Demo
            definition?.installed==false -> LimitAvailability.NotInstalled
            definition?.launchable==false -> LimitAvailability.Error
            usage.date!=today -> LimitAvailability.Stale
            usage.access==UsageAccess.Locked -> LimitAvailability.Locked
            usage.access==UsageAccess.Loading -> LimitAvailability.Loading
            usage.access==UsageAccess.Error -> LimitAvailability.Error
            !usage.available || definition==null || (usage.secondsByApp[settings.appId] ?: -1)<0 -> LimitAvailability.NoData
            else -> LimitAvailability.Ready
        }
        val used=usage.secondsByApp[settings.appId]?.toLong()?.takeIf { availability==LimitAvailability.Ready }
        val extra=ledger.extensions.filter { it.appId==settings.appId && it.date==today }.sumOf { it.addedMinutes.toLong() }
        settings.appId to DailyLimitState(settings.appId,today,settings.dailyLimitMinutes.coerceIn(1,1440),extra,used,availability,settings.isManaged)
    }
    return DailyLimitSnapshot(today,states)
}

/** One shared limit projection; reservations survive process restart and never use Intent countdown as usage. */
class DailyLimitRepository(private val storage:DailyLimitStorage,private val sink:LimitReminderSink,
                           private val clock:LimitClock=SystemLimitClock) {
    private var ledger=storage.read()
    private val mutable=MutableStateFlow(DailyLimitSnapshot(clock.today()))
    val state=mutable.asStateFlow()
    private var lastUsage:DailyUsage?=null
    private var lastApps=emptyList<ManagedApp>()
    private var lastCatalog=emptyList<AppDefinition>()
    private var lastPermission=false
    val extensions:List<DailyLimitExtension> get()=ledger.extensions.toList()
    private fun persist(next:DailyLimitLedger):Boolean=try { storage.write(next);ledger=next;true } catch(_:RuntimeException) { false }
    @Synchronized fun refresh(usage:DailyUsage,apps:List<ManagedApp>,catalog:List<AppDefinition>,permissionGranted:Boolean=true) {
        lastUsage=usage;lastApps=apps;lastCatalog=catalog;lastPermission=permissionGranted
        val snapshot=calculateDailyLimits(usage,apps,catalog,ledger,clock.today(),permissionGranted)
        mutable.value=snapshot
        for((id,state) in snapshot.apps) {
            val used=state.usedSeconds ?: continue
            if(!state.managed || state.availability!=LimitAvailability.Ready) continue
            val threshold=when { used>=state.limitSeconds -> 100;used*100>=state.limitSeconds*80 -> 80;else -> continue }
            val key=LimitReminderKey(id,snapshot.date,threshold)
            if(key in ledger.satisfied) continue
            // A 100% notice covers the lower threshold, avoiding back-to-back alerts after a delayed refresh.
            val reserved=if(threshold==100) setOf(key,LimitReminderKey(id,snapshot.date,80)) else setOf(key)
            val previous=ledger
            if(!persist(previous.copy(satisfied=previous.satisfied+reserved))) continue
            val reminder=DailyLimitReminder(key,catalog.firstOrNull { it.id==id }?.name ?: id,used,state.limitSeconds)
            val delivered=try { sink.deliver(reminder) } catch(_:RuntimeException) { false }
            if(!delivered) persist(previous)
        }
    }
    @Synchronized fun extendToday(appId:String,minutes:Int,actionId:String):Boolean {
        if(minutes !in 1..1440 || actionId.isBlank()) return false
        ledger.extensions.firstOrNull { it.actionId==actionId }?.let { return it.appId==appId && it.addedMinutes==minutes }
        val usage=lastUsage ?: return false
        val current=calculateDailyLimits(usage,lastApps,lastCatalog,ledger,clock.today(),lastPermission).apps[appId] ?: return false
        if(current.usedSeconds==null || !current.managed) return false
        val entry=DailyLimitExtension(actionId,appId,clock.today(),minutes,clock.wallMillis(),current.baseMinutes)
        if(!persist(ledger.copy(extensions=ledger.extensions+entry))) return false
        refresh(usage,lastApps,lastCatalog,lastPermission)
        return true
    }
}
object DailyLimitCodec {
    fun encode(ledger:DailyLimitLedger):ByteArray=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
        out.writeInt(1);out.writeInt(ledger.extensions.size)
        ledger.extensions.forEach { e -> with(out) { writeUTF(e.actionId);writeUTF(e.appId);writeUTF(e.date.toString());writeInt(e.addedMinutes);writeLong(e.createdAtMillis);writeInt(e.baseLimitMinutes) } }
        out.writeInt(ledger.satisfied.size)
        ledger.satisfied.forEach { k -> out.writeUTF(k.appId);out.writeUTF(k.date.toString());out.writeInt(k.threshold) }
    } }.toByteArray()
    fun decode(bytes:ByteArray):DailyLimitLedger=DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt()==1)
        fun count()=input.readInt().also { require(it in 0..100000) }
        val extensions=List(count()) { with(input) {
            DailyLimitExtension(readUTF(),readUTF(),LocalDate.parse(readUTF()),readInt(),readLong(),readInt()).also { require(it.addedMinutes in 1..1440) }
        } }
        val satisfied=List(count()) { LimitReminderKey(input.readUTF(),LocalDate.parse(input.readUTF()),input.readInt()).also { require(it.threshold==80 || it.threshold==100) } }.toSet()
        DailyLimitLedger(extensions,satisfied)
    }
}
