package com.intentgate.app.ui
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.intentgate.app.data.addableInstalledApps
import com.intentgate.app.data.UsageAccess
import com.intentgate.app.data.PickerUsage
import com.intentgate.app.data.openUsageAccess
import com.intentgate.app.data.readPickerUsage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentgate.app.data.AppDefinition
import com.intentgate.app.data.DailyUsage
import com.intentgate.app.data.ManagedApp
import com.intentgate.app.data.DailyLimitState
import com.intentgate.app.data.limitDuration

@Composable
internal fun AppsContent(
    apps:List<ManagedApp>,catalog:List<AppDefinition>,usage:DailyUsage,s:Float,
    onManaged:(String,Boolean)->Unit,onApp:(String)->Unit,onAdd:()->Unit,
    onRemove:(Set<String>)->Boolean={false},onLaunch:(String)->Unit={},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(listOf<String>()) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    fun toggle(id:String) { selection=if(id in selection) selection-id else selection+id;saveFailed=false }
    val chosen=selection.filter { id -> apps.any { it.appId==id } }.toSet()
    val managedCount=apps.count { it.isManaged }
    val visible=apps.filter { settings ->
        val app=catalog.firstOrNull { it.id==settings.appId }
        (app?.name ?: settings.appId).contains(query.trim(),ignoreCase=true) ||
            (app?.packageName ?: settings.appId).contains(query.trim(),ignoreCase=true)
    }
    Row(Modifier.fillMaxWidth().height((40*s).dp),verticalAlignment=Alignment.CenterVertically) {
        GateText(loc(R.string.ig_apps),32*s,Modifier.weight(1f),weight=FontWeight.SemiBold)
        DemoBadge(s,usage.sourceLabel)
    }
    Space(6f,s)
    GateText(loc(R.string.ig_managed_paused,managedCount,apps.size-managedCount),14*s,color=GateColors.Secondary)
    Space(31f,s)
    AppSearch(query,{query=it},loc(R.string.ig_search_your_apps),s,"apps_search")
    Space(32f,s)
    Row(Modifier.fillMaxWidth().heightIn(min=(22*s).dp),verticalAlignment=Alignment.CenterVertically) {
        GateText(loc(R.string.ig_your_apps),18*s,Modifier.weight(1f),weight=FontWeight.SemiBold)
        if(apps.isNotEmpty()) TextButton(onClick={removing=!removing;selection=emptyList();saveFailed=false},modifier=Modifier.testTag("apps_remove_mode")) {
            GateText(loc(if(removing) R.string.ig_cancel else R.string.ig_remove_apps),12.5f*s,color=GateColors.Ring)
        }
        GateText(loc(R.string.ig_today),12.5f*s,color=GateColors.Secondary)
    }
    Space(16f,s)
    GateCard(s) {
        Column(Modifier.padding(horizontal=(16*s).dp,vertical=(12*s).dp)) {
            if(visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                GateText(loc(if(apps.isEmpty()) R.string.ig_no_apps_added_yet else R.string.ig_no_matching_apps),16*s,weight=FontWeight.Medium)
                Space(8f,s)
                GateText(loc(if(apps.isEmpty()) R.string.ig_choose_an_app_to_manage else R.string.ig_try_a_different_app_name),13*s,color=GateColors.Secondary)
            }
            visible.forEach { settings ->
                val app=catalog.firstOrNull { it.id==settings.appId } ?: return@forEach
                ManagedAppRow(app,settings,if(usage.available) usage.secondsByApp[app.id] else null,s,
                    onApp={if(removing) toggle(app.id) else onApp(app.id)},onManaged={onManaged(app.id,it)},dailyLimit=usage.dailyLimits[app.id],
                    selected=if(removing) app.id in chosen else null,onSelect={toggle(app.id)},onLaunch={onLaunch(app.id)})
            }
        }
    }
    Space(20f,s)
    if(removing) MintButton(loc(R.string.ig_remove_selected_apps,chosen.size),s,enabled=chosen.isNotEmpty(),onClick={confirmRemove=true})
    else MintButton(loc(R.string.ig_add_app),s,plus=true,onClick=onAdd)
    if(saveFailed) GateText(loc(R.string.ig_app_changes_failed),13*s,color=GateColors.Secondary)
    Space(24f,s)
    GateText(loc(R.string.ig_tap_an_app_to_edit_its_daily_limit_and_intent_checks),12.5f*s,
        Modifier.fillMaxWidth(),GateColors.Secondary,textAlign=TextAlign.Center)
    if(confirmRemove) AlertDialog(onDismissRequest={confirmRemove=false},containerColor=GateColors.Background.last(),
        title={GateText(loc(R.string.ig_remove_apps),22f,weight=FontWeight.SemiBold)},
        text={GateText(loc(R.string.ig_remove_apps_explanation),15f,color=GateColors.Secondary)},
        dismissButton={TextButton(onClick={confirmRemove=false}) {GateText(loc(R.string.ig_cancel),16f)}},
        confirmButton={TextButton(onClick={
            if(onRemove(chosen)) {confirmRemove=false;removing=false;selection=emptyList();saveFailed=false}
            else {confirmRemove=false;saveFailed=true}
        }) {GateText(loc(R.string.ig_remove_selected_apps,chosen.size),16f)}})
}

