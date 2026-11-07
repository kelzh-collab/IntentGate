package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StatsDateQueryTest {
    private val date=LocalDate.of(2026,11,6)
    private val zone=ZoneId.systemDefault()
    private fun session(id:String,day:LocalDate,origin:IntentOrigin=IntentOrigin.UserLaunch)=
        IntentSession(id,id,"pkg","pkg","App",0,"Read",day.atTime(10,0).atZone(zone).toInstant().toEpochMilli(),
            60_000,30_000,status=SessionStatus.Paused,origin=origin)
    private val all=listOf(session("today",date),session("week",date.minusDays(2)),
        session("month",date.minusDays(20)),session("old",date.minusDays(90)),
        session("demo",date,IntentOrigin.Simulated))
    private val today=DailyUsage(mapOf("pkg" to 60),0,emptyList(),false,date=date)

    @Test fun pickerCallbackUsesZeroBasedMonthsAndAcceptsValidLeapDays() {
        assertEquals(date,statsDateFromPicker(2026,10,6,date))
        assertEquals(LocalDate.of(2024,2,29),statsDateFromPicker(2024,1,29,date))
        assertEquals(LocalDate.of(2025,12,31),statsDateFromPicker(2025,11,31,date))
    }
    @Test fun malformedOrFuturePickerCallbacksCannotChangeTheSelectedDate() {
        for(parts in listOf(listOf(2026,1,29),listOf(2026,12,1),listOf(2026,-1,1),
            listOf(2026,10,0),listOf(2026,10,31),listOf(0,0,1),listOf(2026,10,7))) {
            assertNull(statsDateFromPicker(parts[0],parts[1],parts[2],date))
        }
    }
    @Test fun futureDatesAreRejectedAndTodayIsAllowed() {
        assertTrue(validStatsDate(date,date));assertTrue(validStatsDate(date.minusYears(10),date))
        assertFalse(validStatsDate(date.plusDays(1),date))
    }
    @Test fun singleDateUsesOnlyThatDaysUsageAndSavedSessionsEvenBeyondSystemRetention() {
        val target=date.minusDays(90)
        val day=today.copy(date=target,secondsByApp=mapOf("pkg" to 120))
        val stats=buildStatsSelection(StatsPeriod.Month,today,emptyList(),date,target,day,all)
        assertEquals(target,stats.start);assertEquals(target,stats.end)
        assertEquals(120,stats.totalSeconds);assertEquals(1,stats.sessions)
        val missing=buildStatsSelection(StatsPeriod.Today,today,emptyList(),date,target,null,all)
        assertEquals(0,missing.totalSeconds);assertEquals(0,missing.availableDays);assertEquals(1,missing.sessions)
    }
    @Test fun periodSessionCountsDoNotDependOnCompletionOrUsagePermission() {
        for((period,count) in listOf(StatsPeriod.Today to 1,StatsPeriod.Week to 2,StatsPeriod.Month to 3)) {
            val stats=buildStatsSelection(period,today.copy(access=UsageAccess.PermissionRequired),
                emptyList(),date,null,null,all)
            assertEquals(count,stats.sessions)
        }
    }
    @Test fun homeAndFullDailyHistoryExcludeOtherDatesAndDemoWithoutDeletingOriginals() {
        val records=realIntentionRecords(sessionsInDates(all,date,date))
        assertEquals(listOf("today"),records.map {it.id})
        assertEquals(30,records.single().usedSeconds);assertNotNull(records.single().startedAt)
        assertEquals(5,all.size)
        assertEquals(listOf("old"),sessionsInDates(all,date.minusDays(90),date.minusDays(90)).map {it.id})
    }
    @Test fun selectedDayBreakdownAndViewMoreShareOnlyThatDateAndRejectStaleData() {
        val target=date.minusDays(90)
        val start=target.atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val apps=listOf(AppDefinition("a","A","a",0,true),AppDefinition("b","B","b",0,true))
        val events=listOf(ForegroundEvent(start,"a","Main",1),ForegroundEvent(start+60_000,"a","Main",2),
            ForegroundEvent(start+60_000,"b","Main",1),ForegroundEvent(start+180_000,"b","Main",2))
        val data=statsBreakdownFromEvents(events,apps,start,start+200_000,ZoneOffset.UTC,referenceDate=target)
        assertEquals(target,data.usage.today.date)
        val rows=rankedStatsApps(data,StatsPeriod.Month,target)
        assertEquals(listOf("b","a"),rows.map {it.key});assertEquals(180,rows.sumOf {it.value})
        assertEquals(rows,rankedStatsApps(data,StatsPeriod.Today,target))
        assertTrue(rankedStatsApps(data,StatsPeriod.Today,date).isEmpty())
    }
    @Test fun localMidnightAndDuplicateIdsAreHandledConsistently() {
        val midnight=session("boundary",date).copy(startedAt=date.atStartOfDay(zone).toInstant().toEpochMilli())
        val before=midnight.copy(id="previous",startedAt=midnight.startedAt-1)
        assertEquals(listOf("boundary"),sessionsInDates(listOf(midnight,midnight,before),date,date).map {it.id})
    }
}
