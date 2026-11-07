package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import com.intentgate.app.R
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class IntentSessionTest {
    private class Fixture {
        var wall=LocalDate.of(2026,11,3).atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val storage=MemoryIntentSessionStorage()
        val repo=IntentSessionRepository(storage){wall}
        val app=AppDefinition("instagram","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder)
        fun request():IntentRequest {
            val record=ForegroundDetectionRecord("test-event-$wall",DetectionKind.Enter,app.id,app.packageName,wall,wall,1000,0,"Eligible",true)
            return requireNotNull(repo.offer(record,app,true,setOf(app.id)))
        }
        fun start(minutes:Int=10):IntentSession= requireNotNull(repo.start(request().key,"Reply to messages",minutes){true})
    }
    @Test fun startRecordsExactAppPurposeTimeAndDurationButIsNotCompleted() {
        val f=Fixture();val start=f.wall;val session=f.start(15)
        assertEquals("Instagram",session.appName);assertEquals("Reply to messages",session.purpose)
        assertEquals(start,session.startedAt);assertEquals(900_000L,session.remainingMillis)
        assertEquals(SessionStatus.Running,session.status);assertEquals(IntentOutcome.InProgress,session.outcome)
    }
    @Test fun cancellingCreatesNoSessionAndCannotRepeatSameOpening() {
        val f=Fixture();val request=f.request();f.repo.cancel(request.key)
        assertTrue(f.repo.state.value.sessions.isEmpty())
        assertNull(f.repo.start(request.key,"Learn something",5){true})
        val duplicate=ForegroundDetectionRecord("different-detector-id",DetectionKind.Enter,f.app.id,f.app.packageName,f.wall,f.wall,0,0,"Eligible",true)
        assertNull(f.repo.offer(duplicate,f.app,true,setOf(f.app.id)))
    }
    @Test fun doubleStartAndRecreatedDetectorEventsDoNotDuplicateSession() {
        val f=Fixture();val request=f.request()
        val first=f.repo.start(request.key,"Learn something",5){true}
        val second=f.repo.start(request.key,"Other",30){true}
        assertEquals(first,second);assertEquals(1,f.repo.state.value.sessions.size)
        assertNull(f.repo.offer(ForegroundDetectionRecord("new",DetectionKind.Enter,f.app.id,f.app.packageName,f.wall,f.wall,0,0,"Eligible",true),f.app,true,setOf(f.app.id)))
    }
    @Test fun invalidPurposeDurationExpiryAndRevocationDoNotCreateSession() {
        val f=Fixture();val request=f.request()
        assertNull(f.repo.start(request.key," ",10){true});assertNull(f.repo.start(request.key,"Work",0){true})
        assertNull(f.repo.start(request.key,"Work",1441){true});assertNull(f.repo.start(request.key,"Work",10){false})
        f.wall+=121_000;assertNull(f.repo.start(request.key,"Work",10){true})
        f.repo.expireAndValidate { false };assertNull(f.repo.state.value.pending)
        assertTrue(f.repo.state.value.sessions.isEmpty())
    }
    @Test fun staleInactiveAndDisabledDetectionsNeverOpenCheck() {
        val f=Fixture()
        val record=ForegroundDetectionRecord("fake",DetectionKind.Enter,f.app.id,f.app.packageName,f.wall-6000,f.wall,0,6000,"DetectionDelayed",true)
        assertNull(f.repo.offer(record,f.app,true,setOf(f.app.id)))
        assertNull(f.repo.offer(record.copy(eventTimeMillis=f.wall),f.app,false,setOf(f.app.id)))
        assertNull(f.repo.offer(record.copy(eventTimeMillis=f.wall),f.app,true,emptySet()))
    }
    @Test fun codecAndProcessRestorationKeepDataAndRestoreRunningPaused() {
        val f=Fixture();val session=f.start()
        f.repo.credit(session.id,session.windows,listOf(5000L to f.wall+5000))
        val before=f.repo.state.value
        val decoded=IntentSessionCodec.decode(IntentSessionCodec.encode(before))
        assertEquals(before,decoded)
        val restored=IntentSessionRepository(MemoryIntentSessionStorage(decoded)){f.wall+60_000}
        assertEquals(SessionStatus.Paused,restored.state.value.active?.status)
        assertEquals(595_000L,restored.state.value.active?.remainingMillis)
        assertEquals(f.wall+5000,restored.state.value.active?.windows?.single()?.endAt)
    }
    @Test fun onlyTargetForegroundAndUserRunningWindowsReduceTimer() {
        val f=Fixture();val started=f.wall;f.start()
        f.wall=started+10_000;f.repo.pause()
        f.wall=started+20_000;f.repo.resume()
        val session=requireNotNull(f.repo.state.value.active)
        val events=listOf(ForegroundEvent(started+1000,f.app.packageName,"Main",1),
            ForegroundEvent(started+10_000,f.app.packageName,"Main",2),
            ForegroundEvent(started+12_000,"other.app","Main",1),
            ForegroundEvent(started+22_000,f.app.packageName,"Main",1))
        val credits=sessionWindowCredits(session,events,started+25_000,ZoneOffset.UTC)
        f.repo.credit(session.id,session.windows,credits)
        assertEquals(12_000L,f.repo.state.value.active?.usedMillis)
        assertEquals(588_000L,f.repo.state.value.active?.remainingMillis)
    }
    @Test fun timerExpiryIsNotCompletionAndCompletionRequiresExplicitUserAction() {
        val f=Fixture();val session=f.start(5)
        f.repo.credit(session.id,session.windows,listOf(300_000L to f.wall+300_000))
        assertEquals(SessionStatus.Ended,f.repo.state.value.sessions.last().status)
        assertEquals(IntentOutcome.NotCompleted,f.repo.state.value.sessions.last().outcome)
        // Expiry stays unresolved until Continue or Finish; a detected opening cannot bypass it.
        assertEquals(ExpiryDecision.Pending,f.repo.state.value.sessions.last().expiryDecision)
        f.wall+=310_000
        val continuation=requireNotNull(f.repo.offerContinuation(session.id,f.app,true))
        val next=requireNotNull(f.repo.start(continuation.key,"Reply to messages",10){true})
        assertNotEquals(session.id,next.id)
        assertEquals(ExpiryDecision.Continued,f.repo.state.value.sessions.first().expiryDecision)
        f.repo.end(true,f.app.id)
        assertEquals(IntentOutcome.Completed,f.repo.state.value.sessions.last().outcome)
        assertEquals(IntentOutcome.NotCompleted,f.repo.state.value.sessions.first().outcome)
    }
    @Test fun homeAndStatsProjectStoredIntentsEvenWhenUsageAccessIsUnavailable() {
        val f=Fixture();f.start()
        val date=Instant.ofEpochMilli(f.wall).atZone(ZoneOffset.UTC).toLocalDate()
        val raw=DailyUsage(emptyMap(),0,MockIntentions.records,false,access=UsageAccess.PermissionRequired,intentStatsAvailable=false,date=date)
        var merged=projectIntentSessions(UsageSnapshot(raw),f.repo.state.value,ZoneOffset.UTC)
        assertEquals("Reply to messages",merged.today.intentions.first().purpose)
        assertFalse(merged.today.intentions.first().isDemo)
        val stats=buildUsageStats(StatsPeriod.Today,merged.today,merged.history,date)
        assertEquals(1,stats.sessions);assertEquals(0,stats.intents.completed);assertEquals(1,stats.intents.inProgress)
        assertEquals(0,stats.availableDays);assertEquals(0,stats.totalSeconds)
        f.repo.end(true);merged=projectIntentSessions(UsageSnapshot(raw),f.repo.state.value,ZoneOffset.UTC)
        assertEquals(100,buildUsageStats(StatsPeriod.Today,merged.today,merged.history,date).intents.percent)
    }
    @Test fun simulatedUiSessionsAreNeverProjectedAsRealIntents() {
        val f=Fixture();val request=f.request().copy(origin=IntentOrigin.Simulated)
        val fakeRepo=IntentSessionRepository(MemoryIntentSessionStorage(IntentSessionBook(requests=listOf(request)))){f.wall}
        fakeRepo.start(request.key,"UI test",10){true}
        val raw=DailyUsage(mapOf(f.app.id to 100),0,MockIntentions.records,false,date=LocalDate.of(2026,11,3))
        val merged=projectIntentSessions(UsageSnapshot(raw),fakeRepo.state.value,ZoneOffset.UTC)
        assertEquals(0,merged.today.sessions);assertTrue(merged.today.intentions.all { it.isDemo })
        assertEquals(raw.secondsByApp,merged.today.secondsByApp)
    }
    @Test fun switchingAppsKeepsIndependentSessionsWithoutInferringCompletion() {
        val f=Fixture();val first=f.start();f.wall+=20_000
        val other=AppDefinition("chrome","Chrome","com.android.chrome",R.drawable.ic_app_icon_placeholder)
        val record=ForegroundDetectionRecord("other",DetectionKind.Enter,other.id,other.packageName,f.wall,f.wall,0,0,"Eligible",true)
        val request=requireNotNull(f.repo.offer(record,other,true,setOf(other.id)))
        f.repo.start(request.key,"Find recipe",10){true}
        assertEquals(2,f.repo.state.value.sessions.size)
        assertEquals(IntentOutcome.InProgress,f.repo.state.value.sessions.first().outcome)
        assertEquals(first.id,f.repo.state.value.activeFor(f.app.id)?.id)
        assertEquals(SessionStatus.Running,f.repo.state.value.activeFor(f.app.id)?.status)
        assertEquals(SessionStatus.Running,f.repo.state.value.activeFor(other.id)?.status)
        assertEquals("chrome",f.repo.state.value.active?.appId)
    }
    @Test fun deniedTimingPausesWithoutChargingUnknownTimeAndStaleCreditsAreRejected() {
        val f=Fixture();val session=f.start()
        f.wall+=60_000;f.repo.suspendTiming("Revoked")
        assertEquals(600_000L,f.repo.state.value.active?.remainingMillis)
        assertEquals(SessionStatus.Paused,f.repo.state.value.active?.status)
        f.repo.credit(session.id,session.windows,listOf(60_000L to f.wall))
        assertEquals(600_000L,f.repo.state.value.active?.remainingMillis)
    }
}
