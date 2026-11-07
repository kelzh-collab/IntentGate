package com.intentgate.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.intentgate.app.data.limitDuration

/** Presentation-only updates. No new reminders, dedup changes, permission prompts or session actions. */
object LocalizedNotifications {
    fun refresh(context:Context) {
        try {
            val manager=context.getSystemService(NotificationManager::class.java)
            for((channelId,title) in listOf("intent_checks" to R.string.ig_intent_checks,"daily_limits" to R.string.ig_daily_limit_reminders,"session_expiry" to R.string.ig_session_expiry_channel, "session_expiry_banner" to R.string.ig_session_expiry_banner_channel)) {
                manager.getNotificationChannel(channelId)?.let { channel -> channel.name=loc(title);manager.createNotificationChannel(channel) }
            }
            for(active in manager.activeNotifications) {
                val notification=active.notification
                val extras=notification.extras
                val app=extras.getString("ig_app_name") ?: continue
                val builder=Notification.Builder.recoverBuilder(context,notification).setOnlyAlertOnce(true)
                when(notification.channelId) {
                    "intent_checks" -> builder.setContentTitle(loc(R.string.ig_what_are_you_here_to_do_193)).setContentText(loc(R.string.ig_set_an_intention_for,app))
                    "session_expiry", "session_expiry_banner" -> builder.setContentTitle(loc(R.string.ig_session_expired_title,app))
                        .setContentText(loc(R.string.ig_session_expired_notification))
                    "daily_limits" -> {
                        val label=if(extras.getInt("ig_threshold")==100) loc(R.string.ig_limit_reached) else loc(R.string.ig_notice_80)
                        val text=loc(R.string.ig_used_daily_limit_updated_while_intentgate_is_visible_reminders,
                            limitDuration(extras.getLong("ig_used_seconds")),limitDuration(extras.getLong("ig_limit_seconds")))
                        builder.setContentTitle(loc(R.string.ig_notification_title,app,label)).setContentText(text)
                            .setStyle(Notification.BigTextStyle().bigText(text))
                    }
                    else -> continue
                }
                manager.notify(active.tag,active.id,builder.build())
            }
        } catch(_:RuntimeException) { /* Permission or notification service changes cannot interrupt language selection. */ }
    }
}
