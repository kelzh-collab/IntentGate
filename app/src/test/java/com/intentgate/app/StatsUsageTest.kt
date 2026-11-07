package com.intentgate.app

import com.intentgate.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatsUsageTest {
    private val date = LocalDate.of(2026,11,2)
    private val source = MockUsageDataSource(date)
    private fun stats(period: StatsPeriod) = buildUsageStats(period,source.today.value,source.history.value,date)

    @Test fun confirmedWeekMatchesAllSharedTotals() {
        val week = stats(StatsPeriod.Week)
        assertEquals(565*60,week.totalSeconds)
        assertEquals(55,week.sessions)
        assertEquals(mapOf("instagram" to 210*60,"youtube" to 160*60,"chrome" to 125*60,"reddit" to 70*60),week.secondsByApp)
        assertEquals(week.totalSeconds,week.trends.sumOf { it.seconds ?: 0 })
        assertEquals(84*60,week.trends.last().seconds)
        assertEquals(7,week.availableDays)
        assertFalse(week.isPartial)
    }

    @Test fun rangesUseTheSameCanonicalTodayAndCoverTheirWholeWindow() {
        val today = stats(StatsPeriod.Today)
        assertEquals(source.today.value.secondsByApp,today.secondsByApp)
        assertEquals(8,today.sessions)
        assertEquals(84*60,today.trends.sumOf { it.seconds ?: 0 })
        assertEquals(6,today.trends.size)
        val month = stats(StatsPeriod.Month)
        assertEquals(30,month.availableDays)
        assertEquals(date.minusDays(29),month.start)
        assertEquals(2427*60,month.totalSeconds)
        assertEquals(236,month.sessions)
        assertEquals(month.totalSeconds,month.trends.sumOf { it.seconds ?: 0 })
        assertEquals(month.totalSeconds,month.secondsByApp.values.sum())
    }

    @Test fun intentCompletionUsesExplicitOutcomesAndExcludesSessionsWithoutIntent() {
        val week = stats(StatsPeriod.Week)
        assertEquals(36,week.intents.completed)
        assertEquals(48,week.intents.totalIntents)
        assertEquals(11,week.intents.notCompleted)
        assertEquals(1,week.intents.inProgress)
        assertEquals(7,week.intents.notRequested)
        assertEquals(.75f,week.intents.completionRate!!,.0001f)
        assertEquals(75,week.intents.percent)
        assertNotEquals(week.sessions,week.intents.totalIntents)
    }

    @Test fun missingOutcomesAndZeroIntentDenominatorNeverInventACompletionRate() {
        val missing = source.today.value.copy(intentOutcomes = null)
        val unknown = buildUsageStats(StatsPeriod.Today,missing,emptyList(),date)
        assertNull(unknown.intents.completionRate)
        assertFalse(unknown.intents.outcomesAvailable)
        val noIntent = source.today.value.copy(intentOutcomes = List(8) { SessionIntentOutcome("none-$it",IntentOutcome.NotRequested) })
        val empty = buildUsageStats(StatsPeriod.Today,noIntent,emptyList(),date)
        assertTrue(empty.intents.outcomesAvailable)
        assertEquals(0,empty.intents.totalIntents)
        assertNull(empty.intents.completionRate)
    }

    @Test fun staleHistoryDoesNotDoubleCountTodayAndMissingDaysStayUnavailable() {
        val canonical = source.today.value.copy(secondsByApp = mapOf("instagram" to 42*60))
        val stale = listOf(DatedUsage(date,source.today.value),DatedUsage(date.plusDays(1),source.today.value))
        val view = buildUsageStats(StatsPeriod.Week,canonical,stale,date)
        assertEquals(42*60,view.totalSeconds)
        assertEquals(1,view.availableDays)
        assertTrue(view.isPartial)
        assertEquals(6,view.trends.count { it.seconds == null })
        val today = buildUsageStats(StatsPeriod.Today,canonical,stale,date)
        assertEquals(42*60,today.trends.sumOf { it.seconds ?: 0 })
        assertEquals("Daily",today.trendLabel)
    }
}
