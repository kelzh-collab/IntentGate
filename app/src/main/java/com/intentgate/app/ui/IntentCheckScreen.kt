package com.intentgate.app.ui
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings
import kotlinx.coroutines.launch



import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.intentgate.app.data.*

@Composable
fun IntentCheckScreen(request:IntentRequest,app:AppDefinition,onStart:suspend (String,Int)->Boolean,onCancel:()->Unit,
                      notificationsAllowed:Boolean=true,onEnableNotifications:()->Unit={},dailyLimit:DailyLimitState?=null) {
    var purpose by rememberSaveable(request.key) { mutableStateOf("") }
    var customPurpose by rememberSaveable(request.key) { mutableStateOf("") }
    var duration by rememberSaveable(request.key) { mutableStateOf("") }
    var customMinutes by rememberSaveable(request.key) { mutableStateOf("") }
    var error by rememberSaveable(request.key) { mutableStateOf(false) }
    var submitting by remember(request.key) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val answer=if(purpose=="Other") customPurpose.trim() else UiStrings.purposeOption(purpose)
    val minutes=intentCheckMinutes(duration,customMinutes)
    val continuation=request.continuationOf!=null
    val withinLimit=!continuation || minutes?.let {canContinueWithinLimit(dailyLimit,it,java.time.LocalDate.now())}==true
    BackHandler {if(!submitting) onCancel()}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(GateColors.Background))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                GateGlyph(GateIcon.Logo,Modifier.size(24.dp));Spacer(Modifier.width(10.dp))
                GateText(loc(R.string.ig_intentgate),22f,weight=FontWeight.SemiBold)
            }
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Image(appPainter(app),app.name,Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    GateText(app.name,18f,weight=FontWeight.SemiBold)
                    GateText(if(request.origin==IntentOrigin.Simulated) loc(R.string.ig_simulated_event_ui_test) else loc(R.string.ig_a_moment_of_intention),12f,color=GateColors.Secondary)
                }
            }
            Spacer(Modifier.height(26.dp))
            GateText(loc(R.string.ig_what_are_you_here_to_do),30f,Modifier.testTag("intent_check_title"),weight=FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            GateText(loc(R.string.ig_choose_a_purpose_before_you_begin),14f,color=GateColors.Secondary)
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GateColors.Surface).padding(8.dp)) {
                listOf("Reply to messages","Learn something","Browse for inspiration","Other").forEach { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("purpose_$option")
                        .semantics { selected=purpose==option }.clip(RoundedCornerShape(14.dp))
                        .background(if(purpose==option) GateColors.Mint.copy(alpha=.35f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role=Role.RadioButton) { purpose=option;error=false }.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        GateText(UiStrings.purposeOption(option),15f,Modifier.weight(1f),weight=if(purpose==option) FontWeight.Medium else FontWeight.Normal)
                        RadioButton(purpose==option,null,colors=RadioButtonDefaults.colors(selectedColor=GateColors.Ring))
                    }
                }
                if(purpose=="Other") OutlinedTextField(customPurpose,{customPurpose=it.take(200)},
                    Modifier.fillMaxWidth().padding(8.dp).testTag("intent_custom_purpose"),label={GateText(loc(R.string.ig_your_purpose),14f)},maxLines=3)
            }
            Spacer(Modifier.height(26.dp))
            GateText(loc(R.string.ig_how_much_time_do_you_need),18f,weight=FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("5","10","15","30","Custom").forEach { option ->
                    Box(Modifier.weight(if(option=="Custom") 1.25f else 1f).heightIn(min=48.dp).testTag("duration_$option")
                        .semantics { selected=duration==option }.clip(RoundedCornerShape(14.dp))
                        .background(if(duration==option) GateColors.Mint else GateColors.Surface)
                        .clickable(role=Role.RadioButton) { duration=option;error=false },contentAlignment=Alignment.Center) {
                        GateText(if(option=="Custom") loc(R.string.ig_custom) else loc(R.string.ig_min, option),12f,weight=FontWeight.Medium)
                    }
                }
            }
            if(duration=="Custom") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(customMinutes,{customMinutes=it.filter { c -> c in '0'..'9' }.take(4)},
                    Modifier.fillMaxWidth().testTag("intent_custom_minutes"),label={GateText(loc(R.string.ig_minutes_1_1440),14f)},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=customMinutes.isNotEmpty() && minutes==null)
            }
            Spacer(Modifier.height(12.dp))
            GateText(dailyLimit?.let {state -> state.remainingSeconds?.let {
                loc(R.string.ig_today_remaining_of,limitDuration(it),limitDuration(state.limitSeconds))
            } ?: state.statusLabel} ?: loc(R.string.ig_usage_unavailable),12f,color=GateColors.Secondary)
            minutes?.let {selected -> sessionDailyLimitWarning(dailyLimit,selected)?.let {warning ->
                GateText(warning,12f,Modifier.testTag("session_limit_warning"),GateColors.Secondary)
            }}
            if(continuation && !withinLimit) GateText(loc(R.string.ig_continuation_limit_required),12f,color=GateColors.Secondary)
            Spacer(Modifier.height(28.dp))
            Button(onClick={
                if(!submitting) {submitting=true;scope.launch {
                    try {error=!onStart(answer,requireNotNull(minutes))} finally {submitting=false}
                }}
            },enabled=!submitting && answer.isNotBlank() && minutes!=null && withinLimit,
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("start_intent_session"),shape=RoundedCornerShape(18.dp),
                colors=ButtonDefaults.buttonColors(containerColor=GateColors.Mint,contentColor=GateColors.Ink)) { GateText(loc(R.string.ig_start_session),16f,weight=FontWeight.SemiBold) }
            TextButton(onClick=onCancel,enabled=!submitting,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("cancel_intent_check")) { GateText(loc(R.string.ig_cancel),15f,color=GateColors.Secondary) }
            if(error) GateText(loc(R.string.ig_unable_to_start_check_usage_access_and_app_management_then_try),12f,color=GateColors.Secondary)
            if(!notificationsAllowed) TextButton(onClick=onEnableNotifications,modifier=Modifier.fillMaxWidth()) { GateText(loc(R.string.ig_enable_check_notifications),12f,color=GateColors.Secondary) }
            Spacer(Modifier.height(8.dp))
            GateText(loc(if(continuation) R.string.ig_expiry_background_notice else R.string.ig_trigger_scope_notice),11.5f,color=GateColors.Secondary)
        }
    }
}
