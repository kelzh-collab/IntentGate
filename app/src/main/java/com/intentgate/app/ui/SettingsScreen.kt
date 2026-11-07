package com.intentgate.app.ui
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings

import com.intentgate.app.AppLanguage


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import com.intentgate.app.data.IntentGateWindowService
import com.intentgate.app.data.INTENT_CHECK_ENABLED_KEY
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Local preferences only: no system permissions, scheduling or interception is implied. */
@Composable
internal fun SettingsContent(s: Float, usage: com.intentgate.app.data.DailyUsage,onEnableNotifications:()->Unit={}) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("gate_preferences", Context.MODE_PRIVATE) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val overlaySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayAllowed = Settings.canDrawOverlays(context)
    }
    fun desktopEnabled() = context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.name == IntentGateWindowService::class.java.name }
    var desktopAllowed by remember { mutableStateOf(desktopEnabled()) }
    val desktopSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        desktopAllowed = desktopEnabled()
    }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    fun enabled(key: String, default: Boolean) = revision.let { prefs.getBoolean(key, default) }
    fun save(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); revision++ }
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
    Row(Modifier.fillMaxWidth().height((40*s).dp), verticalAlignment = Alignment.CenterVertically) {
        GateText(loc(R.string.ig_settings),32*s,Modifier.weight(1f).testTag("settings_title"),weight=FontWeight.SemiBold)
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(GateColors.Surface).padding(horizontal=12.dp,vertical=6.dp)) {
            GateText(usage.sourceLabel,10.5f*s,color=GateColors.Secondary)
        }
    }
    Spacer(Modifier.height((10*s).dp))
    GateText(loc(R.string.ig_your_preferences_on_this_device),13*s,color=GateColors.Secondary)
    Spacer(Modifier.height((20*s).dp))
    SettingsGroup(loc(R.string.ig_preferences),s) {
        SettingsToggle(loc(R.string.ig_notifications),enabled("notifications",true),s,"notifications") { save("notifications",it) }
        SettingsToggle(loc(R.string.ig_daily_reminders),enabled("reminders",false),s,"reminders") { save("reminders",it) }
        SettingsRow(loc(R.string.ig_dark_mode),loc(R.string.ig_coming_soon),s,disabled=true)
        SettingsRow(loc(R.string.ig_language),loc(if(AppLanguage.currentTag=="en") R.string.ig_english else R.string.ig_language_simplified),s) { dialog="Language" }
    }
    SettingsGroup(loc(R.string.ig_focus_settings),s) {
        SettingsToggle(loc(R.string.ig_intent_check_enabled),enabled(INTENT_CHECK_ENABLED_KEY,true),s,INTENT_CHECK_ENABLED_KEY) {
            save(INTENT_CHECK_ENABLED_KEY,it)
        }
        SettingsToggle(loc(R.string.ig_strict_mode),enabled("strict",false),s,"strict") { save("strict",it) }
    }
    SettingsGroup(loc(R.string.ig_permissions_data),s) {
        SettingsRow(loc(R.string.ig_usage_access),if(!usage.isMock && com.intentgate.app.data.hasUsageAccess(context)) loc(R.string.ig_connected) else usage.sourceLabel,s,"usage_access") {
            if(!com.intentgate.app.data.openUsageAccess(context)) dialog="Usage settings unavailable"
        }
        SettingsRow(loc(R.string.ig_notification_permission),if(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) loc(R.string.ig_allowed) else loc(R.string.ig_not_granted),s) { onEnableNotifications() }
        SettingsRow(loc(R.string.ig_expiry_overlay_permission),
            loc(if (overlayAllowed) R.string.ig_allowed else R.string.ig_not_granted), s, "expiry_overlay") {
            try {
                overlaySettings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
            } catch (_: RuntimeException) {
                dialog = "@string/ig_expiry_overlay_permission"
            }
        }
        SettingsRow(loc(R.string.ig_desktop_intent_check),
            loc(if (desktopAllowed) R.string.ig_allowed else R.string.ig_not_granted), s, "desktop_intent_check") {
            dialog = "Desktop window disclosure"
        }
        SettingsRow(loc(R.string.ig_data_source),if(usage.isMock) loc(R.string.ig_demo_data) else loc(R.string.ig_android_usagestats),s)
        SettingsRow(loc(R.string.ig_export_data),loc(R.string.ig_coming_soon),s) { dialog="Export data" }
    }
    SettingsGroup(loc(R.string.ig_about),s) {
        SettingsRow(loc(R.string.ig_app_version),version,s)
        SettingsRow(loc(R.string.ig_privacy_policy),"",s) { dialog="Privacy policy" }
        SettingsRow(loc(R.string.ig_help_feedback),"",s) { dialog="Help / Feedback" }
    }
    GateText(loc(R.string.ig_daily_limit_reminders_use_android_foreground_usage_and_may_be_),11.5f*s,color=GateColors.Secondary)
    if (dialog == "Desktop window disclosure") {
        AlertDialog(onDismissRequest = { dialog = null }, containerColor = GateColors.Background.last(),
            title = { GateText(loc(R.string.ig_desktop_intent_check), 22f, weight = FontWeight.SemiBold) },
            text = { GateText(loc(R.string.ig_desktop_window_disclosure), 15f,
                Modifier.verticalScroll(rememberScrollState()), color = GateColors.Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("desktop_window_consent", true).apply()
                    dialog = null
                    try { desktopSettings.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    catch (_: RuntimeException) { dialog = "Desktop settings unavailable" }
                }) { GateText(loc(R.string.ig_desktop_window_agree), 15f) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { GateText(loc(R.string.ig_cancel), 15f) } })
    }
    // Ignore retired dialog IDs restored from an older UI; stored preference values stay untouched.
    dialog?.takeUnless { it in setOf("Desktop window disclosure", "Default daily limit", "Break allowance") }?.let { title ->
        AlertDialog(onDismissRequest={dialog=null},containerColor=GateColors.Background.last(),
            title={GateText(if (title == "Desktop settings unavailable") loc(R.string.ig_desktop_intent_check)
                else UiStrings.message(title),22f,weight=FontWeight.SemiBold)},
            text={ Column(if (title == "Privacy policy") Modifier.verticalScroll(rememberScrollState()) else Modifier) {
                if(title=="Language") {
                    listOf("en" to R.string.ig_english,"zh-CN" to R.string.ig_language_simplified).forEach { (tag,name) ->
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("language_$tag")
                            .clickable { dialog=null;AppLanguage.set(context,tag) },verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(AppLanguage.currentTag==tag,null,colors=RadioButtonDefaults.colors(selectedColor=GateColors.Ring))
                            Spacer(Modifier.width(12.dp));GateText(loc(name),16f)
                        }
                    }
                } else GateText(when(title) {
                    "Language" -> loc(R.string.ig_english_is_currently_available_more_languages_are_coming_soon)
                    "Export data" -> loc(R.string.ig_export_is_coming_soon_system_usage_export_is_not_implemented_y)
                    "Privacy policy" -> loc(R.string.ig_the_full_privacy_policy_is_coming_soon_preferences_stay_local_)
                    "@string/ig_expiry_overlay_permission" -> loc(R.string.ig_overlay_settings_unavailable)
                    "Desktop settings unavailable" -> loc(R.string.ig_desktop_settings_unavailable)
                    "Usage settings unavailable" -> loc(R.string.ig_no_system_usage_access_settings_screen_is_available_on_this_de)
                    else -> loc(R.string.ig_help_and_feedback_are_coming_soon_no_message_will_be_sent_from)
                },15f,color=GateColors.Secondary)
            } },confirmButton={TextButton(onClick={dialog=null}){GateText(loc(R.string.ig_close),16f)}})
    }
}

