package com.intentgate.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Current-profile MAIN/LAUNCHER visibility only. No QUERY_ALL_PACKAGES or demo fallback. */
interface InstalledAppDiscovery {
    fun installedApps():List<AppDefinition>
    fun resolve(app:AppDefinition):AppDefinition=app.copy(installed=false,launchable=false,systemIcon=null)
}
class AndroidAppDiscovery(context:Context):InstalledAppDiscovery {
    private val application=context.applicationContext
    private val pm=application.packageManager
    override fun installedApps():List<AppDefinition> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).asSequence()
            .mapNotNull { result ->
                try {
                    val activity = result.activityInfo ?: return@mapNotNull null
                    val info = activity.applicationInfo ?: return@mapNotNull null
                    val pkg = activity.packageName
                    if (pkg.isNullOrBlank() || pkg == application.packageName ||
                        !activity.enabled || !activity.exported || !info.enabled ||
                        info.flags and ApplicationInfo.FLAG_SUSPENDED != 0
                    ) return@mapNotNull null
                    AppDefinition(pkg, label(info, pkg), pkg, android.R.drawable.sym_def_app_icon,
                        installed = true, isSystemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0)
                } catch (_:RuntimeException) {
                    null
                }
            }
            .distinctBy { it.packageName }
            .toList()
    }

    private fun label(info: ApplicationInfo, fallback: String): String =
        try { pm.getApplicationLabel(info).toString().trim().take(160).ifBlank { fallback } }
        catch (_:RuntimeException) { fallback }

    override fun resolve(app:AppDefinition):AppDefinition = try {
        val info = pm.getApplicationInfo(app.packageName, 0)
        app.copy(name = label(info, app.name), installed = true, launchable = false, systemIcon = null)
    } catch (_:PackageManager.NameNotFoundException) {
        app.copy(installed = false, launchable = false, systemIcon = null)
    } catch (_:RuntimeException) {
        app.copy(launchable = false, systemIcon = null)
    }
    fun launchablePackages():List<String> = installedApps().map { it.packageName }
    fun knownApps():List<AppDefinition> = installedApps() // Compatibility for existing device tests.
}

fun createAndroidAppRepository(context:Context):AppRepository = LocalAppRepository(
    PreferencesAppSettingsStorage(context),installedCatalog=emptyList(),discovery=AndroidAppDiscovery(context))
