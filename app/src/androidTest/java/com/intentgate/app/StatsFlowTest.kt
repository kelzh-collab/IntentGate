package com.intentgate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class StatsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun defaultWeekMatchesApprovedDesignAndUsesExplicitIntentOutcomes() {
        openStats()
        compose.onNodeWithTag("stats_range_week").assertIsSelected()
        compose.onNodeWithTag("stats_total").assertTextEquals("9h 25m")
        compose.onNodeWithTag("stats_sessions").assertTextEquals("Sessions · 55")
        compose.onNodeWithTag("stats_intent_count").assertTextEquals("36 / 48")
        compose.onNodeWithTag("stats_intent_rate").assertTextEquals("75%")
        compose.onNodeWithTag("stats_app_instagram").assertTextEquals("3h 30m")
        compose.onNodeWithTag("stats_app_youtube").assertTextEquals("2h 40m")
        compose.onNodeWithTag("stats_app_chrome").assertTextEquals("2h 05m")
        compose.onNodeWithTag("stats_app_reddit").assertTextEquals("1h 10m")
        compose.onNodeWithText("Demo data").assertIsDisplayed()
        capture("Stats-Week")
    }

    @Test fun periodSwitchesUpdateEveryMetricAndRetainStateAcrossTabsAndRecreation() {
        openStats()
        compose.onNodeWithTag("stats_range_today").performClick()
        compose.onNodeWithTag("stats_total").assertTextEquals("1h 24m")
        compose.onNodeWithTag("stats_sessions").assertTextEquals("Sessions · 8")
        compose.onNodeWithTag("stats_intent_count").assertTextEquals("6 / 8")
        compose.onNodeWithTag("stats_app_instagram").assertTextEquals("32 min")
        compose.onNodeWithTag("stats_app_youtube").assertTextEquals("24 min")
        compose.onNodeWithTag("stats_app_chrome").assertTextEquals("18 min")
        compose.onNodeWithTag("stats_app_reddit").assertTextEquals("10 min")
        capture("Stats-Today")
        compose.onNodeWithTag("stats_range_month").performClick()
        compose.onNodeWithTag("stats_total").assertTextEquals("40h 27m")
        compose.onNodeWithTag("stats_sessions").assertTextEquals("Sessions · 236")
        compose.onNodeWithTag("stats_intent_count").assertTextEquals("154 / 206")
        capture("Stats-Month")
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithTag("stats_range_month").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("stats_range_month").assertIsSelected()
        compose.onNodeWithTag("stats_total").assertTextEquals("40h 27m")
    }

    @Test fun selectingTrendBarChangesHighlightWithoutChangingRangeTotals() {
        openStats()
        compose.onNodeWithTag("stats_bar_0").performClick().assertIsSelected()
        compose.onNodeWithTag("stats_selected_trend", useUnmergedTree = true).assertTextEquals("68m")
        compose.onNodeWithTag("stats_total").assertTextEquals("9h 25m")
        compose.onNodeWithTag("stats_app_instagram").assertTextEquals("3h 30m")
        compose.onNodeWithTag("stats_bar_6").performClick().assertIsSelected()
        compose.onNodeWithTag("stats_selected_trend", useUnmergedTree = true).assertTextEquals("84m")
    }

    private fun openStats() {
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithTag("stats_title").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
