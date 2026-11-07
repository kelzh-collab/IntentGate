package com.intentgate.app

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.DatePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import com.intentgate.app.ui.showStatsDatePicker
import java.time.LocalDate
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the actual native dialog and Activity window token, without replacing production data. */
class StatsDatePickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun wrappedActivityPickerSelectsDateAndWorksAfterRecreation() {
        repeat(2) { attempt ->
            val target = LocalDate.now().minusDays(1L + attempt)
            var selected: LocalDate? = null
            compose.runOnIdle {
                showStatsDatePicker(ContextWrapper(compose.activity), target) { selected = it }
            }
            selectDate(target)
            compose.runOnIdle { assertEquals(target, selected) }
            if (attempt == 0) compose.activityRule.scenario.recreate()
        }
    }

    @Test fun statsPageAcceptsDateAndSwitchesBackToWeek() {
        compose.onAllNodesWithText(loc(R.string.ig_stats)).onLast().performClick()
        compose.onNodeWithText(loc(R.string.ig_stats_select_date)).performClick()
        selectDate(LocalDate.now().minusDays(1))
        compose.onNodeWithTag("stats_title").assertIsDisplayed()
        compose.onNodeWithTag("stats_range_week").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithTag("stats_total").assertExists()
    }

    private fun selectDate(target: LocalDate) {
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check(matches(isDisplayed()))
            .perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
                override fun getDescription() = "Select a valid past date"
                override fun perform(controller: UiController, view: View) {
                    (view as DatePicker).updateDate(target.year, target.monthValue - 1, target.dayOfMonth)
                }
            })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    @Test fun applicationContextCannotOpenAnInvalidWindow() {
        var called = false
        compose.runOnIdle {
            showStatsDatePicker(ApplicationProvider.getApplicationContext<Context>(), null) { called = true }
        }
        compose.runOnIdle { assertFalse(called) }
    }
}
