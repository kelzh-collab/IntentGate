package com.intentgate.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun validStatsDate(date: LocalDate, today: LocalDate = LocalDate.now()) = !date.isAfter(today)

internal fun sessionsInDates(sessions: List<IntentSession>, start: LocalDate, end: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()): List<IntentSession> =
    sessions.filter { it.origin != IntentOrigin.Simulated &&
        Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().let { date -> date >= start && date <= end } }
        .distinctBy { it.id }.sortedByDescending { it.startedAt }

internal fun buildStatsSelection(period: StatsPeriod, today: DailyUsage, history: List<DatedUsage>,
    referenceDate: LocalDate, selectedDate: LocalDate?, selectedUsage: DailyUsage?,
    sessions: List<IntentSession>): UsageStatsSnapshot {
    val date = selectedDate ?: referenceDate
    val range = if (selectedDate != null) StatsPeriod.Today else period
    val day = if (selectedDate == null) today else selectedUsage?.takeIf { it.date == date && !it.isMock }
        ?: DailyUsage(emptyMap(),0,emptyList(),false,access=UsageAccess.NoData,date=date)
    val snapshot = buildUsageStats(range,day,if(selectedDate == null) history else emptyList(),date)
    return snapshot.copy(sessions=sessionsInDates(sessions,snapshot.start,snapshot.end).size,
        trends=if(selectedDate!=null) snapshot.trends.map { it.copy(label=date.dayOfMonth.toString(),description=date.toString()) } else snapshot.trends)
}

/** OEM picker callbacks are untrusted; do not update saved filters for invalid/future dates. */
internal fun statsDateFromPicker(year: Int, zeroBasedMonth: Int, day: Int,
    today: LocalDate = LocalDate.now()): LocalDate? {
    if(year<1 || zeroBasedMonth !in 0..11) return null
    return try { LocalDate.of(year,zeroBasedMonth+1,day).takeIf { validStatsDate(it,today) } }
        catch (_:java.time.DateTimeException) { null }
}
