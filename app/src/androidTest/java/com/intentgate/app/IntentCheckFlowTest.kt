package com.intentgate.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.intentgate.app.data.*
import com.intentgate.app.ui.IntentCheckScreen
import com.intentgate.app.ui.theme.IntentGateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class IntentCheckFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun show(onStart:(String,Int)->Boolean,onCancel:()->Unit) {
        val request=IntentRequest("simulated-ui-event","instagram","com.instagram.android","Instagram",R.drawable.ic_app_icon_placeholder,
            0,0,Long.MAX_VALUE,IntentOrigin.Simulated)
        compose.activity.runOnUiThread { compose.activity.setContent {
            IntentGateTheme(dynamicColor=false,darkTheme=false) {
                IntentCheckScreen(request,AppDefinition("instagram","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder),onStart,onCancel)
            }
        } }
    }
    @Test fun formSelectionCreatesSessionAndCancelCreatesNone() {
        val request=IntentRequest("simulated-ui-event","instagram","com.instagram.android","Instagram",R.drawable.ic_app_icon_placeholder,
            0,0,Long.MAX_VALUE,IntentOrigin.Simulated)
        val repo=IntentSessionRepository(MemoryIntentSessionStorage(IntentSessionBook(requests=listOf(request)))){1000}
        var cancelled=false
        show({purpose,minutes -> repo.start(request.key,purpose,minutes){true}!=null},{cancelled=true})
        compose.onNodeWithTag("intent_check_title").assertIsDisplayed()
        compose.onNodeWithTag("start_intent_session").assertIsNotEnabled()
        compose.onNodeWithTag("purpose_Other").performScrollTo().performClick()
        compose.onNodeWithTag("intent_custom_purpose").performTextInput("Plan a drawing")
        compose.onNodeWithTag("duration_Custom").performScrollTo().performClick()
        compose.onNodeWithTag("intent_custom_minutes").performTextInput("17")
        compose.onNodeWithTag("start_intent_session").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1,repo.state.value.sessions.size)
            assertEquals("Plan a drawing",repo.state.value.sessions.single().purpose)
            assertEquals(17*60_000L,repo.state.value.sessions.single().durationMillis)
        }
        compose.onNodeWithTag("start_intent_session").performClick()
        compose.runOnIdle { assertEquals(1,repo.state.value.sessions.size) }
        val empty=IntentSessionRepository(MemoryIntentSessionStorage())
        show({_,_ -> false},{cancelled=true})
        compose.onNodeWithTag("cancel_intent_check").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled);assertTrue(empty.state.value.sessions.isEmpty()) }
    }
    @Test fun captureActualIntentCheckFormFromExplicitlySimulatedEvent() {
        show({_,_ -> true},{})
        compose.onNodeWithTag("purpose_Reply to messages").performClick()
        compose.onNodeWithTag("duration_10").performClick()
        compose.onNodeWithTag("intent_check_title").performScrollTo()
        compose.waitForIdle();Thread.sleep(350)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null),"Intent-Check.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun androidLocalStoreRestoresSavedSessionWithoutClaimingRealDetection() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val storage=PreferencesIntentSessionStorage(context)
        val original=storage.read()
        try {
            val request=IntentRequest("isolated-store-test","instagram","com.instagram.android","Instagram",R.drawable.ic_app_icon_placeholder,
                0,0,Long.MAX_VALUE,IntentOrigin.Simulated)
            storage.write(IntentSessionBook(requests=listOf(request)))
            val repo=IntentSessionRepository(storage){1000}
            val session=requireNotNull(repo.start(request.key,"Local persistence test",5){true})
            val restored=IntentSessionRepository(PreferencesIntentSessionStorage(context)){2000}
            assertEquals(session.id,restored.state.value.active?.id)
            assertEquals(com.intentgate.app.session.SessionStatus.Paused,restored.state.value.active?.status)
            assertEquals(IntentOrigin.Simulated,restored.state.value.active?.origin)
            assertEquals(300_000L,restored.state.value.active?.remainingMillis)
        } finally { storage.write(original) }
    }

}