@Composable
private fun ManagedAppRow(
    app: AppDefinition, settings: ManagedApp, usedSeconds: Int?, s: Float,
    onApp: () -> Unit, onManaged: (Boolean) -> Unit,dailyLimit:DailyLimitState?=null,
    selected:Boolean?=null,onSelect:()->Unit={},onLaunch:(()->Unit)?=null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = (88*s).dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = maxOf(64f, 88*s).dp).clickable(
            onClickLabel = loc(R.string.ig_open_details, app.name), onClick = onApp
        ), verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(app, 44*s)
            Spacer(Modifier.width((14*s).dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GateText(app.name, 16*s, Modifier.weight(1f,fill=false), weight = FontWeight.SemiBold)
                    Spacer(Modifier.width((6*s).dp))
                    GateGlyph(GateIcon.Chevron, Modifier.size((11*s).dp), GateColors.Muted)
                }
                Space(4f, s)
                GateText(if(!app.isAvailable) appAvailabilityLabel(app) else dailyLimit?.summary ?: usedSeconds?.let { loc(R.string.ig_min_today, it/60) } ?: loc(R.string.ig_usage_unavailable), 14*s, color = GateColors.Secondary)
                Space(5f, s)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size((5*s).dp)) { drawCircle(if (settings.isManaged) GateColors.Ring else GateColors.Muted) }
                    Spacer(Modifier.width((5*s).dp))
                    GateText(if(!app.isAvailable) appAvailabilityLabel(app) else dailyLimit?.statusLabel ?: if (settings.isManaged) loc(R.string.ig_managed) else loc(R.string.ig_paused), 11.5f*s,
                        color = if (settings.isManaged) GateColors.Ring else GateColors.Secondary, weight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.width((8*s).dp))
        if(selected!=null) Checkbox(selected,{onSelect()},Modifier.testTag("remove_${app.id}"),
            colors=CheckboxDefaults.colors(checkedColor=GateColors.Ring,checkmarkColor=GateColors.Surface))
        else Column(horizontalAlignment=Alignment.CenterHorizontally) {
            ManagementSwitch(settings.isManaged,onManaged,s,loc(R.string.ig_manage,app.name),"manage_${app.id}")
            if(onLaunch!=null) TextButton(onClick=onLaunch,enabled=app.isAvailable,modifier=Modifier.testTag("open_${app.id}")) {
                GateText(loc(R.string.ig_open_app),12*s,color=GateColors.Ring)
            }
        }
    }
}

