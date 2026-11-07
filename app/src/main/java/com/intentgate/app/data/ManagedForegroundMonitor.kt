package com.intentgate.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import java.util.UUID

data class ForegroundTarget(val appId:String,val packageName:String,val intentCheckEnabled:Boolean=true)
enum class DetectionStatus { Suspended, Observing, PermissionRequired, Locked, Error }
enum class DetectionKind { Enter, Leave, ObservationGap }
data class ForegroundDetectionRecord(
    val id:String,val kind:DetectionKind,val appId:String?,val packageName:String?,
    val eventTimeMillis:Long?,val detectedAtMillis:Long,val detectedAtElapsedMillis:Long,
    val delayMillis:Long?,val reason:String,
    val intentCheckEligible:Boolean=false,
)
data class ForegroundDetectionState(
    val status:DetectionStatus=DetectionStatus.Suspended,
    val observedActiveApps:Set<String> = emptySet(),
    val records:List<ForegroundDetectionRecord> = emptyList(),
    val droppedRecords:Long=0,
)
data class ForegroundEventBatch(val status:DetectionStatus,val events:List<ForegroundEvent> = emptyList())
fun interface ForegroundEventSource {
    suspend fun read(fromMillis:Long,untilMillis:Long,packages:Set<String>):ForegroundEventBatch
}
interface DetectionClock { fun wallMillis():Long; fun elapsedMillis():Long }

