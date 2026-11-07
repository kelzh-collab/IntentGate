package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.AudioAttributes
import android.provider.Settings
import android.util.Log
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.intentgate.app.MainActivity

internal const val LEGACY_EXPIRY_CHANNEL = "session_expiry"
internal const val EXPIRY_BANNER_CHANNEL = "session_expiry_banner"

/** Preserve existing channels whose defaults cannot be established. */
internal fun canMigrateExpiryChannel(
    sdk: Int, importance: Int, defaultSound: Boolean, vibrates: Boolean,
    userSetImportance: Boolean, userSetSound: Boolean,
): Boolean = sdk >= 29 && importance == NotificationManager.IMPORTANCE_DEFAULT &&
    defaultSound && !vibrates && !userSetImportance && !userSetSound

class IntentCheckNotifications(private val context:Context) {
    private val id=3001
    fun allowed():Boolean = (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun dismiss() { NotificationManagerCompat.from(context).cancel(id) }
    private fun expiryChannelId(manager: NotificationManager): String {
        val legacy = manager.getNotificationChannel(LEGACY_EXPIRY_CHANNEL) ?: return EXPIRY_BANNER_CHANNEL
        return if (canMigrateExpiryChannel(Build.VERSION.SDK_INT, legacy.importance,
                legacy.sound == Settings.System.DEFAULT_NOTIFICATION_URI, legacy.shouldVibrate(),
                Build.VERSION.SDK_INT >= 29 && legacy.hasUserSetImportance(),
                Build.VERSION.SDK_INT >= 30 && legacy.hasUserSetSound())) EXPIRY_BANNER_CHANNEL
        else LEGACY_EXPIRY_CHANNEL
    }

    fun canPostExpiry(): Boolean {
        if (!allowed() || !context.getSharedPreferences("gate_preferences", Context.MODE_PRIVATE)
                .getBoolean("notifications", true)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.getNotificationChannel(expiryChannelId(manager))?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun dismissExpiry(id:String) {NotificationManagerCompat.from(context).cancel("expiry:$id",3002)}
    fun postExpiry(session:IntentSession):Boolean {
        if(!allowed() || !context.getSharedPreferences("gate_preferences",Context.MODE_PRIVATE).getBoolean("notifications",true) ||
            session.expiryDecision!=ExpiryDecision.Pending || session.origin==IntentOrigin.Simulated) return false
        val manager=context.getSystemService(NotificationManager::class.java)
        val channelId = expiryChannelId(manager)
        if (manager.getNotificationChannel(channelId) == null && channelId == EXPIRY_BANNER_CHANNEL) {
            manager.createNotificationChannel(NotificationChannel(channelId,
                loc(R.string.ig_session_expiry_banner_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(true)
            })
        }
        val channel = manager.getNotificationChannel(channelId) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        Log.i(EXPIRY_LOG, "notification_channel id=" + channelId + " importance=" + channel.importance +
            " sound=" + (channel.sound != null) + " vibrates=" + channel.shouldVibrate())
        val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("intentgate://expired/${Uri.encode(session.id)}"))
            .putExtra("expired_session",session.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channelId).setSmallIcon(R.drawable.ic_intent_notification)
            .setPriority(if (channel.importance >= NotificationManager.IMPORTANCE_HIGH)
                NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(loc(R.string.ig_session_expired_title,session.appName))
            .setContentText(loc(R.string.ig_session_expired_notification)).setContentIntent(pending)
            .addExtras(android.os.Bundle().apply {putString("ig_app_name",session.appName)})
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        return try {NotificationManagerCompat.from(context).notify("expiry:${session.id}",3002,notification);true}
            catch(_:SecurityException) {false}
    }
    fun post(request:IntentRequest):Boolean {
        val prefs = context.getSharedPreferences("gate_preferences", Context.MODE_PRIVATE)
        if(!allowed() || !prefs.getBoolean("notifications",true) || !prefs.getBoolean(INTENT_CHECK_ENABLED_KEY,true) ||
            request.status!=IntentRequestStatus.Pending || System.currentTimeMillis()>request.expiresAt || request.origin!=IntentOrigin.AndroidEvent) return false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("intent_checks",loc(R.string.ig_intent_checks),NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel("intent_checks")?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("intentgate://check/${Uri.encode(request.key)}"))
            .putExtra("intent_request",request.key).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"intent_checks").setSmallIcon(R.drawable.ic_intent_notification)
            .setContentTitle(loc(R.string.ig_what_are_you_here_to_do_193)).setContentText(loc(R.string.ig_set_an_intention_for, request.appName))
            .addExtras(android.os.Bundle().apply { putString("ig_app_name",request.appName) })
            .setContentIntent(pending).setAutoCancel(true).setTimeoutAfter((request.expiresAt-System.currentTimeMillis()).coerceAtLeast(1))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        return try { NotificationManagerCompat.from(context).notify(id,notification);true } catch(_:SecurityException) { false }
    }
}