@Composable
internal fun AppDetailsContent(
    app: AppDefinition, settings: ManagedApp, usage: DailyUsage, s: Float,
    onBack: () -> Unit, onSave: (ManagedApp) -> Unit,onExtend:((Int,String)->Boolean)?=null,onRemove:(()->Boolean)?=null,onOpenApp:(()->Unit)?=null,onSessionControls:(()->Unit)?=null,
) {
    var showRemove by rememberSaveable(app.id) { mutableStateOf(false) }
    var removeFailed by rememberSaveable(app.id) { mutableStateOf(false) }
    var enabled by rememberSaveable(app.id) { mutableStateOf(settings.isManaged) }
    var limit by rememberSaveable(app.id) { mutableIntStateOf(settings.dailyLimitMinutes) }
    var intentCheck by rememberSaveable(app.id) { mutableStateOf(settings.intentCheckEnabled) }
    val hasUnsavedLaunchSettings = enabled != settings.isManaged || intentCheck != settings.intentCheckEnabled
    var showLimit by rememberSaveable(app.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val dailyLimit=usage.dailyLimits[app.id]
    var showUsage by rememberSaveable(app.id) { mutableStateOf(false) }
    var showExtend by rememberSaveable(app.id) { mutableStateOf(false) }
    var extensionAction by rememberSaveable(app.id) { mutableStateOf("") }
    var extensionError by rememberSaveable(app.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().heightIn(min = (40*s).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).testTag("apps_back").semantics { contentDescription = loc(R.string.ig_back_to_apps) }
            .clip(RoundedCornerShape((14*s).dp)).background(Color.White.copy(alpha = .55f))
            .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            GateGlyph(GateIcon.Back, Modifier.size((24*s).dp))
        }
        Spacer(Modifier.width((8*s).dp))
        GateText(loc(R.string.ig_app_details), 22*s, Modifier.weight(1f), weight = FontWeight.SemiBold)
        DemoBadge(s, usage.sourceLabel)
    }
    Space(24f, s)
    GateCard(s) {
        Row(Modifier.fillMaxWidth().heightIn(min = (112*s).dp).padding((20*s).dp),
            verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(app, 56*s)
            Spacer(Modifier.width((18*s).dp))
            Column(Modifier.weight(1f)) {
                GateText(app.name, 22*s, weight = FontWeight.SemiBold)
                Space(4f, s)
                GateText(if(!app.isAvailable) appAvailabilityLabel(app) else dailyLimit?.summary ?: if(!usage.available) loc(R.string.ig_usage_unavailable) else loc(R.string.ig_min_today, (usage.secondsByApp[app.id] ?: 0)/60), 14*s, color = GateColors.Secondary)
                Space(6f, s)
                GateText(if(!app.isAvailable) appAvailabilityLabel(app) else if(!enabled) loc(R.string.ig_paused) else dailyLimit?.statusLabel ?: loc(R.string.ig_managed), 11.5f*s,
                    color = if (enabled) GateColors.Ring else GateColors.Secondary, weight = FontWeight.Medium)
            }
        }
    }
    Space(28f, s)
    GateText(loc(R.string.ig_management), 18*s, weight = FontWeight.SemiBold)
    Space(14f, s)
    GateCard(s) {
        Column(Modifier.fillMaxWidth().padding(horizontal = (20*s).dp)
            .padding(top = (20*s).dp, bottom = (33*s).dp)) {
            SettingRow(loc(R.string.ig_app_management), loc(R.string.ig_pause_or_resume_this_app), enabled, { enabled = it }, s, "detail_management", 52f)
            Space(24f, s)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = loc(R.string.ig_edit_daily_limit)) { showLimit = true },
                verticalAlignment = Alignment.CenterVertically) {
                GateText(loc(R.string.ig_daily_limit), 16*s, Modifier.weight(1f), weight = FontWeight.SemiBold)
                GateText(loc(R.string.ig_min, limit), 14*s, color = GateColors.Ring)
                Spacer(Modifier.width((8*s).dp))
                GateGlyph(GateIcon.Chevron, Modifier.size((17*s).dp), GateColors.Ring)
            }
            // The 48dp hit area includes the visual header's surrounding space.
            GateText(dailyLimit?.let { loc(R.string.ig_today_total_m_extension, limitDuration(it.limitSeconds), it.extraMinutes) } ?: loc(R.string.ig_across_all_sessions_today), 12.5f*s, color = GateColors.Secondary)
            Space(19f, s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((8*s).dp)) {
                listOf(20, 40, 60).forEach { minutes ->
                    LimitChip(loc(R.string.ig_min, minutes), limit == minutes, s, Modifier.weight(1f)) { limit = minutes }
                }
                LimitChip(loc(R.string.ig_custom), limit !in listOf(20,40,60), s, Modifier.weight(1f).testTag("custom_daily_limit")) { showLimit = true }
            }
            Space(32f, s)
            SettingRow(loc(R.string.ig_intent_check), loc(R.string.ig_ask_for_a_purpose_before_each_session), intentCheck,
                { intentCheck = it }, s, "intent_check", 72f)
            Space(24f, s)
            GateText(loc(R.string.ig_changes_apply_to_future_sessions), 12*s, color = GateColors.Muted)
        }
    }
    Space(28f, s)
    MintButton(loc(R.string.ig_save_changes), s, onClick = {
        keyboard?.hide()
        onSave(settings.copy(isManaged = enabled, dailyLimitMinutes = limit, intentCheckEnabled = intentCheck))
    })
    Space(20f, s)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        TextButton(onClick={showUsage=true},modifier=Modifier.heightIn(min=48.dp)) { GateText(loc(R.string.ig_view_usage),14*s,color=GateColors.Ring) }
        TextButton(onClick={ extensionAction=java.util.UUID.randomUUID().toString();extensionError=false;showExtend=true },
            enabled=onExtend!=null && dailyLimit?.usedSeconds!=null && settings.isManaged && enabled && limit==settings.dailyLimitMinutes,
            modifier=Modifier.heightIn(min=48.dp).testTag("extend_daily_limit")) { GateText(loc(R.string.ig_extend_today),14*s,color=GateColors.Ring) }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        if(onOpenApp!=null) TextButton(onClick=onOpenApp,enabled=app.isAvailable && !hasUnsavedLaunchSettings,
            modifier=Modifier.testTag("open_app_details")) {
            GateText(loc(R.string.ig_open_app),14*s,color=GateColors.Ring)
        }
        if(onSessionControls!=null) TextButton(onClick=onSessionControls) {GateText(loc(R.string.ig_session_controls),14*s,color=GateColors.Ring)}
    }
    if (hasUnsavedLaunchSettings && onOpenApp != null) {
        GateText(loc(R.string.ig_save_app_check_before_opening), 12*s, color = GateColors.Secondary)
    }
    if(onRemove!=null) TextButton(onClick={showRemove=true},modifier=Modifier.fillMaxWidth().testTag("remove_app")) {
        GateText(loc(R.string.ig_remove_this_app),14*s,color=GateColors.Ring)
    }
    if(removeFailed) GateText(loc(R.string.ig_app_changes_failed),13*s,color=GateColors.Secondary)
    if(showRemove) AlertDialog(onDismissRequest={showRemove=false},containerColor=GateColors.Background.last(),
        title={GateText(loc(R.string.ig_remove_this_app),22f,weight=FontWeight.SemiBold)},
        text={GateText(loc(R.string.ig_remove_apps_explanation),15f,color=GateColors.Secondary)},
        dismissButton={TextButton(onClick={showRemove=false}) {GateText(loc(R.string.ig_cancel),16f)}},
        confirmButton={TextButton(onClick={if(onRemove?.invoke()!=true) {showRemove=false;removeFailed=true}}) {GateText(loc(R.string.ig_remove_this_app),16f)}})
    if(extensionError) GateText(loc(R.string.ig_unable_to_extend_refresh_usage_access_and_try_again),12*s,color=GateColors.Secondary)
    if(showUsage) AlertDialog(onDismissRequest={showUsage=false},containerColor=GateColors.Background.last(),
        title={GateText(loc(R.string.ig_today_33, app.name),22f,weight=FontWeight.SemiBold)},text={ Column {
            GateText(dailyLimit?.summary ?: loc(R.string.ig_usage_unavailable),15f,color=GateColors.Secondary)
            Spacer(Modifier.height(10.dp))
            GateText(dailyLimit?.let { loc(R.string.ig_daily_limit_extensions_apply_today_only, it.date, limitDuration(it.limitSeconds)) } ?: loc(R.string.ig_no_real_limit_estimate_is_available),13f,color=GateColors.Secondary)
            Spacer(Modifier.height(10.dp));GateText(loc(R.string.ig_android_foreground_usage_only_reminders_may_be_delayed_while_i),13f,color=GateColors.Secondary)
        } },confirmButton={TextButton(onClick={showUsage=false}) { GateText(loc(R.string.ig_close),16f) }})
    if(showExtend) DailyLimitDialog(15,onDismiss={showExtend=false},onConfirm={minutes ->
        val success=onExtend?.invoke(minutes,extensionAction)==true
        extensionError=!success;showExtend=false;keyboard?.hide()
    },title=loc(R.string.ig_extend_today),confirmLabel=loc(R.string.ig_extend))
    GateText(loc(R.string.ig_daily_limit_resets_at_midnight) +
        if (usage.isMock) loc(R.string.ig_sample_usage_shown_for_design_review) else loc(R.string.ig_only_foreground_usage_is_counted),
        12*s, Modifier.fillMaxWidth(), GateColors.Secondary, textAlign = TextAlign.Center)
    if (showLimit) {
        DailyLimitDialog(limit, onDismiss = { showLimit = false }, onConfirm = {
            limit = it; showLimit = false; keyboard?.hide()
        })
    }
}

