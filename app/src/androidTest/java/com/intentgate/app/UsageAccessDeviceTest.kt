package com.intentgate.app

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.intentgate.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Run on a dedicated emulator only. Restores original AppOps mode, never grants production permissions. */
class UsageAccessDeviceTest {
    @Test fun readsSystemEventsAndClearsDataAfterPermissionRevocation() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val original=shell("appops get ${context.packageName} GET_USAGE_STATS")
        val restore=Regex("GET_USAGE_STATS: (allow|ignore|deny|default|foreground)").find(original)?.groupValues?.get(1) ?: "default"
        val repo=LocalAppRepository(installedCatalog=AndroidAppDiscovery(context).knownApps())
        val source=AndroidUsageDataSource(context,repo)
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            source.refresh()
            assertEquals(UsageAccess.PermissionRequired,source.today.value.access)
            assertTrue(source.snapshot.value.history.isEmpty())
            assertTrue(openUsageAccess(context))
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            source.refresh()
            val usage=source.snapshot.value.today
            assertFalse(usage.isMock)
            assertTrue(usage.access==UsageAccess.Ready || usage.access==UsageAccess.NoData)
            if(!usage.available || usage.totalSeconds==0) {
                println("LIMITATION: No measured selected-app time available. This is not a positive real-usage reading verification.")
            } else {
                val stats=buildUsageStats(StatsPeriod.Today,usage,source.snapshot.value.history,usage.date)
                assertEquals(usage.secondsByApp.values.sum(),stats.totalSeconds)
            }
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            source.refresh()
            assertEquals(UsageAccess.PermissionRequired,source.today.value.access)
            assertTrue(source.today.value.secondsByApp.isEmpty())
        } finally { shell("appops set ${context.packageName} GET_USAGE_STATS $restore") }
    }
}
