package com.intentgate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class HomeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recentRecordsOpenCompleteHistoryAndDetail() {
        compose.onNodeWithText("IntentGate").assertIsDisplayed()
        compose.onNodeWithText("Learn a drawing technique").assertIsDisplayed()
        compose.onNodeWithText("View all").performClick()
        compose.onNodeWithText("Watch a drawing lesson").assertIsDisplayed().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Back to Home").performClick()
        compose.onNodeWithText("Latest 3 sessions").assertIsDisplayed()
    }

    @Test fun settingsNavigationReturnsHome() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings_title").assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Today’s intentions").assertIsDisplayed()
    }

    @Test fun timerCanPauseContinueAndFinish() {
        compose.onNode(hasContentDescription("remaining of 30 minutes", substring = true)).performClick()
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.onNodeWithText("End session").performClick()
        compose.onNodeWithText("Start a new 30-minute session").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("00:00").assertIsDisplayed()
    }
}