@Composable
private fun SettingRow(
    title: String, subtitle: String, enabled: Boolean, onEnabled: (Boolean) -> Unit,
    s: Float, tag: String, height: Float,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = (height*s).dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GateText(UiStrings.message(title), 16*s, weight = FontWeight.SemiBold)
            Space(8f, s)
            GateText(subtitle, 12.5f*s, color = GateColors.Secondary)
        }
        ManagementSwitch(enabled, onEnabled, s, title, tag)
    }
}

@Composable
private fun ManagementSwitch(enabled: Boolean, onEnabled: (Boolean) -> Unit, s: Float, label: String, tag: String) {
    Box(Modifier.size(48.dp).testTag(tag).semantics {
        contentDescription = label
        stateDescription = if (enabled) loc(R.string.ig_managed) else loc(R.string.ig_paused)
    }.toggleable(enabled, role = Role.Switch, onValueChange = onEnabled), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width((44*s).dp).height((28*s).dp)) {
            val u = size.width/44f
            drawRoundRect(if (enabled) GateColors.Mint else Color(0xFFDDE7DE),
                size = size, cornerRadius = CornerRadius(14*u))
            val center = Offset((if (enabled) 30f else 14f)*u, 14*u)
            drawCircle(Color.White, 10*u, center)
            drawCircle((if (enabled) GateColors.Ring else GateColors.Muted).copy(alpha = .25f),
                10*u, center, style = Stroke(.7f*u))
        }
    }
}