/** Pure reducer. Phase 3 may consume eligible record IDs once; no prompt or interception is performed. */
class ManagedForegroundDetector(
    private val cooldownMillis:Long=10_000,
    private val maxCheckDelayMillis:Long=5_000,
    private val journalCapacity:Int=256,
) {
    init { require(cooldownMillis>=0 && maxCheckDelayMillis>=0 && journalCapacity>0) }
    private val prefix=UUID.randomUUID().toString()
    private var sequence=0L
    private var targets=emptyMap<String,ForegroundTarget>()
    private val enabledSince=mutableMapOf<String,Long>()
    private val active=mutableMapOf<String,MutableSet<String>>()
    private val currentEntry=mutableMapOf<String,String>()
    private val lastCheck=mutableMapOf<String,Long>()
    private val seen=linkedSetOf<ForegroundEvent>()
    private var highWater=Long.MIN_VALUE
    private val mutable=MutableStateFlow(ForegroundDetectionState())
    val state=mutable.asStateFlow()
    private fun append(kind:DetectionKind,target:ForegroundTarget?,event:Long?,wall:Long,elapsed:Long,reason:String):String {
        val id="$prefix-${++sequence}"
        val next=mutable.value.records+ForegroundDetectionRecord(id,kind,target?.appId,target?.packageName,event,wall,elapsed,
            event?.let { (wall-it).coerceAtLeast(0) },reason)
        mutable.value=mutable.value.copy(records=next.takeLast(journalCapacity),
            droppedRecords=mutable.value.droppedRecords+(next.size-journalCapacity).coerceAtLeast(0))
        return id
    }
    fun updateTargets(next:List<ForegroundTarget>,wall:Long,elapsed:Long) {
        val byPackage=next.associateBy { it.packageName }
        (targets.keys-byPackage.keys).forEach { pkg ->
            if(active[pkg]?.isNotEmpty()==true) append(DetectionKind.ObservationGap,targets[pkg],null,wall,elapsed,"ManagementDisabled")
            active.remove(pkg);currentEntry.remove(pkg);enabledSince.remove(pkg);lastCheck.remove(pkg)
        }
        (byPackage.keys-targets.keys).forEach { enabledSince[it]=wall }
        targets=byPackage
        publishActive()
    }
    private fun publishActive() {
        mutable.value=mutable.value.copy(observedActiveApps=if(mutable.value.status==DetectionStatus.Observing)
            active.filterValues { it.isNotEmpty() }.keys.mapNotNull { targets[it]?.appId }.toSet() else emptySet())
    }
    fun suspendObservation(wall:Long,elapsed:Long) {
        if(mutable.value.status!=DetectionStatus.Suspended)
            append(DetectionKind.ObservationGap,null,null,wall,elapsed,"BackgroundSuspended")
        mutable.value=mutable.value.copy(status=DetectionStatus.Suspended,observedActiveApps=emptySet())
        // Keep private reducer state for bounded chronological replay, never expose it as live background state.
    }
    fun reset(status:DetectionStatus,wall:Long,elapsed:Long,reason:String,clearProtectedHistory:Boolean=false,rebaseTargetTime:Boolean=false,resetCooldown:Boolean=false) {
        active.clear();currentEntry.clear();seen.clear();highWater=Long.MIN_VALUE
        if(rebaseTargetTime) enabledSince.keys.toList().forEach { enabledSince[it]=wall }
        if(resetCooldown) lastCheck.clear()
        if(clearProtectedHistory) {
            mutable.value=ForegroundDetectionState(status=status);lastCheck.clear();enabledSince.clear();targets=emptyMap()
        }
        append(DetectionKind.ObservationGap,null,null,wall,elapsed,reason)
        mutable.value=mutable.value.copy(status=status,observedActiveApps=emptySet())
    }
    fun accept(events:List<ForegroundEvent>,wall:Long,elapsed:Long,allowChecks:Boolean) {
        mutable.value=mutable.value.copy(status=DetectionStatus.Observing)
        var checksAllowed=allowChecks
        val relevant=events.filter { it.time<=wall && (it.packageName in targets || it.type in setOf(16,17,26,27)) }
        val newEvents=relevant.filter { it !in seen }.sortedBy { it.time }
        if(newEvents.any { it.time<highWater }) {
            val priorSeen=seen.toList()
            val priorHighWater=highWater
            reset(DetectionStatus.Observing,wall,elapsed,"LateEventContinuityLost")
            seen.addAll(priorSeen);highWater=priorHighWater
            checksAllowed=false
        }
        val entered=mutableListOf<Pair<String,ForegroundTarget>>()
        for((time,group) in newEvents.groupBy { it.time }) {
            val before=active.filterValues { it.isNotEmpty() }.keys.toSet()
            var boundary=false
            for(event in group) {
                if(!seen.add(event)) continue
                while(seen.size>4096) seen.remove(seen.first())
                highWater=maxOf(highWater,time)
                if(event.type in setOf(16,17,26,27)) { active.clear();boundary=true;continue }
                val pkg=event.packageName
                if(time<(enabledSince[pkg] ?: Long.MAX_VALUE)) continue
                val activities=active.getOrPut(pkg){mutableSetOf()}
                when(event.type) { 1 -> activities.add(event.activity);2,23 -> activities.remove(event.activity) }
            }
            val after=active.filterValues { it.isNotEmpty() }.keys.toSet()
            (before-after).forEach { pkg ->
                targets[pkg]?.let { append(DetectionKind.Leave,it,time,wall,elapsed,if(boundary) "SystemBoundary" else "ActivityBackground") }
                currentEntry.remove(pkg)
            }
            (after-before).forEach { pkg -> targets[pkg]?.let { target ->
                val id=append(DetectionKind.Enter,target,time,wall,elapsed,"PendingClassification")
                currentEntry[pkg]=id;entered+=id to target
            } }
        }
        val updates=mutableMapOf<String,Pair<Boolean,String>>()
        for((id,target) in entered) {
            val record=mutable.value.records.firstOrNull { it.id==id } ?: continue
            val reason=when {
                currentEntry[target.packageName]!=id || active[target.packageName].isNullOrEmpty() -> "AlreadyLeft"
                !target.intentCheckEnabled -> "IntentCheckDisabled"
                !checksAllowed -> "BaselineOrReplay"
                record.delayMillis==null || record.delayMillis>maxCheckDelayMillis -> "DetectionDelayed"
                lastCheck[target.packageName]?.let { elapsed-it<cooldownMillis }==true -> "Cooldown"
                else -> "Eligible"
            }
            val eligible=reason=="Eligible"
            if(eligible) lastCheck[target.packageName]=elapsed
            updates[id]=eligible to reason
        }
        mutable.value=mutable.value.copy(records=mutable.value.records.map { record ->
            updates[record.id]?.let { record.copy(intentCheckEligible=it.first,reason=it.second) } ?: record
        })
        publishActive()
    }
}

