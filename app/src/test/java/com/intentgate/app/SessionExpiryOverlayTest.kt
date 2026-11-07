package com.intentgate.app

import android.app.NotificationManager
import com.intentgate.app.data.canMigrateExpiryChannel
import com.intentgate.app.data.ForegroundEvent
import com.intentgate.app.data.expiryTargetIsForeground
import org.junit.Assert.*
import org.junit.Test

class SessionExpiryOverlayTest {
    private val target = "target.app"
    private val resumed = ForegroundEvent(10, target, "Main", 1)

    @Test fun bannerMigrationPreservesExistingQuietOrUserConfiguredChannels() {
        val normal = NotificationManager.IMPORTANCE_DEFAULT
        assertTrue(canMigrateExpiryChannel(37, normal, true, false, false, false))
        assertFalse(canMigrateExpiryChannel(37, NotificationManager.IMPORTANCE_NONE, true, false, true, false))
        assertFalse(canMigrateExpiryChannel(37, NotificationManager.IMPORTANCE_LOW, true, false, false, false))
        assertFalse(canMigrateExpiryChannel(37, normal, false, false, false, true))
        assertFalse(canMigrateExpiryChannel(37, normal, true, false, true, false))
        assertFalse(canMigrateExpiryChannel(28, normal, true, false, false, false))
    }

    @Test fun requiresObservedTargetResume() {
        assertFalse(expiryTargetIsForeground(emptyList(), target))
        assertTrue(expiryTargetIsForeground(listOf(resumed), target))
        assertFalse(expiryTargetIsForeground(listOf(resumed, ForegroundEvent(20, "other.app", "Main", 1)), target))
    }

    @Test fun pauseStopOrScreenLockPreventsCrossAppOverlay() {
        for (type in listOf(2, 23, 16, 17, 26, 27)) {
            assertFalse(expiryTargetIsForeground(listOf(resumed, ForegroundEvent(20, target, "Main", type)), target))
        }
    }

    @Test fun aPreviousActivityStoppingDoesNotClearNewTargetActivity() {
        val next = ForegroundEvent(20, target, "Second", 1)
        val oldStop = ForegroundEvent(30, target, "Main", 23)
        assertTrue(expiryTargetIsForeground(listOf(oldStop, next, resumed), target))
    }
}
