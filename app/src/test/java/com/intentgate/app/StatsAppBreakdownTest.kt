package com.intentgate.app

import com.intentgate.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StatsAppBreakdownTest {
    private val zone=ZoneOffset.UTC
    private val date=LocalDate.of(2026,11,6)
    private val now=date.atTime(12,0).toInstant(zone).toEpochMilli()
    private val catalog=(1..6).map { AppDefinition("alias$it","App $it","app.$it",0,true) }
    private fun events(day:LocalDate,pkg:String,seconds:Int):List<ForegroundEvent> {
        val start=day.atTime(9,0).toInstant(zone).toEpochMilli()
        return listOf(ForegroundEvent(start,pkg,"Main",1),
            ForegroundEvent(start+seconds*1000L,pkg,"Main",2))
    }
    private fun data(events:List<ForegroundEvent>,apps:List<AppDefinition> = catalog) =
        statsBreakdownFromEvents(events,apps,date.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli(),now,zone)

    @Test fun allInstalledAppsAreRankedWithoutAnyManagementListAndTopFourAreDescending() {
        val data=data((1..6).flatMap { events(date,"app.$it",it*60) })
        val rows=rankedStatsApps(data,StatsPeriod.Today)
        assertEquals(listOf("app.6","app.5","app.4","app.3"),rows.take(4).map {it.key})
        assertEquals(6,rows.size)
        assertEquals(21*60,rows.sumOf {it.value})
    }
    @Test fun periodChangesRankingAndExcludesDaysOutsideTheSelectedRange() {
        val data=data(events(date,"app.1",60)+events(date.minusDays(2),"app.2",120)+
            events(date.minusDays(15),"app.3",180)+events(date.minusDays(30),"app.4",500))
        assertEquals(listOf("app.1"),rankedStatsApps(data,StatsPeriod.Today).map {it.key})
        assertEquals(listOf("app.2","app.1"),rankedStatsApps(data,StatsPeriod.Week).map {it.key})
        assertEquals(listOf("app.3","app.2","app.1"),rankedStatsApps(data,StatsPeriod.Month).map {it.key})
    }
    @Test fun duplicateLauncherAliasesAndActivityEventsDoNotDoubleCountAndMissingAppsAreExcluded() {
        val e=events(date,"app.1",120)
        val data=data(e+e+events(date,"removed.app",600),catalog+catalog.first().copy(id="other-alias"))
        val rows=rankedStatsApps(data,StatsPeriod.Today)
        assertEquals(1,rows.size);assertEquals(120,rows.single().value)
        assertEquals(1,data.catalog.count {it.packageName=="app.1"})
    }
    @Test fun noPermissionNoDataAndDemoNeverProduceABreakdown() {
        val ready=data(events(date,"app.1",60))
        for (access in listOf(UsageAccess.PermissionRequired,UsageAccess.Loading,UsageAccess.NoData,UsageAccess.Error,UsageAccess.Demo)) {
            val unavailable=ready.copy(usage=ready.usage.copy(today=ready.usage.today.copy(access=access)))
            assertTrue(rankedStatsApps(unavailable,StatsPeriod.Month).isEmpty())
        }
        assertTrue(rankedStatsApps(data(emptyList()),StatsPeriod.Today).isEmpty())
        assertEquals(1,rankedStatsApps(ready,StatsPeriod.Today).size)
    }
    @Test fun blankNameFallsBackToPackageAndUnusedInstalledAppsAreNotShown() {
        val result=data(events(date,"app.1",60),catalog.map {it.copy(name="")})
        assertEquals("app.1",result.catalog.first().name)
        assertEquals(listOf("app.1"),rankedStatsApps(result,StatsPeriod.Today).map {it.key})
    }
}