/** Injectable coordinator. Only its owner starts polling; there is no service or independent background loop. */
class ManagedForegroundMonitor(
    private val source:ForegroundEventSource,private val targets:()->List<ForegroundTarget>,
    private val clock:DetectionClock,val detector:ManagedForegroundDetector=ManagedForegroundDetector(),
    private val overlapMillis:Long=2_000,private val maxBackfillMillis:Long=300_000,
) {
    init { require(overlapMillis>=0 && maxBackfillMillis>=overlapMillis) }
    val state=detector.state
    private val mutex=Mutex()
    private var running=false
    private var generation=0L
    private var replay=true
    private var lastWall:Long?=null
    private var lastElapsed:Long?=null
    fun resume() { generation++;running=true;replay=true }
    fun pause() { generation++;running=false;replay=true;detector.suspendObservation(clock.wallMillis(),clock.elapsedMillis()) }
    suspend fun poll()=mutex.withLock {
        if(!running) return@withLock
        val pollGeneration=generation
        val wall=clock.wallMillis();val elapsed=clock.elapsedMillis()
        var baseline=replay || lastWall==null
        val previousWall=lastWall;val previousElapsed=lastElapsed
        if(previousWall!=null && previousElapsed!=null &&
            (elapsed<previousElapsed || abs((wall-previousWall)-(elapsed-previousElapsed))>5_000 || wall-previousWall>maxBackfillMillis)) {
            val clockChanged=elapsed<previousElapsed || abs((wall-previousWall)-(elapsed-previousElapsed))>5_000
            detector.reset(DetectionStatus.Observing,wall,elapsed,"ClockOrObservationGap",
                rebaseTargetTime=clockChanged,resetCooldown=elapsed<previousElapsed)
            baseline=true
        }
        val selected=targets()
        detector.updateTargets(selected,wall,elapsed)
        val from=maxOf((previousWall ?: wall)-overlapMillis,wall-maxBackfillMillis).coerceAtMost(wall)
        val result=try { source.read(from,wall,selected.map { it.packageName }.toSet()) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:SecurityException) { ForegroundEventBatch(DetectionStatus.PermissionRequired) }
            catch(_:RuntimeException) { ForegroundEventBatch(DetectionStatus.Error) }
        if(!running || pollGeneration!=generation) return@withLock
        val detectedWall=clock.wallMillis();val detectedElapsed=clock.elapsedMillis()
        if(abs((detectedWall-wall)-(detectedElapsed-elapsed))>5_000 || detectedElapsed<elapsed) {
            detector.reset(DetectionStatus.Observing,detectedWall,detectedElapsed,"ClockChangedDuringRead",
                rebaseTargetTime=true,resetCooldown=detectedElapsed<elapsed)
            lastWall=detectedWall;lastElapsed=detectedElapsed;replay=true
            return@withLock
        }
        val latest=targets()
        if(latest!=selected) { detector.updateTargets(latest,detectedWall,detectedElapsed);baseline=true }
        if(result.status!=DetectionStatus.Observing) {
            detector.reset(result.status,detectedWall,detectedElapsed,"Source${result.status}",clearProtectedHistory=true)
            lastWall=null;lastElapsed=null;replay=true
        } else {
            detector.accept(result.events.filter { it.time>=from && it.time<=wall },detectedWall,detectedElapsed,!baseline)
            lastWall=wall;lastElapsed=elapsed;replay=false
        }
    }
}
