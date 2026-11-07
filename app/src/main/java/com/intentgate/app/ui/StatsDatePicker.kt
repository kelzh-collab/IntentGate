package com.intentgate.app.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.intentgate.app.R
import com.intentgate.app.UiStrings
import com.intentgate.app.loc
import com.intentgate.app.data.validStatsDate
import com.intentgate.app.data.statsDateFromPicker
import java.time.LocalDate

private fun pickerActivity(context: Context): Activity? {
    var current=context
    val seen=mutableSetOf<Context>()
    while(seen.add(current)) {
        if(current is Activity) return current
        current=(current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

internal fun showStatsDatePicker(context: Context, selected: LocalDate?, onDate: (LocalDate) -> Unit) {
    val activity=pickerActivity(context)
    if(activity==null || activity.isFinishing || activity.isDestroyed || !activity.window.decorView.isAttachedToWindow) {
        Log.w("IntentGateStats","date_picker_skipped reason=activity_unavailable")
        return
    }
    val today=LocalDate.now()
    val initial=selected?.takeIf { validStatsDate(it,today) } ?: today
    try {
        // Override only resources; keep Activity's WindowManager and application-window token.
        val themed=ContextThemeWrapper(activity,R.style.IntentGateStatsDatePicker).apply {
            applyOverrideConfiguration(Configuration(activity.resources.configuration).apply {
                setLocale(UiStrings.locale())
            })
        }
        DatePickerDialog(themed,{ _,year,month,day ->
            val date=statsDateFromPicker(year,month,day)
            if(date!=null) onDate(date)
            else Log.w("IntentGateStats","date_picker_invalid_selection")
        },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
            setOwnerActivity(activity)
            datePicker.maxDate=System.currentTimeMillis()
        }.show()
    } catch(error:RuntimeException) {
        Log.e("IntentGateStats","date_picker_failed phase=create_or_show",error)
        Toast.makeText(activity,loc(R.string.ig_stats_date_picker_failed),Toast.LENGTH_LONG).show()
    }
}