@Composable
private fun AppSearch(value: String, onValue: (String) -> Unit, placeholder: String, s: Float, tag: String) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).height((52*s).dp)
        .clip(RoundedCornerShape((18*s).dp)).background(Color.White.copy(alpha = .8f))
        .padding(horizontal = (18*s).dp), verticalAlignment = Alignment.CenterVertically) {
        GateGlyph(GateIcon.Search, Modifier.size((20*s).dp), GateColors.Secondary)
        Spacer(Modifier.width((12*s).dp))
        BasicTextField(value, onValue, Modifier.weight(1f).testTag(tag).semantics { contentDescription = placeholder },
            singleLine = true, textStyle = TextStyle(fontFamily = GateFont, fontSize = (14.5f*s).sp, color = GateColors.Ink),
            cursorBrush = SolidColor(GateColors.Ring), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) GateText(placeholder, 14.5f*s, color = GateColors.Secondary); inner() }
            })
        if (value.isNotEmpty()) {
            Box(Modifier.size(40.dp).semantics { contentDescription = loc(R.string.ig_clear_search) }.clickable { onValue("") },
                contentAlignment = Alignment.Center) { GateText("×", 20*s, color = GateColors.Secondary) }
        }
    }
}

private fun appAvailabilityLabel(app:AppDefinition)=loc(if(app.installed==false) R.string.ig_not_installed else R.string.ig_app_unavailable)

