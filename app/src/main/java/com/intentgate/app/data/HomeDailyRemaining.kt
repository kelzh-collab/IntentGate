package com.intentgate.app.data

import com.intentgate.app.R
import com.intentgate.app.loc
import java.time.LocalDate

/** Sums each app's clamped daily remainder, never Intent-session time or a wall-clock countdown. */
data class HomeDailyRemaining(val appCount:Int,val availability:LimitAvailability,
                              val limitSeconds:Long?=null,val remainingSeconds:Long?=null) {
    val available:Boolean get()=availability==LimitAvailability.Ready
    val hasLimits:Boolean get()=appCount>0
    val progress:Float get()=if(available && (limitSeconds ?: 0)>0)
        (remainingSeconds!!.toDouble()/limitSeconds!!).toFloat().coerceIn(0f,1f) else 0f
    val statusLabel:String get()=loc(when {
        available && !hasLimits -> R.string.ig_no_active_daily_limits
        available -> R.string.ig_daily_remaining_title
        availability==LimitAvailability.PermissionRequired -> R.string.ig_usage_access_required
        availability==LimitAvailability.Loading || availability==LimitAvailability.Stale -> R.string.ig_refreshing_today_s_usage
        availability==LimitAvailability.Locked -> R.string.ig_unlock_device
        availability==LimitAvailability.Demo -> R.string.ig_demo_data_no_real_limit_status
        availability==LimitAvailability.NotInstalled -> R.string.ig_managed_app_not_installed
        else -> R.string.ig_usage_unavailable
    })
}

fun homeDailyRemaining(usage:DailyUsage,apps:List<ManagedApp>,today:LocalDate=LocalDate.now()):HomeDailyRemaining {
    val selected=apps.filter { it.isManaged && it.dailyLimitMinutes>0 }.distinctBy { it.appId }
    fun unavailable(status:LimitAvailability)=HomeDailyRemaining(selected.size,status)
    if(usage.isMock || usage.access==UsageAccess.Demo) return unavailable(LimitAvailability.Demo)
    if(usage.access!=UsageAccess.Ready) return unavailable(when(usage.access) {
        UsageAccess.PermissionRequired -> LimitAvailability.PermissionRequired
        UsageAccess.Locked -> LimitAvailability.Locked
        UsageAccess.Loading -> LimitAvailability.Loading
        UsageAccess.NoData -> LimitAvailability.NoData
        else -> LimitAvailability.Error
    })
    if(usage.date!=today) return unavailable(LimitAvailability.Stale)
    val limits=selected.map { app ->
        val state=usage.dailyLimits[app.appId] ?: return unavailable(LimitAvailability.Loading)
        if(state.date!=today) return unavailable(LimitAvailability.Stale)
        if(!state.managed || state.baseMinutes!=app.dailyLimitMinutes) return unavailable(LimitAvailability.Loading)
        if(state.availability!=LimitAvailability.Ready) return unavailable(state.availability)
        if(state.usedSeconds==null || state.usedSeconds<0) return unavailable(LimitAvailability.NoData)
        // Repository and usage flows can publish one frame apart. Never show an incomplete/stale sum.
        if(usage.secondsByApp[app.appId]?.toLong()!=state.usedSeconds) return unavailable(LimitAvailability.Loading)
        state
    }
    return HomeDailyRemaining(selected.size,LimitAvailability.Ready,
        limits.sumOf { it.limitSeconds },limits.sumOf { it.remainingSeconds!! })
}

/** Daily allowance uses labeled hours and minutes, rounded up so a positive sub-minute budget never displays zero. */
fun formatDailyRemaining(seconds:Long):String {
    val minutes=seconds.coerceAtLeast(0)/60+if(seconds>0 && seconds%60!=0L) 1 else 0
    return loc(R.string.ig_daily_time_units, minutes / 60, minutes % 60)
}
