package com.intentgate.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Separate scope: all queryable installed apps, never published into Home or managed usage. */
internal data class StatsAppBreakdown(
    val usage: UsageSnapshot = UsageSnapshot(breakdownEmpty(UsageAccess.Loading)),
    val catalog: List<AppDefinition> = emptyList()
)

private fun breakdownEmpty(access: UsageAccess, date: LocalDate = LocalDate.now()) =
    DailyUsage(emptyMap(), 0, emptyList(), false, access = access,
        intentStatsAvailable = false, intentionsAreDemo = false, date = date)

internal fun statsBreakdownFromEvents(events: List<ForegroundEvent>, catalog: List<AppDefinition>,
    from: Long, now: Long, zone: ZoneId, referenceDate: LocalDate? = null): StatsAppBreakdown {
    val date = referenceDate ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val installed = catalog.filter { it.installed == true }.distinctBy { it.packageName }
        .map { it.copy(id = it.packageName, name = it.name.ifBlank { it.packageName }) }
    val packages = installed.associate { it.packageName to it.packageName }
    if (events.isEmpty()) return StatsAppBreakdown(UsageSnapshot(breakdownEmpty(UsageAccess.NoData,date)), installed)
    val days = foregroundUsageDays(events, packages, from, now, zone)
    val first = Instant.ofEpochMilli(events.minOf { it.time }).atZone(zone).toLocalDate()
    fun usage(day: LocalDate) = breakdownEmpty(UsageAccess.Ready,day).copy(
        secondsByApp = days[day].orEmpty().filterValues { it > 0 })
    return StatsAppBreakdown(UsageSnapshot(usage(date),
        (29 downTo 1).map { date.minusDays(it.toLong()) }.filter { it >= first }
            .map { DatedUsage(it,usage(it)) }), installed)
}

internal fun rankedStatsApps(data: StatsAppBreakdown, period: StatsPeriod, selectedDate: LocalDate? = null): List<Map.Entry<String,Int>> {
    val today = data.usage.today
    if (today.isMock || today.access != UsageAccess.Ready || (selectedDate != null && today.date != selectedDate)) return emptyList()
    val installed = data.catalog.filter { it.installed == true }.map { it.packageName }.toSet()
    val stats = buildUsageStats(if(selectedDate!=null) StatsPeriod.Today else period, today, data.usage.history.filterNot { it.usage.isMock }, today.date)
    return stats.secondsByApp.entries.filter { it.key in installed && it.value > 0 }
        .sortedWith(compareByDescending<Map.Entry<String,Int>> { it.value }.thenBy { it.key })
}

/** UI-only refresh. Application metadata is resolved per recorded package within Android visibility rules. */
internal suspend fun readStatsAppBreakdown(context: Context, requestedDate: LocalDate? = null): StatsAppBreakdown = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val date = requestedDate ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun empty(access: UsageAccess) = StatsAppBreakdown(UsageSnapshot(breakdownEmpty(access,date)))
    if (!validStatsDate(date)) return@withContext empty(UsageAccess.NoData)
    if (!hasUsageAccess(context)) return@withContext empty(UsageAccess.PermissionRequired)
    if (!context.getSystemService(UserManager::class.java).isUserUnlocked)
        return@withContext empty(UsageAccess.Locked)
    try {
        val from = date.minusDays(if(requestedDate!=null) 1 else 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = minOf(now,date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val stream = context.getSystemService(UsageStatsManager::class.java).queryEvents(from,until)
            ?: return@withContext empty(UsageAccess.NoData)
        val events = mutableListOf<ForegroundEvent>()
        val buffer = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(buffer)
            if (buffer.eventType in setOf(1,2,23,16,17,26,27))
                events.add(ForegroundEvent(buffer.timeStamp,buffer.packageName.orEmpty(),
                    buffer.className.orEmpty(),buffer.eventType))
        }
        val catalog = events.filter { it.type in setOf(1,2,23) }.map { it.packageName }
            .filter { it.isNotBlank() }.distinct().mapNotNull { pkg ->
                try {
                    val info = context.packageManager.getApplicationInfo(pkg,0)
                    val name = try { context.packageManager.getApplicationLabel(info).toString().ifBlank { pkg } }
                        catch (_:RuntimeException) { pkg }
                    AppDefinition(pkg,name,pkg,android.R.drawable.sym_def_app_icon,installed = true)
                } catch (_:PackageManager.NameNotFoundException) { null }
                  catch (_:RuntimeException) { null }
            }
        val result = statsBreakdownFromEvents(events,catalog,from,until,zone,referenceDate=date)
        if (hasUsageAccess(context)) result else empty(UsageAccess.PermissionRequired)
    } catch (cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
      catch (_:SecurityException) { empty(UsageAccess.PermissionRequired) }
      catch (_:RuntimeException) { empty(UsageAccess.Error) }
}
