package com.intentgate.app

import com.intentgate.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class ManagedForegroundMonitorTest {
    private val target=ForegroundTarget("a","pkg.a")
    private fun e(time:Long,type:Int=1,activity:String="Main",pkg:String="pkg.a")=ForegroundEvent(time,pkg,activity,type)
    private fun detector(capacity:Int=256)=ManagedForegroundDetector(journalCapacity=capacity).also { it.updateTargets(listOf(target),0,0) }
    private fun entries(d:ManagedForegroundDetector)=d.state.value.records.filter { it.kind==DetectionKind.Enter }
    private class FakeClock:DetectionClock {
        var wall=1_000_000L;var elapsed=100_000L
        override fun wallMillis()=wall
        override fun elapsedMillis()=elapsed
        fun advance(ms:Long) { wall+=ms;elapsed+=ms }
    }
    private class Fixture {
        val clock=FakeClock()
        var selected=listOf(ForegroundTarget("a","pkg.a"))
        var events=emptyList<ForegroundEvent>()
        var status=DetectionStatus.Observing
        var calls=0
        val ranges=mutableListOf<Pair<Long,Long>>()
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { from,to,packages ->
            calls++;ranges+=from to to
            ForegroundEventBatch(status,events.filter { it.time>=from && it.time<=to && (it.packageName in packages || it.type==16) })
        },{selected},clock)
        suspend fun start() { monitor.resume();monitor.poll() }
        suspend fun pollAfter(ms:Long) { clock.advance(ms);monitor.poll() }
    }
    @Test fun enabledManagementIsRequiredButIntentSwitchOnlyControlsEligibility() {
        val repo=LocalAppRepository().also { it.addApps(setOf("instagram","youtube","chrome")) }
        repo.setManaged("instagram",false)
        repo.saveSettings(repo.apps.value.first { it.appId=="youtube" }.copy(intentCheckEnabled=false))
        val targets=enabledForegroundTargets(repo)
        assertEquals(setOf("youtube","chrome"),targets.map { it.appId }.toSet())
        assertFalse(targets.first { it.appId=="youtube" }.intentCheckEnabled)
    }
    @Test fun recordsEnterLeaveAndDetectionTimestampsWithoutDuplicatePolling()=runBlocking {
        val f=Fixture();f.start()
        f.events=listOf(e(f.clock.wall+500))
        f.pollAfter(1000)
        val enter=entries(f.monitor.detector).single()
        assertEquals(1_000_500L,enter.eventTimeMillis)
        assertEquals(1_001_000L,enter.detectedAtMillis)
        assertEquals(500L,enter.delayMillis)
        assertTrue(enter.intentCheckEligible)
        f.pollAfter(200)
        assertEquals(1,entries(f.monitor.detector).size)
        f.events=f.events+e(f.clock.wall+300,2)
        f.pollAfter(500)
        assertEquals(1,f.monitor.state.value.records.count { it.kind==DetectionKind.Leave })
        assertTrue(f.monitor.state.value.observedActiveApps.isEmpty())
    }
    @Test fun duplicateResumeAndSameTimestampActivityHandoffDoNotRetrigger() {
        val d=detector()
        d.accept(listOf(e(1000),e(1000),e(1500),e(2000,2),e(2000,1,"Other")),2500,2500,true)
        assertEquals(1,entries(d).size)
        assertEquals(setOf("a"),d.state.value.observedActiveApps)
        d.accept(listOf(e(3000,2,"Other"),e(3100,23,"Other")),3500,3500,true)
        assertEquals(1,d.state.value.records.count { it.kind==DetectionKind.Leave })
    }
    @Test fun cooldownSuppressesRapidReentryAndAllowsLaterEntry() {
        val d=detector()
        d.accept(listOf(e(1000)),1500,1500,true)
        d.accept(listOf(e(2000,2),e(2200)),2500,2500,true)
        assertEquals(listOf(true,false),entries(d).map { it.intentCheckEligible })
        assertEquals("Cooldown",entries(d).last().reason)
        d.accept(listOf(e(12000,2),e(13000)),13500,13500,true)
        assertTrue(entries(d).last().intentCheckEligible)
    }
    @Test fun aCompletedHistoricalVisitAndStaleEntryNeverRequestIntent() {
        val d=detector()
        d.accept(listOf(e(1000),e(2000,2)),3000,3000,true)
        assertEquals("AlreadyLeft",entries(d).single().reason)
        d.accept(listOf(e(4000)),12000,12000,true)
        assertEquals("DetectionDelayed",entries(d).last().reason)
        assertFalse(entries(d).last().intentCheckEligible)
    }
    @Test fun intentDisabledStillRecordsForegroundAndUnmanagedAppsAreIgnored() {
        val d=detector();d.updateTargets(listOf(target.copy(intentCheckEnabled=false)),0,0)
        d.accept(listOf(e(500,pkg="unmanaged"),e(1000)),1500,1500,true)
        assertEquals(1,entries(d).size)
        assertEquals("IntentCheckDisabled",entries(d).single().reason)
        d.updateTargets(emptyList(),2000,2000)
        assertTrue(d.state.value.observedActiveApps.isEmpty())
        assertEquals("ManagementDisabled",d.state.value.records.last().reason)
        d.accept(listOf(e(2500)),3000,3000,true)
        assertEquals(1,entries(d).size)
    }
    @Test fun enablingAnAppDoesNotReplayEventsFromBeforeItWasEnabled() {
        val d=ManagedForegroundDetector()
        d.updateTargets(listOf(target),5000,5000)
        d.accept(listOf(e(4000)),5500,5500,true)
        assertTrue(entries(d).isEmpty())
        d.accept(listOf(e(6000)),6500,6500,true)
        assertTrue(entries(d).single().intentCheckEligible)
    }
    @Test fun permissionRevocationClearsProtectedStateAndRegrantStartsWithoutStaleTrigger()=runBlocking {
        val f=Fixture();f.start();f.events=listOf(e(f.clock.wall+500));f.pollAfter(1000)
        f.status=DetectionStatus.PermissionRequired;f.pollAfter(1000)
        assertEquals(DetectionStatus.PermissionRequired,f.monitor.state.value.status)
        assertTrue(f.monitor.state.value.observedActiveApps.isEmpty())
        assertTrue(f.monitor.state.value.records.none { it.packageName!=null })
        f.status=DetectionStatus.Observing;f.events=f.events+e(f.clock.wall+500)
        f.pollAfter(1000)
        assertTrue(entries(f.monitor.detector).none { it.intentCheckEligible })
    }
    @Test fun backgroundStopsReadsAndReplayKeepsHistoricalEventTimesWithoutChecks()=runBlocking {
        val f=Fixture();f.start();val calls=f.calls
        f.monitor.pause();f.events=listOf(e(f.clock.wall+500),e(f.clock.wall+1500,2))
        f.pollAfter(2000);assertEquals(calls,f.calls)
        assertEquals(DetectionStatus.Suspended,f.monitor.state.value.status)
        f.monitor.resume();f.monitor.poll()
        val records=f.monitor.state.value.records.filter { it.eventTimeMillis!=null }
        assertEquals(listOf(DetectionKind.Enter,DetectionKind.Leave),records.map { it.kind })
        assertTrue(records.all { !it.intentCheckEligible })
        assertEquals(1_000_500L,records.first().eventTimeMillis)
        assertEquals(1_002_000L,records.first().detectedAtMillis)
    }
    @Test fun longGapAndWallClockJumpAreExplicitAndNeverInventAnExit()=runBlocking {
        val f=Fixture();f.start();f.events=listOf(e(f.clock.wall+500));f.pollAfter(1000)
        f.monitor.pause();f.clock.advance(400_000);f.monitor.resume();f.monitor.poll()
        assertTrue(f.monitor.state.value.records.any { it.reason=="ClockOrObservationGap" })
        assertTrue(f.monitor.state.value.records.none { it.kind==DetectionKind.Leave })
        assertTrue(f.ranges.last().second-f.ranges.last().first<=300_000)
        f.clock.wall-=60_000;f.clock.elapsed+=1000;f.monitor.poll()
        assertEquals(2,f.monitor.state.value.records.count { it.reason=="ClockOrObservationGap" })
    }
    @Test fun screenOffClosesKnownAppsAndBoundedJournalReportsDrops() {
        val d=detector(3)
        d.accept(listOf(e(1000)),1500,1500,true)
        d.accept(listOf(e(2000,16,pkg="")),2500,2500,true)
        assertEquals("SystemBoundary",d.state.value.records.last().reason)
        d.accept(listOf(e(3000),e(4000,2),e(5000)),5500,5500,true)
        assertEquals(3,d.state.value.records.size)
        assertTrue(d.state.value.droppedRecords>0)
        assertEquals(setOf("a"),d.state.value.observedActiveApps)
    }
    @Test fun lateEventsLoseContinuityButCannotReplayIdenticalEnterEventsAgain() {
        val d=detector()
        d.accept(listOf(e(2000)),2500,2500,true)
        d.accept(listOf(e(1000),e(2000)),3000,3000,true)
        val size=d.state.value.records.size
        assertTrue(d.state.value.records.any { it.reason=="LateEventContinuityLost" })
        d.accept(listOf(e(1000),e(2000)),3500,3500,true)
        assertEquals(size,d.state.value.records.size)
    }
    @Test fun sourceExceptionsAndLockedStateAreRecoverableWithoutFalseLeave()=runBlocking {
        val clock=FakeClock()
        var fail=true
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { _,_,_ ->
            if(fail) throw SecurityException("revoked") else ForegroundEventBatch(DetectionStatus.Locked)
        },{listOf(target)},clock)
        monitor.resume();monitor.poll()
        assertEquals(DetectionStatus.PermissionRequired,monitor.state.value.status)
        fail=false;clock.advance(1000);monitor.poll()
        assertEquals(DetectionStatus.Locked,monitor.state.value.status)
        assertTrue(monitor.state.value.records.none { it.kind==DetectionKind.Leave })
    }
    @Test fun aResultArrivingAfterBackgroundPauseCannotPublishLiveEntries()=runBlocking {
        val clock=FakeClock()
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { _,_,_ ->
            started.complete(Unit);release.await()
            ForegroundEventBatch(DetectionStatus.Observing,listOf(e(clock.wall)))
        },{listOf(target)},clock)
        monitor.resume();val job=launch { monitor.poll() }
        started.await();monitor.pause();release.complete(Unit);job.join()
        assertEquals(DetectionStatus.Suspended,monitor.state.value.status)
        assertTrue(entries(monitor.detector).isEmpty())
    }
    @Test fun disablingManagementDuringReadDiscardsThePackagesEvents()=runBlocking {
        val clock=FakeClock();var selected=listOf(target)
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { _,_,_ ->
            selected=emptyList()
            ForegroundEventBatch(DetectionStatus.Observing,listOf(e(clock.wall)))
        },{selected},clock)
        monitor.resume();monitor.poll()
        assertTrue(monitor.state.value.observedActiveApps.isEmpty())
        assertTrue(entries(monitor.detector).isEmpty())
    }
    @Test fun rollbackRebasesActivationBoundaryAndClockChangeDuringReadSuppressesChecks()=runBlocking {
        val f=Fixture();f.start();f.clock.wall-=60_000;f.clock.elapsed+=1000
        f.monitor.poll()
        f.events=listOf(e(f.clock.wall+500));f.pollAfter(1000)
        assertTrue(entries(f.monitor.detector).single().intentCheckEligible)
        val clock=FakeClock()
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { _,_,_ ->
            clock.wall-=60_000
            ForegroundEventBatch(DetectionStatus.Observing,listOf(e(clock.wall)))
        },{listOf(target)},clock)
        monitor.resume();monitor.poll()
        assertEquals("ClockChangedDuringRead",monitor.state.value.records.last().reason)
        assertTrue(entries(monitor.detector).isEmpty())
    }

    @Test fun pauseResumeGenerationRejectsOldInFlightResults()=runBlocking {
        val clock=FakeClock();val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val monitor=ManagedForegroundMonitor(ForegroundEventSource { _,_,_ ->
            started.complete(Unit);release.await()
            ForegroundEventBatch(DetectionStatus.Observing,listOf(e(clock.wall)))
        },{listOf(target)},clock)
        monitor.resume();val job=launch { monitor.poll() }
        started.await();monitor.pause();monitor.resume();release.complete(Unit);job.join()
        assertTrue(entries(monitor.detector).isEmpty())
        assertEquals(DetectionStatus.Suspended,monitor.state.value.status)
    }

}
