package com.intentgate.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun localPreferencesChoicesPlaceholdersAndRestoration() {
        val prefs=compose.activity.getSharedPreferences("gate_preferences",Context.MODE_PRIVATE)
        val before=HashMap(prefs.all)
        try {
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("settings_title").assertIsDisplayed()
            for(key in listOf("notifications","reminders","intent","strict")) {
                val old=prefs.getBoolean(key,key=="notifications"||key=="intent")
                compose.onNodeWithTag("settings_$key").performScrollTo().performClick()
                compose.runOnIdle { check(prefs.getBoolean(key,old)!=old) }
            }
            compose.runOnIdle { check(prefs.edit().putInt("limit",120).putInt("break",15).commit()) }
            compose.onNodeWithTag("settings_default_limit").assertDoesNotExist()
            compose.onNodeWithTag("settings_break_allowance").assertDoesNotExist()
            compose.onNodeWithTag("settings_Export data").performScrollTo().performClick()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("settings_Privacy policy").performScrollTo().performClick()
            compose.onNodeWithText("Close").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings_default_limit").assertDoesNotExist()
            compose.onNodeWithTag("settings_break_allowance").assertDoesNotExist()
            compose.runOnIdle {
                check(prefs.getInt("limit",0)==120)
                check(prefs.getInt("break",0)==15)
            }
            compose.onNodeWithTag("settings_Language").performScrollTo().performClick()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("settings_title").performScrollTo()
            compose.waitForIdle()
            Thread.sleep(350)
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(instrumentation.targetContext.getExternalFilesDir(null),"Settings.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            screenshot.recycle()
        } finally {
            prefs.edit().clear().apply()
            val edit=prefs.edit()
            before.forEach { (key,value) -> when(value) { is Boolean -> edit.putBoolean(key,value); is Int -> edit.putInt(key,value) } }
            edit.apply()
        }
    }
}
