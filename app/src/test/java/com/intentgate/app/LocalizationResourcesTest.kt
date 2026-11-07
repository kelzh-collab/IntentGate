package com.intentgate.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import com.intentgate.app.data.formatStatsDuration

class LocalizationResourcesTest {
    private fun strings(folder:String):Map<String,String> {
        val path=listOf(File("src/main/res/$folder/strings.xml"),File("app/src/main/res/$folder/strings.xml")).first { it.exists() }
        val nodes=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path).getElementsByTagName("string")
        return (0 until nodes.length).associate { index -> nodes.item(index).let { it.attributes.getNamedItem("name").nodeValue to it.textContent } }
    }
    @Test fun localesHaveMatchingKeysAndFormatArguments() {
        val english=strings("values");val chinese=strings("values-zh-rCN")
        assertEquals(english.keys,chinese.keys)
        val placeholders=Regex("%[0-9]+\\${'$'}s")
        english.forEach { (key,value) ->
            assertTrue(chinese.getValue(key).isNotBlank())
            assertEquals(key,placeholders.findAll(value).map { it.value }.toList(),placeholders.findAll(chinese.getValue(key)).map { it.value }.toList())
        }
    }
    @Test fun defaultEnglishUsesTheSameResourceCatalogForExistingDataFormatters() {
        assertEquals("en",AppLanguage.currentTag)
        assertEquals("Settings",loc(R.string.ig_settings))
        assertEquals("1h 4m",formatStatsDuration(3840))
        assertEquals("Paused",UiStrings.message("@string/ig_paused"))
    }
    @Test fun appBrandsStayUntranslatedAndChineseTitlesComeFromResources() {
        val en=strings("values");val zh=strings("values-zh-rCN")
        assertEquals("IntentGate",en.getValue("app_name"));assertEquals("IntentGate",zh.getValue("app_name"))
        assertEquals("English",zh.getValue("ig_english"))
        assertEquals("简体中文",zh.getValue("ig_language_simplified"))
        assertEquals("设置",zh.getValue("ig_settings"));assertEquals("首页",zh.getValue("ig_home"))
    }
}
