package com.intentgate.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** All demo totals, history, hourly buckets and explicit outcomes share this provider. */
class MockUsageDataSource(override val referenceDate: LocalDate = LocalDate.now()) : UsageDataSource {
    private val ids = listOf("instagram", "youtube", "chrome", "reddit")
    private val seeds = listOf(
        listOf(24,20,16,8,7,5,1,0,1), listOf(36,26,20,10,9,6,2,0,1),
        listOf(28,22,18,8,7,4,2,0,1), listOf(42,28,24,10,10,7,2,0,1),
        listOf(20,18,14,6,6,4,1,0,1), listOf(28,22,15,18,8,4,2,0,2),
        listOf(32,24,18,10,8,6,1,1,0),
    )
    private fun outcomes(seed: List<Int>, date: LocalDate, currentDay: Boolean): List<SessionIntentOutcome> {
        val states = listOf(IntentOutcome.Completed, IntentOutcome.NotCompleted, IntentOutcome.InProgress, IntentOutcome.NotRequested)
            .flatMapIndexed { index, state -> List(seed[index + 5]) {
                if (!currentDay && state == IntentOutcome.InProgress) IntentOutcome.NotCompleted else state
            } }
        return states.mapIndexed { index, state ->
            val id = if (currentDay && index < MockIntentions.records.size) MockIntentions.records[index].id else "$date-session-$index"
            SessionIntentOutcome(id, state)
        }
    }
    private val current = DailyUsage(
        secondsByApp = ids.zip(seeds.last().take(4).map { it * 60 }).toMap(),
        sessions = 8,
        intentions = MockIntentions.records,
        isMock = true,
        intentOutcomes = outcomes(seeds.last(), referenceDate, currentDay = true),
        hourlySeconds = listOf(0,0,0,0,0,0,0,0,5,8,12,7,10,16,8,6,5,7,0,0,0,0,0,0).map { it * 60 },
    )
    override val today: StateFlow<DailyUsage> = MutableStateFlow(current).asStateFlow()
    override val history: StateFlow<List<DatedUsage>> = MutableStateFlow((29 downTo 1).map { offset ->
        val date = referenceDate.minusDays(offset.toLong())
        val seed = seeds[6 - offset % 7]
        DatedUsage(date, DailyUsage(ids.zip(seed.take(4).map { it * 60 }).toMap(), seed[4],
            intentions = emptyList(), isMock = true, intentOutcomes = outcomes(seed, date, currentDay = false)))
    }).asStateFlow()
    override suspend fun refresh() = Unit
}
