package com.intentgate.app.data

import com.intentgate.app.session.CountdownState
import com.intentgate.app.session.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.util.UUID

enum class IntentRequestStatus { Pending, Cancelled, Started, Expired }
enum class ExpiryDecision { None, Pending, Checking, Continued, Finished }
enum class IntentOrigin { AndroidEvent, Simulated, UserLaunch }
data class IntentRequest(val key:String,val appId:String,val packageName:String,val appName:String,val iconRes:Int,
    val eventAt:Long,val createdAt:Long,val expiresAt:Long,val origin:IntentOrigin=IntentOrigin.AndroidEvent,
    val status:IntentRequestStatus=IntentRequestStatus.Pending,val continuationOf:String?=null)
data class SessionWindow(val startAt:Long,val endAt:Long?=null,val creditedMillis:Long=0,val verifiedThrough:Long=0)
data class IntentSession(val id:String,val requestKey:String,val appId:String,val packageName:String,val appName:String,
    val iconRes:Int,val purpose:String,val startedAt:Long,val durationMillis:Long,val remainingMillis:Long,
    val status:SessionStatus=SessionStatus.Running,val outcome:IntentOutcome=IntentOutcome.InProgress,
    val windows:List<SessionWindow> = emptyList(),val origin:IntentOrigin=IntentOrigin.AndroidEvent,
    val timingNote:String="@string/ig_waiting_for_confirmed_target_app_foreground_time",
    val endedAt:Long?=null,val expiryDecision:ExpiryDecision=ExpiryDecision.None,
    val expiryNotified:Boolean=false,val resolvedAt:Long?=null) {
    val usedMillis:Long get()=durationMillis-remainingMillis
    fun countdown()=CountdownState(durationMillis,remainingMillis,status)
}
data class IntentSessionBook(val requests:List<IntentRequest> = emptyList(),val sessions:List<IntentSession> = emptyList()) {
    val pending get()=requests.lastOrNull { it.status==IntentRequestStatus.Pending }
    val pendingExpiry get()=sessions.firstOrNull { it.expiryDecision==ExpiryDecision.Pending }
    fun unresolvedExpiry(appId:String)=sessions.lastOrNull {it.appId==appId && it.expiryDecision in setOf(ExpiryDecision.Pending,ExpiryDecision.Checking)}
    val active get()=sessions.lastOrNull { it.status!=SessionStatus.Ended }
    fun activeFor(appId:String)=sessions.lastOrNull { it.appId==appId && it.status!=SessionStatus.Ended }
}
interface IntentSessionStorage { fun read():IntentSessionBook; fun write(book:IntentSessionBook) }
class MemoryIntentSessionStorage(var book:IntentSessionBook=IntentSessionBook()):IntentSessionStorage {
    override fun read()=book
    override fun write(book:IntentSessionBook) { this.book=book }
}

