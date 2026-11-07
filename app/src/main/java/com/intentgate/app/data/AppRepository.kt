package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class AppDefinition(val id: String, val name: String, val packageName: String, val iconRes: Int, val installed: Boolean? = null, val systemIcon: android.graphics.drawable.Drawable? = null, val launchable:Boolean = true, val isSystemApp:Boolean = false) {
    val isAvailable:Boolean get()=installed!=false && launchable
}

data class ManagedApp(
    val appId: String,
    val isManaged: Boolean = true,
    val dailyLimitMinutes: Int = 40,
    val intentCheckEnabled: Boolean = true,
)

/** Shared user-selected management settings and installed-app discovery boundary. */
interface AppRepository {
    val catalog: List<AppDefinition>
    val catalogState:StateFlow<List<AppDefinition>>? get()=null
    val catalogReady:Boolean get()=true
    fun refreshCatalog():Boolean=true
    val apps: StateFlow<List<ManagedApp>>
    fun setManaged(appId: String, enabled: Boolean)
    fun saveSettings(settings: ManagedApp)
    fun addApp(appId: String): Boolean
    fun addApps(appIds:Set<String>):Boolean=appIds.map(::addApp).any { it }
    fun removeApps(appIds:Set<String>):Boolean=false
}

interface AppSettingsStorage {
    fun read(): List<ManagedApp>?
    fun write(apps: List<ManagedApp>)
    fun readNames():Map<String,String> = emptyMap()
    fun writeNames(names:Map<String,String>) {}
}

/** Package names are canonical in Android-backed repositories; no-arg fixtures are retained for existing JVM tests only. */
class LocalAppRepository(private val storage:AppSettingsStorage?=null,installedCatalog:List<AppDefinition>?=null,
                         private val discovery:InstalledAppDiscovery?=null):AppRepository {
    private val packageIds=installedCatalog!=null || discovery!=null
    @Volatile override var catalogReady:Boolean=discovery==null
        private set
    private val saved=storage?.read()
    private val names=(storage?.readNames() ?: emptyMap()).toMutableMap()
    private val fixtures=listOf(
        AppDefinition("instagram","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder),
        AppDefinition("youtube","YouTube","com.google.android.youtube",R.drawable.ic_app_icon_placeholder),
        AppDefinition("chrome","Chrome","com.android.chrome",R.drawable.ic_app_icon_placeholder),
        AppDefinition("reddit","Reddit","com.reddit.frontpage",R.drawable.ic_app_icon_placeholder),
        AppDefinition("demo_notes","Notes (demo)","demo.intentgate.notes",R.drawable.demo_notes),
        AppDefinition("demo_reader","Reader (demo)","demo.intentgate.reader",R.drawable.demo_reader))
    private val initial=(saved ?: emptyList())
        .filter { it.dailyLimitMinutes in 1..1440 && (!packageIds || it.appId !in setOf("demo_notes","demo_reader")) }
        .map { if(packageIds) it.copy(appId=AppIdentity.packageId(it.appId)) else it }.distinctBy { it.appId }
    private fun normalize(app:AppDefinition)=if(packageIds) app.copy(id=app.packageName) else app
    private fun missing(id:String)=AppDefinition(id,names[id] ?: AppIdentity.legacyName(id) ?: id,id,
        android.R.drawable.sym_def_app_icon,installed=false,launchable=false)
    private val catalogMutable=MutableStateFlow(if(packageIds) {
        val discovered=installedCatalog.orEmpty().map(::normalize).distinctBy { it.packageName }
        discovered+initial.filter { config -> discovered.none { it.id==config.appId } }.map { missing(it.appId) }
    } else fixtures)
    override val catalogState=catalogMutable.asStateFlow()
    override val catalog:List<AppDefinition> get()=catalogMutable.value
    private val state=MutableStateFlow(initial.filter { item -> packageIds || catalog.any { it.id==item.appId } })
    override val apps=state.asStateFlow()
    init { if(packageIds && saved!=null && saved!=initial) storage?.write(initial) }
    private fun id(value:String)=if(packageIds) AppIdentity.packageId(value) else value
    override fun refreshCatalog():Boolean {
        val provider=discovery ?: return true
        return try {
            val discovered=provider.installedApps().map(::normalize).filter { it.isAvailable }.distinctBy { it.packageName }
            synchronized(this) {
                val retained=state.value.filter { config -> discovered.none { it.id==config.appId } }.map { config ->
                    val previous = catalog.firstOrNull { it.id == config.appId } ?: missing(config.appId)
                    try { normalize(provider.resolve(previous)) }
                    catch (_:RuntimeException) { previous.copy(launchable = false, systemIcon = null) }
                }
                val next=(discovered+retained).sortedWith(compareBy<AppDefinition> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageName })
                names.putAll(next.associate { it.packageName to it.name })
                // A display-name cache failure should not hide launchable apps.
                try { storage?.writeNames(names) } catch (_:RuntimeException) {}
                catalogReady=true
                catalogMutable.value=next
            }
            true
        } catch(_:RuntimeException) {
            // A failed/filtered query must never leave formerly cached apps addable or monitored.
            synchronized(this) { catalogReady=true;catalogMutable.value=catalog.map { it.copy(launchable=false) } }
            false
        }
    }
    @Synchronized override fun setManaged(appId:String,enabled:Boolean) {
        state.value.firstOrNull { it.appId==id(appId) }?.let { saveSettings(it.copy(isManaged=enabled)) }
    }
    @Synchronized override fun saveSettings(settings:ManagedApp) {
        require(settings.dailyLimitMinutes in 1..1440) { "Daily limit must be between 1 and 1440 minutes" }
        val normalized=settings.copy(appId=id(settings.appId))
        require(state.value.any { it.appId==normalized.appId }) { "App is not in the managed list" }
        commit(state.value.map { if(it.appId==normalized.appId) normalized else it })
    }
    override fun addApp(appId:String)=addApps(setOf(appId))
    @Synchronized override fun addApps(appIds:Set<String>):Boolean {
        val selected=appIds.map(::id).toSet()-state.value.map { it.appId }.toSet()
        if(selected.isEmpty() || selected.any { candidate -> catalog.none { it.id==candidate && it.isAvailable } }) return false
        return try { commit(state.value+selected.map { ManagedApp(it) });true } catch(_:RuntimeException) { false }
    }
    @Synchronized override fun removeApps(appIds:Set<String>):Boolean {
        val selected=appIds.map(::id).toSet();val next=state.value.filterNot { it.appId in selected }
        if(next.size==state.value.size) return false
        return try { commit(next);true } catch(_:RuntimeException) { false }
    }
    private fun commit(next:List<ManagedApp>) {
        names.putAll(catalog.associate { it.packageName to it.name });storage?.writeNames(names)
        storage?.write(next);state.value=next
    }
}

