package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InstalledAppManagementTest {
    private val mail=AppDefinition("old-mail-alias","Mail","org.example.mail",android.R.drawable.sym_def_app_icon,true)
    private val notes=AppDefinition("notes-alias","备忘录","org.example.notes",android.R.drawable.sym_def_app_icon,true)
    private class Memory:AppSettingsStorage {
        var values:List<ManagedApp>?=null;var labels=emptyMap<String,String>()
        override fun read()=values?.toList()
        override fun write(apps:List<ManagedApp>) {values=apps.toList()}
        override fun readNames()=labels.toMap()
        override fun writeNames(names:Map<String,String>) {labels=names.toMap()}
    }
    private class Discovery(var rows:List<AppDefinition>):InstalledAppDiscovery {
        var fail=false;var disabled=emptySet<String>()
        override fun installedApps():List<AppDefinition> {if(fail) throw SecurityException("query unavailable");return rows}
        override fun resolve(app:AppDefinition)=app.copy(installed=app.packageName in disabled,launchable=false,systemIcon=null)
    }
    private fun repository(storage:Memory=Memory(),discovery:Discovery=Discovery(listOf(mail,notes)))=
        LocalAppRepository(storage,installedCatalog=emptyList(),discovery=discovery)

    @Test fun discoveredAppsStayUnselectedAndEmptyManagementHasNoBudgetOrUsage() {
        val storage=Memory();val source=Discovery(listOf(mail,notes))
        repeat(2) {
            val repo=repository(storage,source)
            assertTrue(repo.refreshCatalog())
            assertEquals(2,repo.catalog.size)
            assertTrue(repo.apps.value.isEmpty())
            assertEquals(2,addableInstalledApps(repo.catalog,repo.apps.value).size)
            val date=LocalDate.of(2026,11,6)
            val usage=DailyUsage(emptyMap(),0,emptyList(),false,date=date)
            val remaining=homeDailyRemaining(usage,repo.apps.value,date)
            assertEquals(0,remaining.appCount);assertEquals(0L,remaining.limitSeconds)
            assertEquals(0L,remaining.remainingSeconds)
            val stats=buildUsageStats(StatsPeriod.Today,usage,emptyList(),date)
            assertTrue(stats.secondsByApp.isEmpty());assertEquals(0,stats.totalSeconds)
        }
        val repo=repository(storage,source);repo.refreshCatalog()
        assertTrue(repo.addApp(mail.packageName))
        assertEquals(listOf(ManagedApp(mail.packageName)),repository(storage,source).apps.value)
    }
    @Test fun actualCatalogUsesPackageIdentityDeduplicatesLauncherAliasesAndHasNoDemoSubstitute() {
        val source=Discovery(listOf(mail,mail.copy(id="second-activity"),notes))
        val repo=repository(discovery=source)
        assertFalse(repo.catalogReady);assertTrue(repo.catalog.isEmpty());assertTrue(repo.apps.value.isEmpty())
        assertTrue(repo.refreshCatalog());assertTrue(repo.catalogReady)
        assertEquals(setOf(mail.packageName,notes.packageName),repo.catalog.map {it.id}.toSet())
        assertEquals(2,repo.catalog.size);assertTrue(repo.catalog.all {it.id==it.packageName})
        assertFalse(repo.catalog.any {it.packageName.startsWith("demo.intentgate")})
    }
    @Test fun pickerSearchesRealNameAndPackageAndExcludesAddedUnavailableAndDuplicatePackages() {
        val unavailable=mail.copy(packageName="org.disabled",installed=true,launchable=false)
        val gone=mail.copy(packageName="org.gone",installed=false)
        val catalog=listOf(mail,mail.copy(id="another-launcher"),notes,unavailable,gone)
        assertEquals(listOf(mail),addableInstalledApps(catalog,emptyList(),"mAiL"))
        assertEquals(listOf(notes),addableInstalledApps(catalog,emptyList(),"备忘"))
        assertEquals(listOf(notes),addableInstalledApps(catalog,emptyList()," example.NOTES "))
        assertEquals(listOf(notes),addableInstalledApps(catalog,listOf(ManagedApp(mail.packageName))))
        assertTrue(addableInstalledApps(catalog,emptyList(),"missing").isEmpty())
    }
    @Test fun pickerListsUserAppsBeforePreinstalledAppsEvenWhenSystemNameSortsFirst() {
        val system = mail.copy(name = "AAA", isSystemApp = true)
        assertEquals(listOf(notes, system), addableInstalledApps(listOf(system, notes), emptyList()))
    }

    @Test fun failedMetadataForASavedAppDoesNotHideOtherLaunchableApps() {
        val storage = Memory().apply { values = listOf(ManagedApp(mail.packageName, dailyLimitMinutes = 73)) }
        val source = object : InstalledAppDiscovery {
            override fun installedApps() = listOf(notes)
            override fun resolve(app: AppDefinition): AppDefinition = throw IllegalStateException("Bad package metadata")
        }
        val repo = LocalAppRepository(storage, installedCatalog = emptyList(), discovery = source)
        assertTrue(repo.refreshCatalog())
        assertEquals(listOf(notes.packageName), addableInstalledApps(repo.catalog, repo.apps.value).map { it.packageName })
        assertEquals(73, repo.apps.value.single().dailyLimitMinutes)
        assertFalse(repo.catalog.first { it.packageName == mail.packageName }.isAvailable)
    }

    @Test fun bulkAddIsAtomicAndDoesNotDuplicateAlreadyAddedPackages() {
        val repo=repository();repo.refreshCatalog()
        assertTrue(repo.addApps(setOf(mail.packageName,notes.packageName)))
        assertEquals(2,repo.apps.value.size)
        assertFalse(repo.addApp(mail.packageName));assertFalse(repo.addApps(setOf(mail.packageName,notes.packageName)))
        assertFalse(repo.addApp("not.queryable"));assertEquals(2,repo.apps.value.size)
        repo.removeApps(setOf(notes.packageName))
        assertFalse(repo.addApps(setOf(notes.packageName,"not.queryable")))
        assertEquals(listOf(mail.packageName),repo.apps.value.map {it.appId})
    }
    @Test fun perAppSettingsAndBulkRemovalPersistIncludingAnEmptyList() {
        val storage=Memory();val source=Discovery(listOf(mail,notes));val repo=repository(storage,source);repo.refreshCatalog()
        repo.addApps(setOf(mail.packageName,notes.packageName))
        repo.saveSettings(ManagedApp(mail.packageName,false,187,false))
        val restored=repository(storage,source);restored.refreshCatalog()
        assertEquals(ManagedApp(mail.packageName,false,187,false),restored.apps.value.first {it.appId==mail.packageName})
        assertEquals(ManagedApp(notes.packageName),restored.apps.value.first {it.appId==notes.packageName})
        assertTrue(restored.removeApps(setOf(mail.packageName,notes.packageName)))
        assertTrue(repository(storage,source).apps.value.isEmpty())
        assertFalse(restored.removeApps(setOf("unknown")))
    }
    @Test fun uninstallDisableAndReinstallPreserveSettingsAndTheLastKnownLabel() {
        val storage=Memory();val source=Discovery(listOf(mail,notes));val repo=repository(storage,source);repo.refreshCatalog()
        repo.addApp(notes.packageName);repo.saveSettings(ManagedApp(notes.packageName,true,73,false))
        source.rows=listOf(mail);repo.refreshCatalog()
        val missing=repo.catalog.first {it.id==notes.packageName}
        assertEquals("备忘录",missing.name);assertEquals(false,missing.installed);assertFalse(missing.isAvailable)
        assertTrue(addableInstalledApps(repo.catalog,repo.apps.value).none {it.packageName==notes.packageName})
        source.disabled=setOf(notes.packageName);repo.refreshCatalog()
        val disabled=repo.catalog.first {it.id==notes.packageName}
        assertEquals(true,disabled.installed);assertFalse(disabled.launchable)
        assertTrue(enabledForegroundTargets(repo).isEmpty())
        val restarted=repository(storage,source);restarted.refreshCatalog()
        assertEquals("备忘录",restarted.catalog.first {it.id==notes.packageName}.name)
        source.rows=listOf(mail,notes);source.disabled=emptySet();restarted.refreshCatalog()
        assertTrue(restarted.catalog.first {it.id==notes.packageName}.isAvailable)
        assertEquals(ManagedApp(notes.packageName,true,73,false),restarted.apps.value.single())
        assertEquals(notes.packageName,enabledForegroundTargets(restarted).single().appId)
    }
    @Test fun queryFailureClosesAddAndMonitoringWithoutErasingSavedManagement() {
        val source=Discovery(listOf(mail));val repo=repository(discovery=source);repo.refreshCatalog();repo.addApp(mail.packageName)
        source.fail=true
        assertFalse(repo.refreshCatalog());assertEquals(1,repo.apps.value.size)
        assertTrue(addableInstalledApps(repo.catalog,emptyList()).isEmpty())
        assertTrue(enabledForegroundTargets(repo).isEmpty())
        assertFalse(repo.addApp(notes.packageName))
    }
    @Test fun legacyPreferencesMigrateWithoutChangingLimitsAndDoNotDuplicatePackageAliases() {
        val text="instagram\tfalse\t55\tfalse\ncom.instagram.android\ttrue\t40\ttrue\ndemo_notes\ttrue\t40\ttrue\norg.example.mail\ttrue\t187\ttrue\ninvalid\ttrue\t0\ttrue"
        val decoded=AppSettingsCodec.decode(text)
        assertEquals(listOf(ManagedApp("com.instagram.android",false,55,false),ManagedApp(mail.packageName,true,187,true)),decoded)
        assertEquals(decoded,AppSettingsCodec.decode(AppSettingsCodec.encode(decoded)))
        val store=Memory().apply {values=listOf(ManagedApp("instagram",false,55,false))}
        val repo=repository(store,Discovery(emptyList()));repo.refreshCatalog()
        assertEquals(ManagedApp("com.instagram.android",false,55,false),repo.apps.value.single())
        assertEquals("Instagram",repo.catalog.single().name);assertFalse(repo.catalog.single().isAvailable)
    }
    @Test fun storedSessionsExtensionsAndNotificationDedupKeepHistoryUnderTheSamePackageIdentity() {
        val pkg="com.instagram.android";val date=LocalDate.of(2026,11,3)
        val request=IntentRequest("existing-key","instagram",pkg,"Instagram",R.drawable.ic_app_icon_placeholder,1,1,1000,status=IntentRequestStatus.Started)
        val session=IntentSession("existing-session",request.key,"instagram",pkg,"Instagram",R.drawable.ic_app_icon_placeholder,"User purpose",1,600000,500000,SessionStatus.Paused)
        val book=IntentSessionBook(listOf(request),listOf(session))
        val migrated=migrateSessionAppIds(IntentSessionCodec.decode(IntentSessionCodec.encode(book)))
        assertEquals(pkg,migrated.requests.single().appId);assertEquals(pkg,migrated.sessions.single().appId)
        assertEquals(session.copy(appId=pkg),migrated.sessions.single());assertEquals(migrated,migrateSessionAppIds(migrated))
        val old=DailyLimitLedger(listOf(DailyLimitExtension("keep-action","instagram",date,13,1,37)),setOf(LimitReminderKey("instagram",date,80)))
        val ledger=migrateLimitAppIds(DailyLimitCodec.decode(DailyLimitCodec.encode(old)))
        assertEquals(pkg,ledger.extensions.single().appId);assertEquals("keep-action",ledger.extensions.single().actionId)
        assertEquals(setOf(LimitReminderKey(pkg,date,80)),ledger.satisfied);assertEquals(ledger,migrateLimitAppIds(ledger))
    }
    @Test fun arbitraryInstalledAppsShareUsageLimitsHomeAndStatsAndRemovalDoesNotAlterSavedSessions() {
        val repo=repository();repo.refreshCatalog();repo.addApps(setOf(mail.packageName,notes.packageName))
        repo.saveSettings(ManagedApp(mail.packageName,true,37,true));repo.saveSettings(ManagedApp(notes.packageName,true,60,false))
        val date=LocalDate.of(2026,11,3)
        val raw=DailyUsage(mapOf(mail.packageName to 600,notes.packageName to 1200),0,emptyList(),false,date=date)
        val limits=calculateDailyLimits(raw,repo.apps.value,repo.catalog,DailyLimitLedger(),date)
        val home=homeDailyRemaining(raw.copy(dailyLimits=limits.apps),repo.apps.value,date)
        assertEquals(67*60L,home.remainingSeconds)
        assertEquals(1800,buildUsageStats(StatsPeriod.Today,raw,emptyList(),date).totalSeconds)
        assertEquals(2,enabledForegroundTargets(repo).size)
        repo.removeApps(setOf(mail.packageName));assertEquals(listOf(notes.packageName),repo.apps.value.map {it.appId})
        assertEquals(notes.packageName,enabledForegroundTargets(repo).single().appId)
    }
    @Test fun minimumVisibilityAndBothLanguageCatalogsCoverNewPickerActions() {
        fun file(relative:String)=listOf(File(relative),File("app/$relative")).first {it.exists()}
        val manifest=file("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"));assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        val keys=listOf("ig_remove_apps","ig_remove_selected_apps","ig_remove_this_app","ig_remove_apps_explanation","ig_add_selected_apps",
            "ig_installed_launchable_apps","ig_loading_installed_apps","ig_installed_apps_query_failed","ig_no_addable_installed_apps","ig_app_changes_failed","ig_app_unavailable")
        fun resource(folder:String):Map<String,String> {
            val nodes=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file("src/main/res/$folder/strings.xml")).getElementsByTagName("string")
            return (0 until nodes.length).associate { nodes.item(it).let {node -> node.attributes.getNamedItem("name").nodeValue to node.textContent} }
        }
        val en=resource("values");val zh=resource("values-zh-rCN")
        keys.forEach { key -> assertTrue(en.getValue(key).isNotBlank());assertTrue(zh.getValue(key).any {it in '一'..'鿿'}) }
    }
}
