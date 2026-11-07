package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.intentgate.app.MainActivity
import java.util.Base64

class PreferencesDailyLimitStorage(context:Context):DailyLimitStorage {
    private val prefs=context.applicationContext.getSharedPreferences("daily_limits",Context.MODE_PRIVATE)
    override fun read():DailyLimitLedger=try {
        prefs.getString("ledger",null)?.let { migrateLimitAppIds(DailyLimitCodec.decode(Base64.getDecoder().decode(it))) } ?: DailyLimitLedger()
    } catch(_:RuntimeException) { DailyLimitLedger() } catch(_:java.io.IOException) { DailyLimitLedger() }
    override fun write(ledger:DailyLimitLedger) {
        check(prefs.edit().putString("ledger",Base64.getEncoder().encodeToString(DailyLimitCodec.encode(ledger))).commit()) { "Unable to save daily limits" }
    }
}
class AndroidLimitReminderSink(private val context:Context):LimitReminderSink {
    fun reconcile(snapshot:DailyLimitSnapshot) {
        try {
            val manager=context.getSystemService(NotificationManager::class.java)
            for(notification in manager.activeNotifications) {
                val tag=notification.tag ?: continue
                if(!tag.startsWith("daily-limit-")) continue
                val current=snapshot.apps.values.firstOrNull { tag=="daily-limit-${it.appId}-${snapshot.date}" }
                val isToday=tag.endsWith("-${snapshot.date}")
                if(!isToday || current==null || (current!=null && (!current.managed ||
                    (current.usedSeconds==null && current.availability !in setOf(LimitAvailability.Loading,LimitAvailability.Stale)) ||
                    (notification.id==100 && current.usedSeconds!=null && !current.reached) ||
                    (notification.id==80 && current.usedSeconds!=null && (current.percent!!<80 || current.reached)))))
                    manager.cancel(tag,notification.id)
            }
        } catch(_:RuntimeException) { /* Notification service unavailable: never crash the UI. */ }
    }
    override fun deliver(reminder:DailyLimitReminder):Boolean {
        if(!hasUsageAccess(context) || !IntentCheckNotifications(context).allowed() ||
            !context.getSharedPreferences("gate_preferences",Context.MODE_PRIVATE).getBoolean("notifications",true)) return false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily_limits",loc(R.string.ig_daily_limit_reminders),NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel("daily_limits")?.importance==NotificationManager.IMPORTANCE_NONE) return false
        val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("intentgate://limit/${Uri.encode(reminder.key.appId)}/${reminder.key.date}/${reminder.key.threshold}"))
            .putExtra("limit_app",reminder.key.appId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text=loc(R.string.ig_used_daily_limit_updated_while_intentgate_is_visible_reminders, limitDuration(reminder.usedSeconds), limitDuration(reminder.limitSeconds))
        val notification=NotificationCompat.Builder(context,"daily_limits").setSmallIcon(R.drawable.ic_intent_notification)
            .setContentTitle(loc(R.string.ig_notification_title,reminder.appName,if(reminder.key.threshold==100) loc(R.string.ig_limit_reached) else loc(R.string.ig_notice_80)))
            .addExtras(android.os.Bundle().apply { putString("ig_app_name",reminder.appName);putLong("ig_used_seconds",reminder.usedSeconds);putLong("ig_limit_seconds",reminder.limitSeconds);putInt("ig_threshold",reminder.key.threshold) })
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setTimeoutAfter((reminder.key.date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()-System.currentTimeMillis()).coerceAtLeast(1))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setOnlyAlertOnce(true).setAutoCancel(true).build()
        return try {
            NotificationManagerCompat.from(context).notify("daily-limit-${reminder.key.appId}-${reminder.key.date}",reminder.key.threshold,notification);true
        } catch(_:SecurityException) { false }
    }
}
