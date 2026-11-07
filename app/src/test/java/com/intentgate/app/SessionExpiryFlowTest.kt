package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import java.time.*
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class SessionExpiryFlowTest {
    private class Fixture {
        var now=LocalDate.of(2026,11,3).atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val app=AppDefinition("org.test.a","App A","org.test.a",android.R.drawable.sym_def_app_icon,true)
        val b=AppDefinition("org.test.b","App B","org.test.b",android.R.drawable.sym_def_app_icon,true)
        val apps=LocalAppRepository(installedCatalog=listOf(app,b)).also {it.addApps(setOf(app.id,b.id))}
        var checksEnabled = true
        val storage=MemoryIntentSessionStorage()
        val sessions=IntentSessionRepository(storage, checksEnabled={checksEnabled}){now}
        val date=LocalDate.of(2026,11,3)
        var balance=DailyLimitState(app.id,date,40,0,1200L,LimitAvailability.Ready,true)
        var opened=0
        fun flow(repo:IntentSessionRepository=sessions)=IntentLaunchCoordinator(apps,repo,TargetAppLauncher {opened++;true}){true}.also {
            it.continuationAllowed={_,minutes -> canContinueWithinLimit(balance,minutes,date)}
        }
        val launch=flow()
        fun start(appId:String=app.id,minutes:Int=5):IntentSession {
            assertEquals(AppLaunchResult.CheckOpened,launch.request(appId))
            val request=sessions.state.value.pending!!
            assertEquals(AppLaunchResult.StartedAndOpened,launch.start(request.key,"Read",minutes))
            return sessions.state.value.activeFor(appId)!!
        }
        fun expire(session:IntentSession):IntentSession {
            now+=session.durationMillis
            sessions.credit(session.id,session.windows,listOf(session.durationMillis to now))
            return sessions.state.value.sessions.first {it.id==session.id}
        }
        fun restore()=IntentSessionRepository(MemoryIntentSessionStorage(IntentSessionCodec.decode(IntentSessionCodec.encode(storage.book)))){now}
    }
    @Test fun expiryIsDurableOnceOnlyAndStopsCountingWithoutInferringCompletion() {
        val f=Fixture();val start=f.start();val expired=f.expire(start)
        assertEquals(SessionStatus.Ended,expired.status);assertEquals(0L,expired.remainingMillis)
        assertEquals(ExpiryDecision.Pending,expired.expiryDecision);assertEquals(f.now,expired.endedAt)
        assertEquals(IntentOutcome.NotCompleted,expired.outcome);assertNotNull(expired.windows.single().endAt)
        f.now+=900_000
        f.sessions.credit(start.id,start.windows,listOf(999_000L to f.now))
        assertEquals(expired,f.sessions.state.value.sessions.single())
        var delivered=0
        assertTrue(f.sessions.deliverExpiry(start.id){delivered++;true})
        assertFalse(f.sessions.deliverExpiry(start.id){delivered++;true})
        assertEquals(1,delivered);assertEquals(1,f.sessions.state.value.sessions.size)
        val restored=f.restore()
        assertEquals(start.id,restored.state.value.pendingExpiry!!.id)
        assertFalse(restored.deliverExpiry(start.id){delivered++;true})
        assertEquals(1,delivered)
    }

    @Test fun globalSwitchKeepsActiveSessionAndItsExpiryAndFinish() {
        val f = Fixture()
        val session = f.start()
        f.checksEnabled = false
        f.sessions.expireAndValidate { true }
        assertEquals(session, f.sessions.state.value.active)
        val expired = f.expire(session)
        assertEquals(ExpiryDecision.Pending, expired.expiryDecision)
        assertTrue(f.sessions.deliverExpiry(session.id) { true })
        assertFalse(f.launch.continueExpired(session.id))
        assertNull(f.sessions.state.value.pending)
        assertTrue(f.sessions.finishExpired(session.id))
        assertEquals(ExpiryDecision.Finished, f.sessions.state.value.sessions.single().expiryDecision)
    }

    @Test fun disablingWhileContinuationIsPendingRestoresExpiryAndReenablingAllowsFreshContinue() {
        val f = Fixture()
        val expired = f.expire(f.start())
        assertTrue(f.launch.continueExpired(expired.id))
        val oldRequest = f.sessions.state.value.pending!!
        f.checksEnabled = false
        f.sessions.expireAndValidate { true }
        assertNull(f.sessions.state.value.pending)
        assertEquals(expired, f.sessions.state.value.pendingExpiry)
        assertEquals(AppLaunchResult.StartFailed, f.launch.start(oldRequest.key, "Read", 5))

        f.checksEnabled = true
        assertTrue(f.launch.continueExpired(expired.id))
        val newRequest = f.sessions.state.value.pending!!
        assertNotEquals(oldRequest.key, newRequest.key)
        assertEquals(AppLaunchResult.StartedAndOpened, f.launch.start(newRequest.key, "Read", 5))
        assertEquals(ExpiryDecision.Continued, f.sessions.state.value.sessions.first().expiryDecision)
        assertEquals(2, f.sessions.state.value.sessions.size)
    }
    @Test fun backgroundCompositionCannotRemoveOverlayOrConsumeNotificationFallback() {
        val f=Fixture();val old=f.expire(f.start())
        var overlayVisible=true;var notificationCancelled=false
        assertFalse(presentExpiryInForeground(f.sessions,old.id,false,
            {overlayVisible=false},{notificationCancelled=true}))
        assertTrue(overlayVisible);assertFalse(notificationCancelled)
        assertFalse(f.sessions.state.value.pendingExpiry!!.expiryNotified)
        assertFalse(f.restore().state.value.pendingExpiry!!.expiryNotified)
        var notifications=0
        assertTrue(f.sessions.deliverExpiry(old.id){notifications++;true})
        assertFalse(f.sessions.deliverExpiry(old.id){notifications++;true})
        assertEquals(1,notifications)
    }
    @Test fun returningToForegroundPresentsSavedExpiryWithoutResolvingOrDuplicatingSession() {
        val f=Fixture();val old=f.expire(f.start());val restored=f.restore()
        val events=mutableListOf<String>()
        assertFalse(presentExpiryInForeground(restored,old.id,false,
            {events+="overlay_removed"},{events+="notification_cancelled"}))
        assertTrue(events.isEmpty())
        assertTrue(presentExpiryInForeground(restored,old.id,true,
            {assertFalse(restored.state.value.pendingExpiry!!.expiryNotified);events+="overlay_removed"},
            {assertTrue(restored.state.value.pendingExpiry!!.expiryNotified);events+="notification_cancelled"}))
        assertEquals(listOf("overlay_removed","notification_cancelled"),events)
        assertEquals(ExpiryDecision.Pending,restored.state.value.pendingExpiry!!.expiryDecision)
        assertEquals(1,restored.state.value.sessions.size)
        assertFalse(restored.deliverExpiry(old.id){fail("Foreground presentation already acknowledged");true})
        assertTrue(restored.finishExpired(old.id))
        assertEquals(ExpiryDecision.Finished,restored.state.value.sessions.single().expiryDecision)
    }
    @Test fun deniedNotificationDoesNotLoseConfirmationOrCreateASession() {
        val f=Fixture();val old=f.expire(f.start())
        assertFalse(f.sessions.deliverExpiry(old.id){false})
        assertFalse(f.sessions.state.value.pendingExpiry!!.expiryNotified)
        f.sessions.acknowledgeExpiry(old.id)
        assertFalse(f.sessions.deliverExpiry(old.id){fail("Already presented in foreground");true})
        assertEquals(1,f.sessions.state.value.sessions.size)
    }
    @Test fun continueRequiresFreshChoicesAndCreatesANewRecordOnlyAfterStart() {
        val f=Fixture();val old=f.expire(f.start())
        assertTrue(f.launch.continueExpired(old.id));val request=f.sessions.state.value.pending!!
        assertEquals(old.id,request.continuationOf);assertEquals(1,f.sessions.state.value.sessions.size)
        assertTrue(f.launch.continueExpired(old.id));assertEquals(request.key,f.sessions.state.value.pending!!.key)
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(request.key,"",5))
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(request.key,"New purpose",0))
        assertEquals(AppLaunchResult.StartedAndOpened,f.launch.start(request.key,"New purpose",10))
        val book=f.sessions.state.value;val previous=book.sessions.first {it.id==old.id};val current=book.activeFor(old.appId)!!
        assertEquals(ExpiryDecision.Continued,previous.expiryDecision);assertEquals(old.startedAt,previous.startedAt)
        assertEquals(old.endedAt,previous.endedAt);assertEquals(old.purpose,previous.purpose)
        assertNotEquals(old.id,current.id);assertEquals("New purpose",current.purpose);assertEquals(600_000L,current.remainingMillis)
        assertEquals(AppLaunchResult.AlreadyStarted,f.launch.start(request.key,"Duplicate",10));assertEquals(2,f.opened)
        assertEquals(2,book.sessions.size)
    }
    @Test fun cancelOrIgnoredContinuationNeverStartsAndCanBeReopenedAfterRestart() {
        val f=Fixture();val old=f.expire(f.start());f.launch.continueExpired(old.id)
        val key=f.sessions.state.value.pending!!.key
        f.launch.cancel(key)
        assertEquals(old.id,f.sessions.state.value.pendingExpiry!!.id)
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(key,"No",5))
        assertEquals(1,f.opened);assertEquals(1,f.sessions.state.value.sessions.size)
        f.launch.continueExpired(old.id);f.now+=121_000
        val restored=f.restore();restored.expireAndValidate {true}
        assertNull(restored.state.value.pending);assertEquals(old.id,restored.state.value.pendingExpiry!!.id)
        assertEquals(1,restored.state.value.sessions.size);assertNull(restored.state.value.active)
    }
    @Test fun finishEndsOnlyTheChosenAppSavesResolutionAndCannotBeResumed() {
        val f=Fixture();val a=f.start();val b=f.start(f.b.id);val old=f.expire(a)
        f.launch.continueExpired(old.id);val request=f.sessions.state.value.pending!!
        f.now+=3000;assertTrue(f.sessions.finishExpired(old.id));assertFalse(f.sessions.finishExpired(old.id))
        val ended=f.sessions.state.value.sessions.first {it.id==old.id}
        assertEquals(SessionStatus.Ended,ended.status);assertEquals(ExpiryDecision.Finished,ended.expiryDecision)
        assertEquals(f.now,ended.resolvedAt);assertEquals(old.endedAt,ended.endedAt)
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(request.key,"No",5))
        f.sessions.resume(f.app.id);assertNull(f.sessions.state.value.activeFor(f.app.id))
        assertEquals(b.id,f.sessions.state.value.activeFor(f.b.id)!!.id)
        assertEquals(ExpiryDecision.Finished,f.restore().state.value.sessions.first().expiryDecision)
    }
    @Test fun dailyBudgetBlocksOverrunAndUnavailableDataWithoutImplicitExtension() {
        val f=Fixture();val old=f.expire(f.start());f.launch.continueExpired(old.id)
        val key=f.sessions.state.value.pending!!.key
        f.balance=f.balance.copy(baseMinutes=30,usedSeconds=1800)
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(key,"More",5));assertEquals(0L,f.balance.extraMinutes)
        // Only a separately recorded daily extension changes eligibility.
        f.balance=f.balance.copy(extraMinutes=10)
        assertEquals(AppLaunchResult.StartFailed,f.launch.start(key,"More",15))
        val valid=f.balance
        for(state in listOf(valid.copy(availability=LimitAvailability.PermissionRequired,usedSeconds=null),
            valid.copy(availability=LimitAvailability.Demo),valid.copy(date=f.date.minusDays(1)),valid.copy(managed=false))) {
            f.balance=state;assertEquals(AppLaunchResult.StartFailed,f.launch.start(key,"More",5))
        }
        assertFalse(canContinueWithinLimit(null,5,f.date))
        f.balance=valid;assertEquals(AppLaunchResult.StartedAndOpened,f.launch.start(key,"Within explicit extension",10))
        assertEquals(10L,f.balance.extraMinutes)
    }
    @Test fun repeatDetectionsCannotBypassPendingConfirmationOrDisabledIntentCheck() {
        val f=Fixture();val old=f.expire(f.start())
        val record=ForegroundDetectionRecord("repeat",DetectionKind.Enter,f.app.id,f.app.packageName,f.now,f.now,0,0,"Eligible",true)
        assertNull(f.sessions.offer(record,f.app,true,setOf(f.app.id)))
        assertEquals(AppLaunchResult.CheckOpened,f.launch.request(f.app.id));assertNull(f.sessions.state.value.pending)
        assertEquals(1,f.opened)
        val config=f.apps.apps.value.first {it.appId==f.app.id}
        f.apps.saveSettings(config.copy(intentCheckEnabled=false))
        assertFalse(f.launch.continueExpired(old.id));assertEquals(1,f.sessions.state.value.sessions.size)
    }
    @Test fun simultaneousExpiriesRemainIndependentAndHomeStatsPreserveHistory() {
        val f=Fixture();val a=f.start();val b=f.start(f.b.id)
        f.expire(a);f.expire(b)
        assertEquals(a.id,f.sessions.state.value.pendingExpiry!!.id)
        f.sessions.finishExpired(a.id);assertEquals(b.id,f.sessions.state.value.pendingExpiry!!.id)
        val raw=DailyUsage(mapOf(a.appId to 600,b.appId to 800),0,emptyList(),false,date=f.date)
        val merged=projectIntentSessions(UsageSnapshot(raw),f.sessions.state.value,ZoneOffset.UTC)
        assertEquals(raw.secondsByApp,merged.today.secondsByApp);assertEquals(2,merged.today.intentions.size)
        val stats=buildUsageStats(StatsPeriod.Today,merged.today,merged.history,f.date)
        assertEquals(2,stats.sessions);assertEquals(0,stats.intents.completed);assertEquals(0,stats.intents.inProgress)
    }
    @Test fun checkingRestoresItsLinkAndPermissionRevocationCannotCreateAnotherSession() {
        val f=Fixture();val old=f.expire(f.start());f.launch.continueExpired(old.id)
        val restored=f.restore();val key=restored.state.value.pending!!.key
        assertEquals(old.id,restored.state.value.pending!!.continuationOf)
        val flow=f.flow(restored);f.balance=f.balance.copy(usedSeconds=null,availability=LimitAvailability.PermissionRequired)
        assertEquals(AppLaunchResult.StartFailed,flow.start(key,"More",5))
        restored.expireAndValidate {false}
        assertEquals(old.id,restored.state.value.pendingExpiry!!.id);assertEquals(1,restored.state.value.sessions.size)
    }
    @Test fun legacyCodecLoadsWithoutAlertingOldExpiredHistory() {
        val f=Fixture();val expired=f.expire(f.start())
        val bytes=ByteArrayOutputStream().also {buffer -> DataOutputStream(buffer).use {out -> with(out) {
            writeInt(1);writeInt(0);writeInt(1)
            writeUTF(expired.id);writeUTF(expired.requestKey);writeUTF(expired.appId);writeUTF(expired.packageName);writeUTF(expired.appName)
            writeInt(expired.iconRes);writeUTF(expired.purpose);writeLong(expired.startedAt);writeLong(expired.durationMillis);writeLong(0)
            writeUTF(SessionStatus.Ended.name);writeUTF(IntentOutcome.NotCompleted.name);writeUTF(IntentOrigin.UserLaunch.name)
            writeUTF(expired.timingNote);writeInt(0)
        }} }.toByteArray()
        val book=IntentSessionCodec.decode(bytes)
        assertEquals(1,book.sessions.size);assertEquals(ExpiryDecision.None,book.sessions.single().expiryDecision)
        assertNull(book.pendingExpiry);assertEquals(book,IntentSessionCodec.decode(IntentSessionCodec.encode(book)))
    }
    @Test fun foregroundCreditsOnlyAndCustomDurationStillWorkAfterContinuation() {
        val f=Fixture();f.balance=f.balance.copy(baseMinutes=240,usedSeconds=0)
        val old=f.expire(f.start());f.launch.continueExpired(old.id)
        val key=f.sessions.state.value.pending!!.key
        assertNull(intentCheckMinutes("",""));assertEquals(187,intentCheckMinutes("Custom","187"))
        assertEquals(AppLaunchResult.StartedAndOpened,f.launch.start(key,"Long study",187))
        val session=f.sessions.state.value.active!!;val start=f.now
        val events=listOf(ForegroundEvent(start,session.packageName,"Main",1),
            ForegroundEvent(start+10000,session.packageName,"Main",2),ForegroundEvent(start+10000,"other.app","Main",1))
        f.now+=60_000
        f.sessions.credit(session.id,session.windows,sessionWindowCredits(session,events,f.now,ZoneOffset.UTC))
        assertEquals(10000L,f.sessions.state.value.active!!.usedMillis)
        assertEquals(0L,f.balance.usedSeconds)
    }
    @Test fun expiryCopyIsBilingualAndNeedsNoForegroundServiceOrExactAlarm() {
        fun file(path:String)=listOf(File(path),File("app/$path")).first {it.exists()}
        val manifest=file("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"));assertTrue(manifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(manifest.contains("android.permission.FOREGROUND_SERVICE"));assertFalse(manifest.contains("SCHEDULE_EXACT_ALARM"))
        for(folder in listOf("values","values-zh-rCN")) {
            val xml=file("src/main/res/$folder/strings.xml").readText()
            for(key in listOf("ig_continue_question","ig_finish_use","ig_session_expired_notification","ig_continuation_limit_required","ig_expiry_background_notice"))
                assertTrue(xml.contains("name=\"$key\""))
        }
    }
}
