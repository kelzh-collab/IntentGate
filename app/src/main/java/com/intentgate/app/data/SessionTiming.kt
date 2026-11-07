package com.intentgate.app.data

import java.time.ZoneId

fun sessionWindowCredits(session:IntentSession,events:List<ForegroundEvent>,until:Long,zone:ZoneId):List<Pair<Long,Long>> =
    session.windows.map { w ->
        val end=minOf(w.endAt ?: until,until)
        if(w.endAt!=null && w.verifiedThrough>=w.endAt) w.creditedMillis to w.verifiedThrough
        else {
            // A different app resuming is evidence that the target lost the foreground,
            // even if the target pause event arrives late (for example, in split screen).
            val targetEvents = events.map { event ->
                if (event.type == 1 && event.packageName != session.packageName) event.copy(type = 16)
                else event
            }
            foregroundUsageDays(targetEvents, mapOf(session.packageName to session.appId), w.startAt, end, zone)
                .values.sumOf { it[session.appId]?.toLong() ?: 0L } * 1000 to end
        }
    }
