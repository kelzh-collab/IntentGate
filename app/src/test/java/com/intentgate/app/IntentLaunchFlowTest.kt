package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import java.time.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class IntentLaunchFlowTest {
    private class Fixture {
        var now=LocalDate.of(2026,11,3).atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val a=AppDefinition("org.test.a","App A","org.test.a",android.R.drawable.sym_def_app_icon,true)
        val b=AppDefinition("org.test.b","App B","org.test.b",android.R.drawable.sym_def_app_icon,true)
        val apps=LocalAppRepository(installedCatalog=listOf(a,b)).also {it.addApps(setOf(a.id,b.id))}
        var checksEnabled = true
        val storage=MemoryIntentSessionStorage()
        val sessions=IntentSessionRepository(storage, checksEnabled={checksEnabled}){now}
        val opened=mutableListOf<String>();var canOpen=true;var access=true;var committedBeforeOpen=false
        fun coordinator()=IntentLaunchCoordinator(apps,sessions,TargetAppLauncher {pkg ->
            committedBeforeOpen=sessions.state.value.requests.any {it.packageName==pkg && it.status==IntentRequestStatus.Started}
            opened+=pkg;canOpen
        }) {access}
        val flow=coordinator()
        fun request(id:String=a.id):IntentRequest {assertEquals(AppLaunchResult.CheckOpened,flow.request(id));return sessions.state.value.pending!!}
        fun start(id:String=a.id,minutes:Int=7):IntentSession {
            val request=request(id);assertEquals(AppLaunchResult.StartedAndOpened,flow.start(request.key,"Reply to messages",minutes))
            return sessions.state.value.sessions.last()
        }
    }
    @Test fun internalOpenRequiresConfirmationAndRepeatedRequestDoesNotLaunchOrCreateSession() {
        val f=Fixture();val first=f.request();val second=f.request()
        assertEquals(first.key,second.key);assertEquals(IntentOrigin.UserLaunch,first.origin)
        assertTrue(f.opened.isEmpty());assertTrue(f.sessions.state.value.sessions.isEmpty())
    }
    @Test fun explicitFormSurvivesAnotherAppsDetectedEvent() {
        val f = Fixture()
        val manual = f.request()
        val detected = ForegroundDetectionRecord("detected", DetectionKind.Enter, f.b.id, f.b.packageName,
            f.now, f.now, 0, 0, "Eligible", true)
        assertNull(f.sessions.offer(detected, f.b, true, setOf(f.b.id)))
        assertEquals(manual, f.sessions.state.value.pending)
        assertEquals(AppLaunchResult.StartedAndOpened, f.flow.start(manual.key, "Read", 5))
        assertEquals(listOf(f.a.packageName), f.opened)
    }

    @Test fun globalSwitchRejectsDetectedAndManualChecksAndDoesNotRevivePendingRequests() {
        val f = Fixture()
        val record = ForegroundDetectionRecord("detected", DetectionKind.Enter, f.a.id, f.a.packageName,
            f.now, f.now, 0, 0, "Eligible", true)
        val pending = requireNotNull(f.sessions.offer(record, f.a, true, setOf(f.a.id)))
        f.checksEnabled = false
        assertEquals(AppLaunchResult.StartFailed, f.flow.start(pending.key, "Read", 5))
        assertNull(f.sessions.start(pending.key, "Read", 5) { true })
        f.sessions.expireAndValidate { true }
        assertNull(f.sessions.state.value.pending)
        assertEquals(IntentRequestStatus.Expired, f.storage.book.requests.single().status)
        assertNull(f.sessions.offer(record, f.a, true, setOf(f.a.id)))
        assertNull(f.sessions.offerLaunch(f.a, true))
        assertEquals(AppLaunchResult.Opened, f.flow.request(f.a.id))
        assertEquals(listOf(f.a.packageName), f.opened)
        assertTrue(f.sessions.state.value.sessions.isEmpty())

        f.checksEnabled = true
        assertEquals(AppLaunchResult.StartFailed, f.flow.start(pending.key, "Read", 5))
        f.now += 1_000
        val fresh = record.copy(eventTimeMillis = f.now, detectedAtMillis = f.now)
        val request = requireNotNull(f.sessions.offer(fresh, f.a, true, setOf(f.a.id)))
        assertEquals(AppLaunchResult.StartedAndOpened, f.flow.start(request.key, "Read", 5))
    }

    @Test fun liveProjectionAndFullHistoryExcludeDemoRecordsEvenWithoutUsageAccess() {
        val f = Fixture()
        val date = Instant.ofEpochMilli(f.now).atZone(ZoneOffset.UTC).toLocalDate()
        val demo = IntentionRecord("demo", "Sample", "YouTube", 120, 0, isDemo = true)
        val usage = UsageSnapshot(DailyUsage(emptyMap(), 0, listOf(demo), false,
            access = UsageAccess.PermissionRequired, intentionsAreDemo = true, date = date))
        val empty = projectIntentSessions(usage, f.sessions.state.value, ZoneOffset.UTC).today
        assertTrue(empty.intentions.isEmpty())
        assertFalse(empty.intentionsAreDemo)
        val old = f.start()
        f.sessions.end(false)
        f.now += 86_400_000
        val newer = f.start()
        val persisted = IntentSessionCodec.decode(IntentSessionCodec.encode(f.storage.book))
        val today = projectIntentSessions(usage.copy(today = usage.today.copy(date = date.plusDays(1))),
            persisted, ZoneOffset.UTC).today
        assertEquals(listOf(newer.id), today.intentions.map { it.id })
        assertEquals(listOf(newer.id, old.id), realIntentionRecords(persisted.sessions).map { it.id })
        assertEquals(old.packageName, realIntentionRecords(persisted.sessions).last().packageName)
        assertNotNull(persisted.sessions.first().endedAt)
    }

    @Test fun cancelNeverStartsOrOpensAndInvalidRequiredFieldsCannotStart() {
        val f=Fixture();val request=f.request()
        assertEquals(AppLaunchResult.StartFailed,f.flow.start(request.key,"",10))
        assertEquals(AppLaunchResult.StartFailed,f.flow.start(request.key,"Other",0))
        f.flow.cancel(request.key)
        assertEquals(AppLaunchResult.StartFailed,f.flow.start(request.key,"Reply",10))
        assertTrue(f.opened.isEmpty());assertTrue(f.sessions.state.value.sessions.isEmpty())
    }
    @Test fun sessionCommitsBeforeTargetLaunchAndPersistedRequestPreventsRepeatedLaunch() {
        val f=Fixture();val request=f.request()
        assertEquals(AppLaunchResult.StartedAndOpened,f.flow.start(request.key,"Custom purpose",37))
        assertTrue(f.committedBeforeOpen);assertEquals(listOf(f.a.packageName),f.opened)
        assertEquals(37*60000L,f.sessions.state.value.active!!.remainingMillis)
        assertEquals(AppLaunchResult.AlreadyStarted,f.flow.start(request.key,"Custom purpose",37))
        assertEquals(AppLaunchResult.AlreadyStarted,f.coordinator().start(request.key,"Custom purpose",37))
        assertEquals(1,f.opened.size);assertEquals(1,f.sessions.state.value.sessions.size)
    }
    @Test fun disabledManagementOrCheckOpensWithoutPromptAndUnavailableAppsNeverLaunch() {
        val f=Fixture()
        f.apps.setManaged(f.a.id,false);assertEquals(AppLaunchResult.Opened,f.flow.request(f.a.id))
        f.apps.saveSettings(ManagedApp(f.b.id,true,40,false));assertEquals(AppLaunchResult.Opened,f.flow.request(f.b.id))
        assertNull(f.sessions.state.value.pending);assertTrue(f.sessions.state.value.sessions.isEmpty())
        f.apps.removeApps(setOf(f.a.id));assertEquals(AppLaunchResult.Unavailable,f.flow.request(f.a.id))
        assertEquals(2,f.opened.size)
    }
    @Test fun failedLaunchSavesPausedSessionWithoutInventedUseAndNeverRetriesFromSameRequest() {
        val f=Fixture();f.canOpen=false;val request=f.request()
        assertEquals(AppLaunchResult.LaunchFailed,f.flow.start(request.key,"Read",10))
        val session=f.sessions.state.value.activeFor(f.a.id)!!
        assertEquals(SessionStatus.Paused,session.status);assertEquals(0L,session.usedMillis)
        assertEquals(AppLaunchResult.AlreadyStarted,f.flow.start(request.key,"Read",10));assertEquals(1,f.opened.size)
    }
    @Test fun appSpecificPauseResumeEndAndRestartDoNotReplaceAnotherAppsSession() {
        val f=Fixture();val a=f.start();f.now+=1000;val b=f.start(f.b.id)
        assertEquals(a.id,f.sessions.state.value.activeFor(f.a.id)!!.id)
        f.sessions.pause(f.a.id)
        assertEquals(SessionStatus.Paused,f.sessions.state.value.activeFor(f.a.id)!!.status)
        assertEquals(SessionStatus.Running,f.sessions.state.value.activeFor(f.b.id)!!.status)
        f.sessions.resume(f.a.id);f.sessions.end(true,f.b.id)
        assertEquals(SessionStatus.Running,f.sessions.state.value.activeFor(f.a.id)!!.status)
        assertNull(f.sessions.state.value.activeFor(f.b.id))
        val restored=IntentSessionRepository(MemoryIntentSessionStorage(IntentSessionCodec.decode(IntentSessionCodec.encode(f.storage.book)))){f.now}
        assertEquals(SessionStatus.Paused,restored.state.value.activeFor(f.a.id)!!.status)
        assertEquals(IntentOrigin.UserLaunch,restored.state.value.sessions.first().origin)
        assertEquals(IntentOutcome.Completed,restored.state.value.sessions.first {it.id==b.id}.outcome)
    }
    @Test fun independentForegroundCreditsAreIdempotentAndDoNotDebitDailyUsageAgain() {
        val f=Fixture();val a=f.start();val b=f.start(f.b.id)
        val start=f.now
        val events=listOf(ForegroundEvent(start,f.a.packageName,"Main",1),ForegroundEvent(start+10000,f.a.packageName,"Main",2),
            ForegroundEvent(start+10000,f.b.packageName,"Main",1),ForegroundEvent(start+30000,f.b.packageName,"Main",2))
        for(session in listOf(a,b)) {
            val credits=sessionWindowCredits(session,events,start+30000,ZoneOffset.UTC)
            f.sessions.credit(session.id,session.windows,credits)
            f.sessions.credit(session.id,session.windows,credits)
        }
        assertEquals(10000L,f.sessions.state.value.activeFor(a.appId)!!.usedMillis)
        assertEquals(20000L,f.sessions.state.value.activeFor(b.appId)!!.usedMillis)
        val date=Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
        val raw=DailyUsage(mapOf(a.appId to 600,b.appId to 1200),0,emptyList(),false,date=date)
        val merged=projectIntentSessions(UsageSnapshot(raw),f.sessions.state.value,ZoneOffset.UTC)
        assertEquals(raw.secondsByApp,merged.today.secondsByApp);assertEquals(2,merged.today.sessions)
        assertFalse(merged.today.intentions.any {it.isDemo})
        assertEquals(2,buildUsageStats(StatsPeriod.Today,merged.today,merged.history,date).intents.inProgress)
    }
    @Test fun automaticEventsReuseDetectorCooldownAndRequireRealUsageAccess() {
        val f=Fixture();val detector=ManagedForegroundDetector()
        detector.updateTargets(listOf(ForegroundTarget(f.a.id,f.a.packageName)),f.now,0)
        val event=ForegroundEvent(f.now+1000,f.a.packageName,"Main",1);f.now+=1000
        detector.accept(listOf(event),f.now,1000,true)
        val record=detector.state.value.records.first {it.intentCheckEligible}
        val request=f.sessions.offer(record,f.a,true,detector.state.value.observedActiveApps)!!
        assertNull(f.sessions.offer(record,f.a,true,detector.state.value.observedActiveApps))
        f.access=false;assertEquals(AppLaunchResult.StartFailed,f.flow.start(request.key,"Reply",10))
        f.sessions.cancel(request.key)
        detector.accept(listOf(ForegroundEvent(f.now+100,f.a.packageName,"Main",2),ForegroundEvent(f.now+200,f.a.packageName,"Main",1)),f.now+200,1200,true)
        assertEquals(1,detector.state.value.records.count {it.intentCheckEligible})
        // A direct, visible user action may start a saved session without fabricating usage permission.
        val manual=f.request();assertEquals(AppLaunchResult.StartedAndOpened,f.flow.start(manual.key,"Reply",10))
        assertFalse(f.access)
    }
    @Test fun explicitDurationChoicesAndRemainingLimitWarningSupportCustomValues() {
        assertNull(intentCheckMinutes("",""));assertNull(intentCheckMinutes("Custom",""));assertNull(intentCheckMinutes("Custom","0"))
        assertNull(intentCheckMinutes("Custom","1441"));assertNull(intentCheckMinutes("11",""))
        for(value in listOf("5","10","15","30")) assertEquals(value.toInt(),intentCheckMinutes(value,""))
        assertEquals(187,intentCheckMinutes("Custom","187"))
        val state=DailyLimitState("a",LocalDate.of(2026,11,3),40,13,3000,LimitAvailability.Ready,true)
        assertEquals(180L,state.remainingSeconds);assertNotNull(sessionDailyLimitWarning(state,5));assertNull(sessionDailyLimitWarning(state,3))
    }
    @Test fun bilingualScopeDisclosureAndManifestDoNotClaimBackgroundInterception() {
        fun file(path:String)=listOf(File(path),File("app/$path")).first {it.exists()}
        val manifest=file("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"));assertFalse(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        for(folder in listOf("values","values-zh-rCN")) {
            val xml=file("src/main/res/$folder/strings.xml").readText()
            for(key in listOf("ig_open_app","ig_target_launch_failed_paused","ig_learn_or_research","ig_browse_to_get_inspiration","ig_trigger_scope_notice"))
                assertTrue(xml.contains("name=\"$key\""))
        }
    }
}