@Composable
private fun SettingsGroup(title:String,s:Float,content:@Composable ColumnScope.()->Unit) {
    GateText(UiStrings.message(title),16*s,weight=FontWeight.SemiBold)
    Spacer(Modifier.height((10*s).dp))
    val shape=RoundedCornerShape((18*s).dp)
    Column(Modifier.fillMaxWidth().shadow(2.dp,shape,ambientColor=GateColors.Ink.copy(alpha=.025f),spotColor=GateColors.Ink.copy(alpha=.025f))
        .clip(shape).background(GateColors.Surface).padding(horizontal=(16*s).dp,vertical=(4*s).dp),content=content)
    Spacer(Modifier.height((18*s).dp))
}

@Composable
private fun SettingsRow(title:String,value:String,s:Float,tag:String=title,disabled:Boolean=false,onClick:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("settings_${UiStrings.englishLabel(tag)}")
        .then(if(onClick!=null) Modifier.clickable(role=Role.Button,onClick=onClick) else Modifier),verticalAlignment=Alignment.CenterVertically) {
        GateText(UiStrings.message(title),14*s,Modifier.weight(1f),if(disabled)GateColors.Muted else GateColors.Ink)
        if(value.isNotEmpty()) GateText(value,11.5f*s,color=GateColors.Secondary)
        if(onClick!=null) { Spacer(Modifier.width((7*s).dp)); GateGlyph(GateIcon.Chevron,Modifier.size((12*s).dp),GateColors.Secondary) }
    }
}

@Composable
private fun SettingsToggle(title:String,value:Boolean,s:Float,key:String,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        GateText(UiStrings.message(title),14*s,Modifier.weight(1f))
        Switch(value,onChange,Modifier.testTag("settings_$key"),colors=SwitchDefaults.colors(
            checkedThumbColor=GateColors.Ink,checkedTrackColor=GateColors.Mint,checkedBorderColor=GateColors.Mint,
            uncheckedThumbColor=GateColors.Secondary,uncheckedTrackColor=GateColors.Track,uncheckedBorderColor=GateColors.Track))
    }
}
