package com.intentgate.app

import com.intentgate.app.data.*
import org.junit.Assert.*
import org.junit.Test

class AppRepositoryTest {
    private class MemoryStorage : AppSettingsStorage {
        var saved: List<ManagedApp>? = null
        override fun read() = saved
        override fun write(apps: List<ManagedApp>) { saved = apps.toList() }
    }

    @Test fun freshRepositoryHasNoImplicitManagementAndRecreationPreservesEmptySelection() {
        val storage=MemoryStorage()
        repeat(2) {
            val repo=LocalAppRepository(storage)
            assertTrue(repo.apps.value.isEmpty())
            assertTrue(repo.refreshCatalog())
            assertTrue(repo.apps.value.isEmpty())
        }
        val repo=LocalAppRepository(storage)
        assertTrue(repo.addApp("instagram"))
        assertTrue(repo.removeApps(setOf("instagram")))
        assertEquals(emptyList<ManagedApp>(),storage.saved)
        assertTrue(LocalAppRepository(storage).apps.value.isEmpty())
    }
    @Test fun savedChoicesAndLimitsAreNotDeletedEvenForFormerDefaultAppNames() {
        val storage=MemoryStorage().apply { saved=listOf(
            ManagedApp("instagram",false,73,false),ManagedApp("youtube",true,95,true),
            ManagedApp("chrome",true,187,false),ManagedApp("reddit",false,33,true)) }
        val expected=storage.saved
        repeat(2) { assertEquals(expected,LocalAppRepository(storage).apps.value) }
        assertEquals(expected,storage.saved)
    }
    @Test fun managementTogglePreservesLimitAndIntentSetting() {
        val repository = LocalAppRepository().also { assertTrue(it.addApp("instagram")) }
        val original = repository.apps.value.first()
        repository.saveSettings(original.copy(dailyLimitMinutes = 55, intentCheckEnabled = false))
        repository.setManaged(original.appId, false)
        assertEquals(original.copy(isManaged = false, dailyLimitMinutes = 55, intentCheckEnabled = false),
            repository.apps.value.first())
    }

    @Test fun savedSettingsAndAddedAppsSurviveRepositoryRecreation() {
        val storage = MemoryStorage()
        val first = LocalAppRepository(storage).also { assertTrue(it.addApp("instagram")) }
        first.saveSettings(first.apps.value.first().copy(dailyLimitMinutes = 25, intentCheckEnabled = false))
        assertTrue(first.addApp("demo_notes"))
        assertEquals(first.apps.value, LocalAppRepository(storage).apps.value)
    }

    @Test fun catalogRejectsDuplicatesAndUnknownApps() {
        val repository = LocalAppRepository().also { assertTrue(it.addApp("instagram")) }
        assertFalse(repository.addApp("instagram"))
        assertFalse(repository.addApp("not-installed"))
        assertTrue(repository.addApp("demo_reader"))
        assertFalse(repository.addApp("demo_reader"))
        assertEquals(2, repository.apps.value.size)
    }

    @Test fun invalidLimitDoesNotChangeExistingState() {
        val repository = LocalAppRepository().also { assertTrue(it.addApp("instagram")) }
        val original = repository.apps.value
        for (limit in listOf(0, 1441)) {
            assertThrows(IllegalArgumentException::class.java) {
                repository.saveSettings(original.first().copy(dailyLimitMinutes = limit))
            }
        }
        assertEquals(original, repository.apps.value)
    }

    @Test fun homeAndAppsUseTheSameExplicitlyMockedUsage() {
        val usage = MockUsageDataSource().today.value
        assertTrue(usage.isMock)
        assertEquals(84 * 60, usage.totalSeconds)
        assertEquals("1h 24m", usage.totalLabel)
        assertEquals(8, usage.sessions)
    }
}
