package com.intentgate.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Millisecond timestamps from UsageEvents. Background services are deliberately ignored. */
data class ForegroundEvent(val time: Long, val packageName: String, val activity: String, val type: Int)

/** Union of resumed activities within each package; clips and splits intervals at local midnights. */
fun foregroundUsageDays(events: List<ForegroundEvent>, packages: Map<String,String>, start: Long,
                        end: Long, zone: ZoneId): Map<LocalDate, Map<String,Int>> {
    val active=mutableMapOf<String,MutableSet<String>>()
    val opened=mutableMapOf<String,Long>()
    val totals=mutableMapOf<LocalDate,MutableMap<String,Long>>()
    fun close(pkg:String,time:Long) {
        var from=maxOf(opened.remove(pkg) ?: return,start)
        val until=minOf(time,end)
        while(from<until) {
            val day=Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
            val next=minOf(until,day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val values=totals.getOrPut(day){mutableMapOf()}
            val id=packages.getValue(pkg)
            values[id]=(values[id] ?: 0)+(next-from)
            from=next
        }
    }
    for(event in events.sortedBy { it.time }) {
        if(event.time>end) break
        if(event.type in setOf(16,17,26,27)) {
            opened.keys.toList().forEach { close(it,event.time) };active.clear();continue
        }
        val pkg=event.packageName
        if(pkg !in packages) continue
        val activities=active.getOrPut(pkg){mutableSetOf()}
        when(event.type) {
            1 -> { if(activities.isEmpty()) opened[pkg]=event.time;activities.add(event.activity) }
            2,23 -> { activities.remove(event.activity);if(activities.isEmpty()) close(pkg,event.time) }
        }
    }
    opened.keys.toList().forEach { close(it,end) }
    return totals.mapValues { (_,apps)->apps.mapValues { (_,millis)->(millis/1000).toInt() } }
}
