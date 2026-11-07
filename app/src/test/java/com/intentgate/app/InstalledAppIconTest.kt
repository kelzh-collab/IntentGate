package com.intentgate.app

import com.intentgate.app.ui.loadInstalledIcon
import org.junit.Assert.*
import org.junit.Test

class InstalledAppIconTest {
    @Test fun installedAppLoadsItsIconByPackageName() {
        var requested: String? = null
        val icon = loadInstalledIcon("org.example.reader", true, null) { requested = it; "device icon" }
        assertEquals("org.example.reader", requested)
        assertEquals("device icon", icon)
    }

    @Test fun unknownInstallationStateStillQueriesTheDevice() {
        val icon = loadInstalledIcon("org.example.reader", null, null) { "device icon" }
        assertEquals("device icon", icon)
    }

    @Test fun missingAppDiscardsStaleCachedArtworkAndDoesNotQuery() {
        var queried = false
        val icon = loadInstalledIcon("org.example.removed", false, "stale icon") { queried = true; "icon" }
        assertNull(icon)
        assertFalse(queried)
    }

    @Test fun absentPackageProducesThePlaceholderSignal() {
        assertNull(loadInstalledIcon<String>("org.example.absent", null, null) { null })
    }

    @Test fun cachedInstalledIconAvoidsAnotherPackageQuery() {
        val icon = loadInstalledIcon("org.example.reader", true, "cached device icon") { error("Unexpected query") }
        assertEquals("cached device icon", icon)
    }

    @Test fun blankPackageNeverUsesCachedArtworkOrQueries() {
        val icon = loadInstalledIcon("", null, "stale icon") { error("Unexpected query") }
        assertNull(icon)
    }
}
