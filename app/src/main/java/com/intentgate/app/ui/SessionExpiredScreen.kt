package com.intentgate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.data.*

@Composable
fun SessionExpiredScreen(session:IntentSession,app:AppDefinition,onContinue:()->Boolean,onFinish:()->Unit,
                         notificationsAllowed:Boolean,onEnableNotifications:()->Unit,continueEnabled:Boolean=true) {
    var error by remember(session.id) {mutableStateOf(false)}
    BackHandler(onBack=onFinish)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(GateColors.Background)).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            GateGlyph(GateIcon.Logo,Modifier.size(24.dp));Spacer(Modifier.width(10.dp))
            GateText(loc(R.string.ig_intentgate),22f,weight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(64.dp))
        Image(appPainter(app),app.name,Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(16.dp));GateText(app.name,22f,weight=FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(GateColors.Surface).padding(24.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            GateText(loc(R.string.ig_time_s_up),17f,color=GateColors.Secondary)
            Spacer(Modifier.height(16.dp))
            GateText(loc(if(continueEnabled) R.string.ig_continue_question else R.string.ig_intent_check_disabled_expiry),
                28f,Modifier.testTag("session_expiry_title"),weight=FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp));GateText(loc(R.string.ig_expiry_no_automatic_session),14f,color=GateColors.Secondary)
            Spacer(Modifier.height(28.dp))
            Button(onClick={error=!onContinue()},enabled=continueEnabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("continue_expired_session"),
                shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=GateColors.Mint,contentColor=GateColors.Ink)) {
                GateText(loc(R.string.ig_continue),16f,weight=FontWeight.SemiBold)
            }
            TextButton(onClick=onFinish,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finish_expired_session")) {
                GateText(loc(R.string.ig_finish_use),16f)
            }
            if(error) GateText(loc(R.string.ig_continuation_unavailable),13f,color=GateColors.Secondary)
        }
        Spacer(Modifier.height(24.dp));GateText(loc(R.string.ig_expiry_background_notice),12f,color=GateColors.Secondary)
        if(!notificationsAllowed) TextButton(onClick=onEnableNotifications) {GateText(loc(R.string.ig_enable_check_notifications),13f)}
    }
}