/** Explicit user pause and process recreation close counting windows. Wall time alone never reduces remaining time. */
class IntentSessionRepository(
    private val storage: IntentSessionStorage,
    private val checksEnabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {
    val intentCheckEnabled: Boolean get() = checksEnabled()
    private val mutable=MutableStateFlow(restore(storage.read()))
    val state=mutable.asStateFlow()
    init { storage.write(mutable.value) }
    private fun restore(book:IntentSessionBook)=book.copy(sessions=book.sessions.map { session ->
        if(session.status==SessionStatus.Running) session.copy(status=SessionStatus.Paused,
            windows=session.windows.map { w -> if(w.endAt==null) w.copy(endAt=maxOf(w.startAt,w.verifiedThrough)) else w },
            timingNote="@string/ig_restored_paused_resume_to_continue") else session
    })
    private fun commit(book:IntentSessionBook) { storage.write(book);mutable.value=book }
    @Synchronized fun expireAndValidate(allowed:(IntentRequest)->Boolean) {
        val wall=now()
        val book=mutable.value
        val enabled = intentCheckEnabled
        val next=book.copy(requests=book.requests.map { if(it.status==IntentRequestStatus.Pending &&
            (!enabled || wall>it.expiresAt || !allowed(it))) it.copy(status=IntentRequestStatus.Expired) else it })
        val reconciled=next.copy(sessions=next.sessions.map { session ->
            if(session.expiryDecision==ExpiryDecision.Checking && next.requests.none {
                it.continuationOf==session.id && it.status==IntentRequestStatus.Pending
            }) session.copy(expiryDecision=ExpiryDecision.Pending) else session
        })
        if(reconciled!=book) commit(reconciled)
    }
    @Synchronized fun offer(record:ForegroundDetectionRecord,app:AppDefinition,allowed:Boolean,activeApps:Set<String>):IntentRequest? {
        val wall=now();val eventAt=record.eventTimeMillis ?: return null
        if(!intentCheckEnabled || !allowed || !record.intentCheckEligible || record.kind!=DetectionKind.Enter || record.appId!=app.id ||
            record.packageName!=app.packageName || app.id !in activeApps || wall-eventAt !in 0..5_000 ||
            wall-record.detectedAtMillis !in 0..5_000) return null
        val key="${app.packageName}:$eventAt"
        val book=mutable.value
        // A detected event must not replace a form that the user explicitly opened.
        if (book.pending?.let { it.origin == IntentOrigin.UserLaunch && wall <= it.expiresAt } == true) return null
        if(book.requests.any { it.key==key } || book.sessions.any { it.requestKey==key } ||
            (book.activeFor(app.id)!=null || book.unresolvedExpiry(app.id)!=null)) return null
        val request=IntentRequest(key,app.id,app.packageName,app.name,app.iconRes,eventAt,wall,wall+120_000)
        val old=book.requests.map { if(it.status==IntentRequestStatus.Pending) it.copy(status=IntentRequestStatus.Expired) else it }
        commit(book.copy(requests=(old+request).takeLast(2048)))
        return request
    }
    /** Explicit in-app user action: never mislabel it as a detected Android event. */
    @Synchronized fun offerLaunch(app:AppDefinition,enabled:Boolean):IntentRequest? {
        if(!intentCheckEnabled || !enabled || !app.isAvailable) return null
        val book=mutable.value;val wall=now()
        book.pending?.takeIf { it.appId==app.id && it.origin==IntentOrigin.UserLaunch && wall<=it.expiresAt }?.let {return it}
        val request=IntentRequest("launch:${app.packageName}:${UUID.randomUUID()}",app.id,app.packageName,app.name,
            app.iconRes,wall,wall,wall+120_000,IntentOrigin.UserLaunch)
        commit(book.copy(requests=(book.requests.map { if(it.status==IntentRequestStatus.Pending)
            it.copy(status=IntentRequestStatus.Expired) else it }+request).takeLast(2048)))
        return request
    }
    @Synchronized fun cancel(key:String) {
        val book=mutable.value
        val parent=book.requests.firstOrNull {it.key==key && it.status==IntentRequestStatus.Pending}?.continuationOf
        commit(book.copy(requests=book.requests.map { if(it.key==key && it.status==IntentRequestStatus.Pending)
            it.copy(status=IntentRequestStatus.Cancelled) else it },sessions=book.sessions.map {
                if(it.id==parent && it.expiryDecision==ExpiryDecision.Checking) it.copy(expiryDecision=ExpiryDecision.Pending) else it
            }))
    }
    /** A retry is a fresh explicit choice, linked to (and never overwriting) the expired record. */
    @Synchronized fun offerContinuation(id:String,app:AppDefinition,enabled:Boolean):IntentRequest? {
        val book=mutable.value
        val old=book.sessions.firstOrNull {it.id==id && it.appId==app.id && it.packageName==app.packageName} ?: return null
        if(!intentCheckEnabled || !enabled || !app.isAvailable || old.expiryDecision !in setOf(ExpiryDecision.Pending,ExpiryDecision.Checking)) return null
        val wall=now()
        book.requests.lastOrNull {it.continuationOf==id && it.status==IntentRequestStatus.Pending && wall<=it.expiresAt}?.let {return it}
        val request=IntentRequest("continue:$id:${UUID.randomUUID()}",app.id,app.packageName,app.name,app.iconRes,
            wall,wall,wall+120_000,IntentOrigin.UserLaunch,continuationOf=id)
        commit(book.copy(requests=(book.requests.map {if(it.status==IntentRequestStatus.Pending)
            it.copy(status=IntentRequestStatus.Expired) else it}+request).takeLast(2048),
            sessions=book.sessions.map {if(it.id==id) it.copy(expiryDecision=ExpiryDecision.Checking) else if(it.expiryDecision==ExpiryDecision.Checking)
                it.copy(expiryDecision=ExpiryDecision.Pending) else it}))
        return request
    }
    @Synchronized fun finishExpired(id:String):Boolean {
        val book=mutable.value
        val session=book.sessions.firstOrNull {it.id==id && it.expiryDecision in setOf(ExpiryDecision.Pending,ExpiryDecision.Checking)} ?: return false
        commit(book.copy(sessions=book.sessions.map {if(it.id==id) close(it,now()).copy(status=SessionStatus.Ended,
            endedAt=session.endedAt ?: now(),expiryDecision=ExpiryDecision.Finished,resolvedAt=now()) else it},
            requests=book.requests.map {if(it.continuationOf==id && it.status==IntentRequestStatus.Pending) it.copy(status=IntentRequestStatus.Cancelled) else it}))
        return true
    }
    @Synchronized fun acknowledgeExpiry(id:String) {
        val book=mutable.value
        if(book.sessions.none {it.id==id && !it.expiryNotified && it.expiryDecision==ExpiryDecision.Pending}) return
        commit(book.copy(sessions=book.sessions.map {if(it.id==id) it.copy(expiryNotified=true) else it}))
    }
    /** Reserve before delivery: ignored notifications never re-alert. Failure (e.g. denied permission) remains retryable. */
    @Synchronized fun deliverExpiry(id:String,sink:(IntentSession)->Boolean):Boolean {
        val book=mutable.value
        val session=book.sessions.firstOrNull {it.id==id && !it.expiryNotified && it.expiryDecision==ExpiryDecision.Pending && it.origin!=IntentOrigin.Simulated} ?: return false
        acknowledgeExpiry(id)
        val sent=try {sink(session)} catch(_:RuntimeException) {false}
        if(!sent) commit(mutable.value.copy(sessions=mutable.value.sessions.map {if(it.id==id) it.copy(expiryNotified=false) else it}))
        return sent
    }
    @Synchronized fun start(key:String,purpose:String,minutes:Int,allowed:(IntentRequest)->Boolean):IntentSession? {
        val book=mutable.value
        book.sessions.firstOrNull { it.requestKey==key }?.let { return it }
        val request=book.requests.firstOrNull { it.key==key && it.status==IntentRequestStatus.Pending } ?: return null
        val clean=purpose.trim()
        if(!intentCheckEnabled || clean.isEmpty() || clean.length>200 || minutes !in 1..1440 || now()>request.expiresAt || !allowed(request)) return null
        val wall=now();val duration=minutes*60_000L
        val session=IntentSession(UUID.randomUUID().toString(),key,request.appId,request.packageName,request.appName,
            request.iconRes,clean,wall,duration,duration,windows=listOf(SessionWindow(wall)),origin=request.origin)
        val parent=request.continuationOf?.let { id -> book.sessions.firstOrNull {it.id==id} }
        if(request.continuationOf!=null && parent?.expiryDecision!=ExpiryDecision.Checking) return null
        val sessions=book.sessions.map { old -> if(old.appId==request.appId && old.status!=SessionStatus.Ended) close(old,wall).copy(
            status=SessionStatus.Ended,outcome=IntentOutcome.NotCompleted,endedAt=wall,timingNote="@string/ig_replaced_by_a_new_session")
            else if(old.id==request.continuationOf) old.copy(expiryDecision=ExpiryDecision.Continued,resolvedAt=wall) else old }
        commit(book.copy(requests=book.requests.map { if(it.key==key) it.copy(status=IntentRequestStatus.Started) else it },sessions=sessions+session))
        return session
    }
    private fun close(session:IntentSession,wall:Long)=session.copy(windows=session.windows.map { w ->
        if(w.endAt==null) w.copy(endAt=maxOf(w.startAt,wall)) else w })
    @Synchronized fun pause(appId:String?=null) {
        val book=mutable.value;val active=(if(appId==null) book.active else book.activeFor(appId)) ?: return
        if(active.status!=SessionStatus.Running) return
        commit(book.copy(sessions=book.sessions.map { if(it.id==active.id) close(it,now()).copy(status=SessionStatus.Paused,timingNote="@string/ig_paused") else it }))
    }
    @Synchronized fun resume(appId:String?=null) {
        val book=mutable.value;val active=(if(appId==null) book.active else book.activeFor(appId)) ?: return
        if(active.status!=SessionStatus.Paused || active.remainingMillis==0L) return
        commit(book.copy(sessions=book.sessions.map { if(it.id==active.id) it.copy(status=SessionStatus.Running,
            windows=it.windows+SessionWindow(now()),timingNote="@string/ig_waiting_for_confirmed_target_app_foreground_time") else it }))
    }
    @Synchronized fun end(completed:Boolean=false,appId:String?=null) {
        val book=mutable.value;val active=(if(appId==null) book.active else book.activeFor(appId)) ?: return
        commit(book.copy(sessions=book.sessions.map { if(it.id==active.id) close(it,now()).copy(status=SessionStatus.Ended,endedAt=now(),
            outcome=if(completed) IntentOutcome.Completed else IntentOutcome.NotCompleted,
            timingNote=if(completed) "@string/ig_intent_marked_completed" else "@string/ig_ended_without_confirmed_completion") else it }))
    }
    @Synchronized fun suspendTiming(note:String,appId:String?=null) {
        val book=mutable.value;val active=(if(appId==null) book.active else book.activeFor(appId)) ?: return
        // Unknown/denied periods are excluded; do not advance to an unverified wall timestamp.
        val next=active.copy(status=SessionStatus.Paused,windows=active.windows.map { w ->
            if(w.endAt==null) w.copy(endAt=maxOf(w.startAt,w.verifiedThrough)) else w },timingNote=note)
        if(next!=active) commit(book.copy(sessions=book.sessions.map { if(it.id==active.id) next else it }))
    }
    @Synchronized fun credit(id:String,expectedWindows:List<SessionWindow>,credits:List<Pair<Long,Long>>) {
        val book=mutable.value;val session=book.sessions.firstOrNull { it.id==id } ?: return
        if(session.status==SessionStatus.Ended || session.windows!=expectedWindows || credits.size!=expectedWindows.size) return
        val windows=session.windows.mapIndexed { i,w -> w.copy(creditedMillis=maxOf(w.creditedMillis,credits[i].first),
            verifiedThrough=maxOf(w.verifiedThrough,credits[i].second)) }
        val used=windows.sumOf { it.creditedMillis }.coerceIn(0,session.durationMillis)
        val left=(session.durationMillis-used).coerceAtLeast(0)
        val expired=left==0L && session.status!=SessionStatus.Ended
        val next=session.copy(windows=if(expired) windows.map {if(it.endAt==null) it.copy(endAt=maxOf(it.startAt,it.verifiedThrough)) else it} else windows,
            endedAt=if(expired) now() else session.endedAt,
            expiryDecision=if(expired && session.origin!=IntentOrigin.Simulated) ExpiryDecision.Pending else session.expiryDecision,
            remainingMillis=minOf(session.remainingMillis,left),
            status=if(expired) SessionStatus.Ended else session.status,
            outcome=if(expired) IntentOutcome.NotCompleted else session.outcome,
            timingNote=if(expired) "@string/ig_time_ended_intent_completion_not_confirmed" else "@string/ig_confirmed_target_app_foreground_time_only")
        if(next!=session) commit(book.copy(sessions=book.sessions.map { if(it.id==id) next else it }))
    }
}

/** Versioned binary codec is pure JVM and shared with Android persistence; decode failures never execute pending requests. */
object IntentSessionCodec {
    fun encode(book:IntentSessionBook):ByteArray=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
        out.writeInt(2);out.writeInt(book.requests.size)
        book.requests.forEach { r -> with(out) { writeUTF(r.key);writeUTF(r.appId);writeUTF(r.packageName);writeUTF(r.appName);writeInt(r.iconRes)
            writeLong(r.eventAt);writeLong(r.createdAt);writeLong(r.expiresAt);writeUTF(r.origin.name);writeUTF(r.status.name);writeUTF(r.continuationOf ?: "") } }
        out.writeInt(book.sessions.size)
        book.sessions.forEach { s -> with(out) { writeUTF(s.id);writeUTF(s.requestKey);writeUTF(s.appId);writeUTF(s.packageName);writeUTF(s.appName)
            writeInt(s.iconRes);writeUTF(s.purpose);writeLong(s.startedAt);writeLong(s.durationMillis);writeLong(s.remainingMillis)
            writeUTF(s.status.name);writeUTF(s.outcome.name);writeUTF(s.origin.name);writeUTF(s.timingNote);writeInt(s.windows.size)
            s.windows.forEach { w -> writeLong(w.startAt);writeLong(w.endAt ?: -1);writeLong(w.creditedMillis);writeLong(w.verifiedThrough) }
            writeLong(s.endedAt ?: -1);writeUTF(s.expiryDecision.name);writeBoolean(s.expiryNotified);writeLong(s.resolvedAt ?: -1) } }
    } }.toByteArray()
    fun decode(bytes:ByteArray):IntentSessionBook=DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val version=input.readInt();require(version in 1..2)
        fun count()=input.readInt().also { require(it in 0..10000) }
        val requests=List(count()) { with(input) { IntentRequest(readUTF(),readUTF(),readUTF(),readUTF(),readInt(),readLong(),readLong(),readLong(),
            IntentOrigin.valueOf(readUTF()),IntentRequestStatus.valueOf(readUTF()),if(version>=2) readUTF().ifEmpty {null} else null) } }
        val sessions=List(count()) { with(input) {
            val id=readUTF();val key=readUTF();val app=readUTF();val pkg=readUTF();val name=readUTF();val icon=readInt();val purpose=readUTF()
            val start=readLong();val duration=readLong();val remaining=readLong();val status=SessionStatus.valueOf(readUTF())
            val outcome=IntentOutcome.valueOf(readUTF());val origin=IntentOrigin.valueOf(readUTF());val note=readUTF()
            val windows=List(count()) { SessionWindow(readLong(),readLong().takeIf { it>=0 },readLong(),readLong()) }
            require(duration in 60_000..86_400_000 && remaining in 0..duration && purpose.length in 1..200)
            val endedAt=if(version>=2) readLong().takeIf {it>=0} else null
            val decision=if(version>=2) ExpiryDecision.valueOf(readUTF()) else ExpiryDecision.None
            val notified=if(version>=2) readBoolean() else false
            val resolved=if(version>=2) readLong().takeIf {it>=0} else null
            IntentSession(id,key,app,pkg,name,icon,purpose,start,duration,remaining,status,outcome,windows,origin,note,endedAt,decision,notified,resolved)
        } }
        IntentSessionBook(requests,sessions)
    }
}
