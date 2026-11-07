package com.intentgate.app

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class IntentGateApplication:Application() {
    override fun onCreate() { super.onCreate();UiStrings.initialize(this) }
}
object AppLanguage {
    var currentTag by mutableStateOf("en")
        private set
    fun load(context:Context) {
        val saved=context.getSharedPreferences("app_language",Context.MODE_PRIVATE).getString("tag","en") ?: "en"
        val native=if(Build.VERSION.SDK_INT>=33) context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags() else ""
        currentTag=normalize(native.ifEmpty { saved })
        context.getSharedPreferences("app_language",Context.MODE_PRIVATE).edit().putString("tag",currentTag).commit()
        if(Build.VERSION.SDK_INT>=33 && native.isEmpty()) context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(currentTag)
    }
    private fun normalize(tag:String)=if(tag.startsWith("zh")) "zh-CN" else "en"
    fun set(context:Context,tag:String) {
        val selected=normalize(tag)
        check(context.getSharedPreferences("app_language",Context.MODE_PRIVATE).edit().putString("tag",selected).commit())
        currentTag=selected
        if(Build.VERSION.SDK_INT>=33) {
            val manager=context.getSystemService(LocaleManager::class.java)
            val locales=LocaleList.forLanguageTags(selected)
            if(manager.applicationLocales!=locales) manager.applicationLocales=locales
        }
        LocalizedNotifications.refresh(context)
    }
    fun syncSystem(context:Context) {
        if(Build.VERSION.SDK_INT>=33) {
            val tag=context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            if(tag.isNotEmpty() && normalize(tag)!=currentTag) set(context,tag)
        }
    }
    fun wrap(context:Context,tag:String=currentTag):Context {
        val configuration=Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration)
    }
}
object UiStrings {
    private var application:Context?=null
    private val contexts=mutableMapOf<String,Context>()
    private val ids by lazy { R.string::class.java.fields.filter { it.type==Int::class.javaPrimitiveType }.associate { it.name to it.getInt(null) } }
    private val fallback by lazy {
        val input=checkNotNull(UiStrings::class.java.classLoader?.getResourceAsStream("strings.xml")) { "Default string resources unavailable" }
        input.use {
            val nodes=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).getElementsByTagName("string")
            (0 until nodes.length).associate { index ->
                val node=nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\n","\n").replace("\\'","'").replace("\\\"","\"").replace("\\\\","\\")
            }
        }
    }
    private val legacyIds by lazy {
        buildMap<String,Int> { for((_,id) in ids) {
            put(forTag("en",id),id)
            if(application!=null) put(forTag("zh-CN",id),id)
        } }
    }
    fun initialize(context:Context) { application=context.applicationContext;AppLanguage.load(context) }
    fun locale():Locale=Locale.forLanguageTag(AppLanguage.currentTag)
    fun get(id:Int,vararg args:Any?):String=forTag(AppLanguage.currentTag,id,*args)
    fun forTag(tag:String,id:Int,vararg args:Any?):String {
        val context=application
        if(context!=null) {
            val localized=synchronized(contexts) { contexts.getOrPut(tag) { AppLanguage.wrap(context,tag) } }
            return if(args.isEmpty()) localized.getString(id) else localized.getString(id,*args)
        }
        val key=ids.entries.first { it.value==id }.key
        val text=fallback.getValue(key)
        return if(args.isEmpty()) text else String.format(Locale.ENGLISH,text,*args)
    }
    fun message(value:String):String {
        val id=if(value.startsWith("@string/")) ids[value.removePrefix("@string/")] else legacyIds[value]
        return id?.let { get(it) } ?: value
    }
    fun englishLabel(value:String):String=legacyIds[value]?.let { forTag("en",it) } ?: value
    fun purpose(value:String):String {
        val options=listOf(R.string.ig_reply_to_messages,R.string.ig_browse_for_inspiration,R.string.ig_learn_something,R.string.ig_learn_or_research,R.string.ig_browse_to_get_inspiration)
        val id=options.firstOrNull { value==forTag("en",it) || value==forTag("zh-CN",it) }
        return id?.let { get(when(it) {R.string.ig_learn_something -> R.string.ig_learn_or_research;R.string.ig_browse_for_inspiration -> R.string.ig_browse_to_get_inspiration;else -> it}) } ?: value
    }
    fun purposeOption(key:String):String=when(key) {
        "Reply to messages" -> get(R.string.ig_reply_to_messages)
        "Browse for inspiration" -> get(R.string.ig_browse_to_get_inspiration)
        "Learn something" -> get(R.string.ig_learn_or_research)
        "Other" -> get(R.string.ig_other)
        else -> key
    }
}
fun loc(id:Int,vararg args:Any?):String=UiStrings.get(id,*args)
