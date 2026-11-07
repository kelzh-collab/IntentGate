package com.intentgate.app.data

import android.content.Context
import android.util.Log
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

class PreferencesIntentSessionStorage(context:Context):IntentSessionStorage {
    private val prefs=context.applicationContext.getSharedPreferences("intent_sessions",Context.MODE_PRIVATE)
    override fun read():IntentSessionBook = try {
        prefs.getString("book",null)?.let { migrateSessionAppIds(IntentSessionCodec.decode(Base64.getDecoder().decode(it))) } ?: IntentSessionBook()
    } catch(_:RuntimeException) { IntentSessionBook() } catch(_:java.io.IOException) { IntentSessionBook() }
    override fun write(book:IntentSessionBook) {
        check(prefs.edit().putString("book",Base64.getEncoder().encodeToString(IntentSessionCodec.encode(book))).commit()) { "Could not save Intent sessions" }
    }
}

/** Session-specific query windows. Never subtract a whole-app daily aggregate from the session timer. */
class AndroidIntentSessionTiming(context:Context,private val sessions:IntentSessionRepository) {
    private val contextForTiming = context.applicationContext
    private val source=AndroidForegroundEventSource(contextForTiming, includeOtherResumes = true)
    private var lastReadStatus: DetectionStatus? = null
    private var previousWall:Long?=null
    private var previousElapsed:Long?=null
    // Return the foreground package from the same snapshot used to verify elapsed time.
    suspend fun refresh(): String? {
        val candidates=sessions.state.value.sessions.filter { it.status!=com.intentgate.app.session.SessionStatus.Ended && it.origin!=IntentOrigin.Simulated }
        if(candidates.isEmpty()) return null
        val wall=System.currentTimeMillis();val elapsed=android.os.SystemClock.elapsedRealtime()
        val oldWall=previousWall;val oldElapsed=previousElapsed
        previousWall=wall;previousElapsed=elapsed
        if(oldWall!=null && oldElapsed!=null && kotlin.math.abs((wall-oldWall)-(elapsed-oldElapsed))>5000) {
            Log.w(EXPIRY_LOG, "timing_paused reason=clock_changed")
            candidates.forEach {sessions.suspendTiming("@string/ig_device_time_changed_resume_manually",it.appId)};return null
        }
        val needed=candidates.mapNotNull { session ->
            val pending=session.windows.filter { it.endAt==null || it.verifiedThrough<it.endAt }
            if(pending.isEmpty()) return@mapNotNull null
            val start=pending.minOf {it.startAt}
            if(wall-start>2*86_400_000L) {
                sessions.suspendTiming("@string/ig_session_records_too_old_to_verify",session.appId);null
            } else session to start
        }
        if(needed.isEmpty()) return null
        // One event query, separate app/window credits. Never use the daily aggregate as a session debit.
        val result=try {source.read(needed.minOf {it.second}-86_400_000L,wall,needed.map {it.first.packageName}.toSet())}
            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:RuntimeException) {ForegroundEventBatch(DetectionStatus.Error)}
        if (result.status != lastReadStatus) {
            if (result.status != DetectionStatus.Observing) Log.w(EXPIRY_LOG, "timing_unavailable reason=" + result.status)
            lastReadStatus = result.status
        }
        if(result.status!=DetectionStatus.Observing) {
            if (result.status == DetectionStatus.PermissionRequired) {
                needed.forEach { sessions.suspendTiming("@string/ig_usage_access_or_system_records_unavailable", it.first.appId) }
            }
            // A temporary system read error does not turn a running session into a manual pause.
            return null
        }
        val (foreground, credits) = withContext(Dispatchers.Default) {
            latestForegroundResume(result.events)?.packageName to needed.map { (session, _) ->
                session to sessionWindowCredits(session, result.events, wall, ZoneId.systemDefault())
            }
        }
        if (!hasUsageAccess(contextForTiming)) {
            needed.forEach { sessions.suspendTiming("@string/ig_usage_access_or_system_records_unavailable", it.first.appId) }
            return null
        }
        credits.forEach { (session, credit) ->
            sessions.credit(session.id, session.windows, credit)
        }
        return foreground
    }
}

internal const val EXPIRY_LOG = "IntentGateExpiry"
