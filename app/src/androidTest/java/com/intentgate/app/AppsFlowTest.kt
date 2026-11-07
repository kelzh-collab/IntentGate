package com.intentgate.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File

class AppsFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("app_management", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }).around(compose)

    @Test fun searchFiltersNamesAndShowsEmptyState() {
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps_search").performTextInput("cHrO")
        compose.onNodeWithText("Chrome").assertIsDisplayed()
        compose.onNodeWithText("Instagram").assertDoesNotExist()
        compose.onNodeWithTag("apps_search").performTextReplacement("unknown")
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Instagram").assertIsDisplayed()
    }

    @Test fun switchesCustomLimitAndIntentPersistAcrossNavigationAndRecreation() {
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("manage_instagram").performClick()
        compose.onNodeWithText("2 managed · 2 paused").assertIsDisplayed()
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithTag("detail_management").assertIsOff()
        compose.onNodeWithTag("intent_check").performScrollTo().performClick()
        compose.onNodeWithTag("custom_daily_limit").performScrollTo().performClick()
        compose.onNodeWithTag("daily_limit_input").performTextReplacement("0")
        compose.onNodeWithTag("confirm_daily_limit").assertIsNotEnabled()
        compose.onNodeWithTag("daily_limit_input").performTextReplacement("55")
        compose.onNodeWithTag("confirm_daily_limit").performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithTag("manage_instagram").assertIsOff()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Today’s intentions").assertIsDisplayed()
        compose.onNodeWithText("Apps").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithText("55 min").assertIsDisplayed()
        compose.onNodeWithTag("intent_check").performScrollTo().assertIsOff()
    }

    @Test fun addAppUsesSearchableCatalogWithoutDuplicates() {
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("Add app").performScrollTo().performClick()
        compose.onNodeWithTag("add_app_search").performTextInput("notes")
        compose.onNodeWithContentDescription("Add Notes (demo)").performClick()
        compose.onNodeWithText("4 managed · 1 paused").assertIsDisplayed()
        compose.onNodeWithText("Notes (demo)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add app").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add Notes (demo)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Reader (demo)").assertIsDisplayed()
    }

    @Test fun captureDefaultHomeAppsAndDetails() {
        capture("Home")
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("3 managed · 1 paused").assertIsDisplayed()
        capture("Apps")
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithText("App details").assertIsDisplayed()
        capture("App-Details")
        compose.onNodeWithContentDescription("Back to Apps").performClick()
        compose.onNodeWithText("3 managed · 1 paused").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