@Composable
internal fun AddAppPage(
    catalog: List<AppDefinition>, apps: List<ManagedApp>, onBack: () -> Unit,
    onAdd: (Set<String>) -> Boolean, onRefresh: () -> Unit, refreshing: Boolean,
    refreshFailed: Boolean, resumed: Int, uiStarted: Boolean, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val available = remember(catalog, apps) { addableInstalledApps(catalog, apps) }
    val filtered = remember(catalog, apps, query) { addableInstalledApps(catalog, apps, query) }
    val packages = remember(available) { available.map { it.packageName }.toSet() }
    val chosen = selected.toSet().intersect(packages)
    var usage by remember { mutableStateOf(PickerUsage(UsageAccess.Loading)) }
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(packages, resumed, uiStarted) {
        if (uiStarted) usage = readPickerUsage(context, packages)
    }

    Column(modifier.imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(GateColors.Surface)
                .semantics { contentDescription = loc(R.string.ig_back_to_apps) }.clickable(onClick = onBack),
                contentAlignment = Alignment.Center) {
                GateGlyph(GateIcon.Back, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            GateText(loc(R.string.ig_choose_managed_apps_title), 24f, weight = FontWeight.SemiBold)
        }
        AppSearch(query, { query = it }, loc(R.string.ig_search_available_apps), 1f, "add_app_search")
        if (usage.access == UsageAccess.PermissionRequired) {
            GateCard(1f) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    GateText(loc(R.string.ig_picker_usage_hint), 13f, color = GateColors.Secondary)
                    TextButton(onClick = { settingsUnavailable = !openUsageAccess(context) }) {
                        GateText(loc(R.string.ig_open_usage_access), 14f, color = GateColors.Ring)
                    }
                    if (settingsUnavailable) GateText(loc(R.string.ig_usage_settings_unavailable), 12f)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(GateColors.Surface), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            when {
                refreshing -> item { GateText(loc(R.string.ig_loading_installed_apps), 14f) }
                refreshFailed -> item {
                    GateText(loc(R.string.ig_installed_apps_query_failed), 14f)
                    TextButton(onClick = onRefresh) { GateText(loc(R.string.ig_retry_app_query), 14f) }
                }
                filtered.isEmpty() -> item {
                    GateText(loc(if (available.isEmpty()) R.string.ig_no_addable_installed_apps else R.string.ig_no_matching_apps), 14f)
                }
                else -> items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in chosen
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("add_${app.packageName}")
                        .toggleable(checked, role = Role.Checkbox) {
                            selected = ArrayList(if (checked) selected - app.packageName else selected + app.packageName)
                            saveFailed = false
                        }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrandIcon(app, 36f)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            GateText(app.name, 15f, weight = FontWeight.Medium)
                            val seconds = usage.secondsByPackage[app.packageName]
                            if (seconds != null) GateText(loc(R.string.ig_picker_time_today, limitDuration(seconds.toLong())),
                                12f, color = GateColors.Secondary)
                            else if (usage.access != UsageAccess.PermissionRequired) {
                                GateText(loc(if (usage.access == UsageAccess.Loading) R.string.ig_loading_usage else R.string.ig_usage_unavailable),
                                    12f, color = GateColors.Secondary)
                            }
                        }
                        Checkbox(checked, null, colors = CheckboxDefaults.colors(
                            checkedColor = GateColors.Ring, checkmarkColor = GateColors.Surface))
                    }
                }
            }
        }
        if (saveFailed) GateText(loc(R.string.ig_app_changes_failed), 13f, color = GateColors.Secondary)
        Box(Modifier.testTag("confirm_add_apps")) {
            MintButton(loc(R.string.ig_add_selected_apps, chosen.size), 1f,
                enabled = chosen.isNotEmpty() && !refreshing && !refreshFailed,
                onClick = { if (!onAdd(chosen)) saveFailed = true })
        }
    }
}

