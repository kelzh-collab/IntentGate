package com.intentgate.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.intentgate.app.data.*
import com.intentgate.app.ui.IntentGateApp
import com.intentgate.app.ui.theme.IntentGateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Isolated injected UsageStats fixture. No OS notification or real app usage is claimed. */
class DailyLimitFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun injectedLimitReachedCanBeViewedAndExtendedWithoutChangingBaseLimit() {
        val apps=LocalAppRepository()
        val fixture=DailyUsage(mapOf("instagram" to 42*60,"youtube" to 0,"chrome" to 0,"reddit" to 0),0,
            MockIntentions.records,false,date=LocalDate.now())
        val limits=DailyLimitRepository(MemoryDailyLimitStorage(),LimitReminderSink { true })
        limits.refresh(fixture,apps.apps.value,apps.catalog)
        // Visible source stays Demo data even though the reducer fixture exercises the Ready state.
        val source=object:UsageDataSource {
            override val today=MutableStateFlow(fixture.copy(isMock=true,access=UsageAccess.Demo))
            override suspend fun refresh()=Unit
        }
        compose.activity.runOnUiThread { compose.activity.setContent { IntentGateTheme(dynamicColor=false,darkTheme=false) {
            IntentGateApp(previewFrozen=true,appRepository=apps,usageDataSource=source,dailyLimits=limits)
        } } }
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithText("App details").assertIsDisplayed()
        compose.onNodeWithText("Limit reached").assertIsDisplayed()
        compose.onNodeWithText("Demo data").assertIsDisplayed()
        compose.waitForIdle();Thread.sleep(350)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null),"Daily-Limit-Reached.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        screenshot.recycle()
        compose.onNodeWithText("View usage").performScrollTo().performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("extend_daily_limit").performScrollTo().performClick()
        compose.onNodeWithTag("daily_limit_input").performTextClearance()
        compose.onNodeWithTag("daily_limit_input").performTextInput("15")
        compose.onNodeWithTag("confirm_daily_limit").performClick()
        compose.runOnIdle {
            val state=limits.state.value.apps.getValue("instagram")
            assertFalse(state.reached);assertEquals(13*60L,state.remainingSeconds)
            assertEquals(1,limits.extensions.size)
            assertEquals(40,apps.apps.value.first { it.appId=="instagram" }.dailyLimitMinutes)
        }
        compose.onAllNodesWithText("Limit reached").assertCountEquals(0)
    }
}
