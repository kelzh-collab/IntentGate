package com.intentgate.app

import com.intentgate.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ForegroundUsageTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2026,11,3)
    private val start=date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val apps=mapOf("pkg.a" to "a","pkg.b" to "b")
    private fun e(seconds:Long,pkg:String="pkg.a",activity:String="Main",type:Int=1)=ForegroundEvent(start+seconds*1000,pkg,activity,type)
    private fun total(events:List<ForegroundEvent>,end:Long=100)=foregroundUsageDays(events,apps,start,start+end*1000,zone)[date].orEmpty()

    @Test fun countsResumedTimeButNeverBackgroundServicesOrIntentSessions() {
        assertEquals(mapOf("a" to 25),total(listOf(e(5),e(30,type=2),e(40,type=19),e(60,type=20))))
    }
    @Test fun overlappingActivitiesAreAUnionAndDuplicateResumeDoesNotResetStart() {
        assertEquals(mapOf("a" to 40),total(listOf(e(0),e(10),e(15,activity="Other"),e(20,type=2),e(40,activity="Other",type=2))))
    }
    @Test fun midnightClippingSplittingAndStillOpenIntervals() {
        val events=listOf(e(-20),e(10,type=2),e(30,pkg="pkg.b"))
        val days=foregroundUsageDays(events,apps,start-86400_000,start+50_000,zone)
        assertEquals(20,days[date.minusDays(1)]?.get("a"))
        assertEquals(mapOf("a" to 10,"b" to 20),days[date])
        assertEquals(mapOf("a" to 10,"b" to 20),total(events,50))
    }
    @Test fun screenOffKeyguardAndRebootCloseOpenTime() {
        for(type in listOf(16,17,26,27)) assertEquals(mapOf("a" to 5),total(listOf(e(0),e(5,pkg="",type=type))))
    }
    @Test fun unselectedPackagesAndUnmatchedPauseDoNotInventUsage() {
        assertTrue(total(listOf(e(5,type=2),e(10,pkg="not.selected"))).isEmpty())
    }
    @Test fun dayLengthFollowsTimezoneInsteadOfAssuming24Hours() {
        val z=ZoneId.of("America/New_York")
        val day=LocalDate.of(2026,11,1)
        val begin=day.atStartOfDay(z).toInstant().toEpochMilli()
        val end=day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        assertEquals(25*3600,foregroundUsageDays(listOf(ForegroundEvent(begin,"pkg.a","Main",1)),apps,begin,end,z)[day]?.get("a"))
    }
    @Test fun oneCanonicalRealSnapshotFeedsAllTotalsWithoutInventingIntentOutcomes() {
        val today=DailyUsage(mapOf("a" to 75,"b" to 45),0,MockIntentions.records,false,
            intentStatsAvailable=false,intentionsAreDemo=true,date=date)
        val snapshot=UsageSnapshot(today)
        val stats=buildUsageStats(StatsPeriod.Today,snapshot.today,snapshot.history,date)
        assertEquals(120,today.totalSeconds)
        assertEquals(today.totalSeconds,stats.totalSeconds)
        assertEquals(today.secondsByApp,stats.secondsByApp)
        assertFalse(stats.intents.outcomesAvailable)
        assertNull(stats.intents.percent)
        assertFalse(today.isMock)
        assertTrue(today.intentionsAreDemo)
    }
    @Test fun revokedNoDataAndLockedSnapshotsDoNotMasqueradeAsMeasuredZero() {
        for(access in listOf(UsageAccess.PermissionRequired,UsageAccess.NoData,UsageAccess.Locked,UsageAccess.Error)) {
            val today=DailyUsage(emptyMap(),0,MockIntentions.records,false,access=access,intentStatsAvailable=false)
            assertFalse(today.available)
            val stats=buildUsageStats(StatsPeriod.Today,today,emptyList(),date)
            assertEquals(0,stats.availableDays)
            assertNull(stats.trends.single().seconds)
            assertNull(stats.intents.percent)
        }
    }
}
