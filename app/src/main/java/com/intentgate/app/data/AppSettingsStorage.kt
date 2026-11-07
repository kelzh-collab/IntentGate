package com.intentgate.app.data

import android.content.Context

/** Same local preference file; old aliases are migrated without changing limits or switches. */
object AppSettingsCodec {
    fun decode(value:String):List<ManagedApp> = value.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val fields=line.split('\t')
        if(fields.size!=4 || fields[0] in setOf("demo_notes","demo_reader")) return@mapNotNull null
        val limit=fields[2].toIntOrNull()?.takeIf { it in 1..1440 } ?: return@mapNotNull null
        val enabled=fields[1].toBooleanStrictOrNull() ?: return@mapNotNull null
        val intent=fields[3].toBooleanStrictOrNull() ?: return@mapNotNull null
        ManagedApp(AppIdentity.packageId(fields[0]),enabled,limit,intent)
    }.distinctBy { it.appId }.toList()
    fun encode(apps:List<ManagedApp>)=apps.joinToString("\n") {
        "${AppIdentity.packageId(it.appId)}\t${it.isManaged}\t${it.dailyLimitMinutes}\t${it.intentCheckEnabled}"
    }
}
class PreferencesAppSettingsStorage(context:Context):AppSettingsStorage {
    private val preferences=context.applicationContext.getSharedPreferences("app_management",Context.MODE_PRIVATE)
    override fun read():List<ManagedApp>? = preferences.getString("apps",null)?.let(AppSettingsCodec::decode)
    override fun write(apps:List<ManagedApp>) {
        check(preferences.edit().putString("apps",AppSettingsCodec.encode(apps)).commit()) { "Unable to save app settings" }
    }
    override fun readNames():Map<String,String> = preferences.getStringSet("app_names",emptySet()).orEmpty().mapNotNull { entry ->
        val parts=entry.split('\t',limit=2);if(parts.size==2) parts[0] to parts[1] else null
    }.toMap()
    override fun writeNames(names:Map<String,String>) {
        check(preferences.edit().putStringSet("app_names",names.map { (pkg,name) -> "$pkg\t${name.replace('\n',' ')}" }.toSet()).commit()) { "Unable to save app names" }
    }
}