@Composable
private fun DailyLimitDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit,title:String=loc(R.string.ig_daily_limit),confirmLabel:String=loc(R.string.ig_set_limit)) {
    var input by rememberSaveable { mutableStateOf(initial.toString()) }
    val minutes = input.toIntOrNull()?.takeIf { it in 1..1440 }
    AlertDialog(onDismissRequest = onDismiss, containerColor = GateColors.Background.last(),
        title = { GateText(UiStrings.message(title), 22f, weight = FontWeight.SemiBold) },
        text = { Column {
            OutlinedTextField(input, { input = it.filter { ch -> ch in '0'..'9' }.take(4) },
                Modifier.fillMaxWidth().testTag("daily_limit_input"), singleLine = true,
                label = { GateText(loc(R.string.ig_minutes), 14f) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                isError = minutes == null)
            Spacer(Modifier.height(12.dp))
            GateText(loc(R.string.ig_choose_1_1440_minutes), 13f, color = GateColors.Secondary)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { GateText(loc(R.string.ig_cancel), 16f) } },
        confirmButton = { TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null,
            modifier = Modifier.testTag("confirm_daily_limit")) { GateText(UiStrings.message(confirmLabel), 16f,
                color = if (minutes != null) GateColors.Ink else GateColors.Muted) } })
}

@Composable
private fun DemoBadge(s: Float, label: String = loc(R.string.ig_demo_data)) {
    Box(Modifier.width((84*s).dp).height((27*s).dp).clip(RoundedCornerShape((14*s).dp))
        .background(Color.White.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
        GateText(label, 10.5f*s, color = GateColors.Secondary, weight = FontWeight.Medium)
    }
}

@Composable
private fun BrandIcon(app: AppDefinition, size: Float) {
    Image(appPainter(app), app.name,
        Modifier.size(size.dp).clip(RoundedCornerShape((size*10/44).dp)), contentScale = ContentScale.Fit)
}

@Composable
private fun GateCard(s: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape((18*s).dp)
    Box(Modifier.fillMaxWidth().shadow(2.dp, shape, ambientColor = GateColors.Ink.copy(alpha = .025f),
        spotColor = GateColors.Ink.copy(alpha = .025f)).clip(shape).background(GateColors.Surface)) { content() }
}

@Composable
private fun MintButton(label: String, s: Float, plus: Boolean = false, enabled:Boolean=true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).height((52*s).dp)
        .clip(RoundedCornerShape((18*s).dp)).background(GateColors.Mint)
        .clickable(enabled=enabled,role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        if (plus) { GateGlyph(GateIcon.Plus, Modifier.size((20*s).dp)); Spacer(Modifier.width((10*s).dp)) }
        GateText(label, 15.5f*s,color=if(enabled) GateColors.Ink else GateColors.Muted, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun LimitChip(label: String, selected: Boolean, s: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).height((36*s).dp).semantics { this.selected = selected }
        .clip(RoundedCornerShape((12*s).dp))
        .background(if (selected) GateColors.Mint else Color(0xFFEDF4EB))
        .clickable(role = Role.RadioButton, onClick = onClick), contentAlignment = Alignment.Center) {
        GateText(label, 12*s, color = if (selected) GateColors.Ink else GateColors.Secondary,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Space(value: Float, s: Float) { Spacer(Modifier.height((value*s).dp)) }
