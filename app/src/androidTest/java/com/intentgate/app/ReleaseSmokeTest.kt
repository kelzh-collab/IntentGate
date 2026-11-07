package com.intentgate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Real application screens with existing device data; no mock repository or usage fixtures. */
class ReleaseSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) {
        compose.onAllNodesWithText(label).onLast().performClick()
        compose.waitForIdle()
    }

    @Test fun homeAppsAndRealStatsRangesNavigateWithoutCrashing() {
        tab(loc(R.string.ig_home))
        tab(loc(R.string.ig_apps))
        tab(loc(R.string.ig_stats))
        compose.onNodeWithTag("stats_title").assertIsDisplayed()
        for (range in listOf("today", "week", "month")) {
            compose.onNodeWithTag("stats_range_$range").performClick().assertIsSelected()
            compose.onNodeWithTag("stats_total").assertExists()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("stats_range_month").assertIsSelected()
    }

    @Test fun languageSwitchesAndRetiredDefaultsRemainAbsent() {
        tab(loc(R.string.ig_settings))
        compose.onNodeWithTag("settings_title").assertIsDisplayed()
        compose.onAllNodesWithTag("settings_Default daily limit").assertCountEquals(0)
        compose.onAllNodesWithTag("settings_Break allowance").assertCountEquals(0)
        for (tag in listOf("zh-CN", "en")) {
            compose.onNodeWithTag("settings_Language").performScrollTo().performClick()
            compose.onNodeWithTag("language_$tag").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings_title").assertTextEquals(if (tag == "en") "Settings" else "设置")
        }
    }
}
