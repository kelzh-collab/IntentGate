package com.intentgate.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.intentgate.app.data.*
import com.intentgate.app.ui.IntentCheckScreen
import com.intentgate.app.ui.theme.IntentGateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only language/rendering is tested. No sessions are started and no settings other than language are changed. */
class LanguageFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tab(title:String)=compose.onNode(hasText(title) and hasClickAction()).performClick()
    private fun choose(tag:String) {
        compose.onNodeWithTag("settings_Language").performScrollTo().performClick()
        compose.onNodeWithTag("language_$tag").performClick()
        compose.waitUntil(10000) { AppLanguage.currentTag==tag }
    }
    private val layoutIssues=mutableListOf<String>()
    private fun checkVisibleText(page:String) {
        compose.waitForIdle()
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true).fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val layouts=mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.filter { result ->
                    // TextLayoutResult reports subpixel intrinsic-width rounding as overflow. Allow 1 physical pixel.
                    // Only localized glyphs are in scope: the unchanged Latin brand has pre-existing extra font leading.
                    result.layoutInput.text.any { it in '一'..'鿿' } && (result.didOverflowHeight || (0 until result.lineCount).any { line ->
                        result.getLineRight(line)-result.getLineLeft(line)>result.size.width+1f
                    })
                }.forEach { result ->
                    val message="$page: ${result.layoutInput.text}, size=${result.size}, lines=${result.lineCount}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, constraints=${result.layoutInput.constraints}, lineBottom=${result.getLineBottom(0)}"
                    println("LANGUAGE_LAYOUT: $message");layoutIssues+=message
                }
            }
        }
    }
    @Test fun pickerRoundTripAndChinesePagesKeepLocaleAcrossActivityRestart() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.activity.runOnUiThread { AppLanguage.set(context,"en") }
        compose.waitUntil(10000) { AppLanguage.currentTag=="en" }
        tab("Stats");compose.onNodeWithTag("stats_title").assertTextEquals("Stats")
        tab("Settings");choose("zh-CN")
        compose.onNodeWithTag("settings_title").assertTextEquals("设置")
        checkVisibleText("Settings")
        tab("首页");compose.onNodeWithText("今日使用意图").assertIsDisplayed();checkVisibleText("Home")
        tab("应用");compose.onNodeWithText("我的应用").assertIsDisplayed();checkVisibleText("Apps")
        compose.onNodeWithText("Instagram").performClick()
        compose.onNodeWithText("应用详情").assertIsDisplayed();checkVisibleText("App Details")
        compose.onNodeWithText("每日限额").performScrollTo().performClick()
        compose.onNodeWithTag("daily_limit_input").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        tab("统计");compose.onNodeWithTag("stats_title").assertTextEquals("统计")
        compose.onNodeWithText("使用概览").assertIsDisplayed();checkVisibleText("Stats")
        tab("设置");choose("en")
        compose.onNodeWithTag("settings_title").assertTextEquals("Settings")
        choose("zh-CN")
        val request=IntentRequest("language-test","instagram","com.instagram.android","Instagram",R.drawable.ic_app_icon_placeholder,0,0,Long.MAX_VALUE,IntentOrigin.Simulated)
        compose.activity.runOnUiThread { compose.activity.setContent { IntentGateTheme(dynamicColor=false,darkTheme=false) {
            IntentCheckScreen(request,AppDefinition("instagram","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder),{_,_ -> false},{})
        } } }
        compose.onNodeWithTag("intent_check_title").assertTextEquals("这次打开应用，\n你想做什么？")
        compose.onNodeWithText("自定义").assertIsDisplayed();compose.onNodeWithText("回复消息").assertIsDisplayed();compose.onNodeWithText("Instagram").assertIsDisplayed()
        checkVisibleText("Intent Check")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("首页").assertIsDisplayed()
        assertEquals("zh-CN",context.getSharedPreferences("app_language",0).getString("tag",null))
        assertEquals("zh-CN",AppLanguage.currentTag)
        assertTrue(layoutIssues.joinToString("\n"),layoutIssues.isEmpty())
    }
}
