package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Explicit purpose outcome. Timer expiry and generic session termination imply no outcome. */
enum class IntentOutcome { Completed, NotCompleted, InProgress, NotRequested, Unknown }
data class SessionIntentOutcome(val sessionId: String, val outcome: IntentOutcome)
data class DatedUsage(val date: LocalDate, val usage: DailyUsage)

internal val EmptyUsageHistory: StateFlow<List<DatedUsage>> =
    MutableStateFlow<List<DatedUsage>>(emptyList()).asStateFlow()

enum class StatsPeriod(val titleRes:Int,val days:Int) {
    Today(R.string.ig_today,1),Week(R.string.ig_week,7),Month(R.string.ig_month,30);
    val title:String get()=loc(titleRes)
}

data class IntentInsights(
    val completed: Int,
    val notCompleted: Int,
    val inProgress: Int,
    val notRequested: Int,
    val outcomesAvailable: Boolean,
) {
    val totalIntents: Int get() = completed + notCompleted + inProgress
    val completionRate: Float? get() = if (outcomesAvailable && totalIntents > 0)
        completed.toFloat() / totalIntents else null
    val percent: Int? get() = completionRate?.let { (it * 100).roundToInt() }
}

data class UsageTrendPoint(val key: String, val label: String, val description: String, val seconds: Int?)

data class UsageStatsSnapshot(
    val period: StatsPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val secondsByApp: Map<String, Int>,
    val sessions: Int,
    val intents: IntentInsights,
    val trends: List<UsageTrendPoint>,
    val trendLabel: String,
    val availableDays: Int,
    val isMock: Boolean,
) {
    val totalSeconds: Int get() = secondsByApp.values.sum()
    val durationLabel: String get() = formatStatsDuration(totalSeconds)
    val isPartial: Boolean get() = availableDays < period.days
    val dateLabel: String get() = formatStatsDateRange(start, end)
}

/** One aggregation path for every range. The canonical today snapshot wins over stale history. */
fun buildUsageStats(
    period: StatsPeriod,
    today: DailyUsage,
    history: List<DatedUsage>,
    referenceDate: LocalDate,
): UsageStatsSnapshot {
    val start = referenceDate.minusDays(period.days.toLong() - 1)
    val byDate = history.filter { it.date >= start && it.date < referenceDate }.associateBy { it.date }.toMutableMap()
    byDate[referenceDate] = DatedUsage(referenceDate, today)
    val dates = (0 until period.days).map { start.plusDays(it.toLong()) }
    val intentDays = dates.mapNotNull { byDate[it] }
    val present = intentDays.filter { it.usage.available }
    val appTotals = linkedMapOf<String, Int>()
    present.forEach { day -> day.usage.secondsByApp.forEach { (id, seconds) ->
        appTotals[id] = (appTotals[id] ?: 0) + seconds
    } }
    val outcomes = intentDays.flatMap { it.usage.intentOutcomes.orEmpty() }
    val outcomesAvailable = today.intentStatsAvailable && intentDays.isNotEmpty() && intentDays.all { day ->
        day.usage.intentStatsAvailable && (day.usage.sessions == 0 || day.usage.intentOutcomes?.let { states ->
            states.size == day.usage.sessions && states.map { it.sessionId }.distinct().size == states.size &&
                states.none { it.outcome == IntentOutcome.Unknown }
        } == true)
    }
    val insights = IntentInsights(
        completed = outcomes.count { it.outcome == IntentOutcome.Completed },
        notCompleted = outcomes.count { it.outcome == IntentOutcome.NotCompleted },
        inProgress = outcomes.count { it.outcome == IntentOutcome.InProgress },
        notRequested = outcomes.count { it.outcome == IntentOutcome.NotRequested },
        outcomesAvailable = outcomesAvailable,
    )
    val hourly = today.hourlySeconds?.takeIf { it.size == 24 && it.all { value -> value >= 0 } && it.sum() == today.totalSeconds }
    val trends = when {
        period == StatsPeriod.Today && hourly != null -> hourly.chunked(4).mapIndexed { i, hours ->
            val hour = i * 4
            UsageTrendPoint("hour_$hour", "%02d".format(Locale.ROOT, hour),
                "%02d:00–%02d:00".format(Locale.ROOT, hour, hour + 4), hours.sum())
        }
        period == StatsPeriod.Month -> dates.chunked(7).map { week ->
            val from = week.first(); val to = week.last()
            val value = if (week.all { byDate[it]?.usage?.available == true }) week.sumOf { byDate.getValue(it).usage.totalSeconds } else null
            UsageTrendPoint(from.toString(), "${from.dayOfMonth}–${to.dayOfMonth}", formatStatsDateRange(from, to), value)
        }
        else -> dates.map { date -> UsageTrendPoint(date.toString(),
            if (period == StatsPeriod.Today) loc(R.string.ig_today) else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,UiStrings.locale()),
            date.format(DateTimeFormatter.ofPattern(loc(R.string.ig_header_date_pattern),UiStrings.locale())), byDate[date]?.usage?.takeIf { it.available }?.totalSeconds) }
    }
    return UsageStatsSnapshot(period, start, referenceDate, appTotals, intentDays.sumOf { it.usage.sessions },
        insights, trends, when {
            period == StatsPeriod.Today && hourly != null -> loc(R.string.ig_4_hour_blocks)
            period == StatsPeriod.Today -> loc(R.string.ig_daily)
            period == StatsPeriod.Week -> loc(R.string.ig_weekly)
            else -> loc(R.string.ig_monthly)
        }, present.size, present.any { it.usage.isMock })
}

fun formatStatsDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes < 60) loc(R.string.ig_min, minutes) else loc(R.string.ig_h_m, minutes / 60, minutes % 60)
}

fun formatStatsDateRange(start: LocalDate, end: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern(loc(R.string.ig_date_pattern),UiStrings.locale())
    return if (start == end) end.format(formatter) else "${start.format(formatter)} – ${end.format(formatter)}"
}