object AppIdentity {
    private val legacy=mapOf("instagram" to "com.instagram.android","youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome","reddit" to "com.reddit.frontpage")
    fun packageId(value:String)=legacy[value] ?: value
    fun legacyName(packageName:String)=when(packageName) {
        "com.instagram.android" -> "Instagram";"com.google.android.youtube" -> "YouTube"
        "com.android.chrome" -> "Chrome";"com.reddit.frontpage" -> "Reddit";else -> null
    }
}

/** Shared filtering for the picker: a launcher activity is not a distinct app identity. */
fun addableInstalledApps(catalog:List<AppDefinition>,managed:List<ManagedApp>,query:String=""):List<AppDefinition> {
    val added=managed.map { AppIdentity.packageId(it.appId) }.toSet();val text=query.trim()
    return catalog.filter { it.isAvailable && it.packageName !in added &&
        (it.name.contains(text,ignoreCase=true) || it.packageName.contains(text,ignoreCase=true)) }
        .distinctBy { it.packageName }.sortedWith(compareBy<AppDefinition> { it.isSystemApp }
            .thenBy { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageName })
}

data class DailyUsage(
    val secondsByApp: Map<String, Int>,
    val sessions: Int,
    val intentions: List<IntentionRecord>,
    val isMock: Boolean,
    val intentOutcomes: List<SessionIntentOutcome>? = null,
    val hourlySeconds: List<Int>? = null,
    val access: UsageAccess = if (isMock) UsageAccess.Demo else UsageAccess.Ready,
    val intentStatsAvailable: Boolean = true,
    val intentionsAreDemo: Boolean = isMock,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val dailyLimits: Map<String,DailyLimitState> = emptyMap(),

) {
    val available: Boolean get() = access == UsageAccess.Ready || access == UsageAccess.Demo
    val sourceLabel: String get() = when(access) {
        UsageAccess.Demo -> loc(R.string.ig_demo_data)
        UsageAccess.Ready -> loc(R.string.ig_android_data)
        UsageAccess.PermissionRequired -> loc(R.string.ig_access_needed)
        UsageAccess.Locked -> loc(R.string.ig_unlock_device)
        UsageAccess.NoData -> loc(R.string.ig_no_system_records)
        UsageAccess.Loading -> loc(R.string.ig_loading_usage)
        UsageAccess.Error -> loc(R.string.ig_usage_unavailable)
    }
    val totalSeconds: Int get() = secondsByApp.values.sum()
    val totalLabel: String get() = loc(R.string.ig_h_m, totalSeconds / 3600, totalSeconds % 3600 / 60)
}

/** Android UsageEvents can replace this provider without changing the screen models. */
interface UsageDataSource {
    val today: StateFlow<DailyUsage>
    val snapshot: StateFlow<UsageSnapshot>? get() = null
    val referenceDate: LocalDate get() = LocalDate.now()
    val history: StateFlow<List<DatedUsage>> get() = EmptyUsageHistory
    suspend fun refresh()
}
