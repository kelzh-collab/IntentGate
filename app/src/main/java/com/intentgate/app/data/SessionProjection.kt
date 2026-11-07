package com.intentgate.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun realIntentionRecords(sessions: List<IntentSession>): List<IntentionRecord> = sessions
    .filter { it.origin != IntentOrigin.Simulated }
    .sortedByDescending { it.startedAt }
    .map { IntentionRecord(it.id, it.purpose, it.appName, (it.usedMillis / 1000).toInt(),
        it.iconRes, packageName = it.packageName, startedAt = it.startedAt) }

/** Local Intent outcomes and Android usage have separate denominators and availability. */
fun projectIntentSessions(usage:UsageSnapshot,book:IntentSessionBook,zone:ZoneId=ZoneId.systemDefault()):UsageSnapshot {
    val date=usage.today.date
    val real=book.sessions.filter { it.origin!=IntentOrigin.Simulated }
    fun onDay(day:LocalDate)=real.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()==day }
    fun merge(original:DailyUsage,day:LocalDate):DailyUsage {
        val sessions=onDay(day)
        return original.copy(sessions=sessions.size,intentOutcomes=sessions.map { SessionIntentOutcome(it.id,it.outcome) },
            intentStatsAvailable=true,intentionsAreDemo=false, intentions=realIntentionRecords(sessions))
    }
    val old=usage.history.associateBy { it.date }
    val history=(29 downTo 1).map { date.minusDays(it.toLong()) }.map { day ->
        val base=old[day]?.usage ?: DailyUsage(emptyMap(),0,emptyList(),false,access=UsageAccess.NoData,date=day)
        DatedUsage(day,merge(base,day))
    }
    return UsageSnapshot(merge(usage.today,date),history)
}
