package com.intentgate.app

import com.intentgate.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyLimitTest {
    private class Clock:LimitClock {
        var date=LocalDate.of(2026,11,3);var wall=1000L
        override fun today()=date
        override fun wallMillis()=wall
    }
    private class Fixture {
        val clock=Clock();val storage=MemoryDailyLimitStorage();val sent=mutableListOf<DailyLimitReminder>()
        var canNotify=true
        val app=AppDefinition("instagram","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder,installed=true)
        var settings=ManagedApp(app.id,dailyLimitMinutes=40)
        val repo=DailyLimitRepository(storage,LimitReminderSink { if(canNotify) {sent+=it;true} else false },clock)
        fun usage(seconds:Int,access:UsageAccess=UsageAccess.Ready,mock:Boolean=false,date:LocalDate=clock.date)=
            DailyUsage(mapOf(app.id to seconds),0,emptyList(),mock,access=access,date=date)
        fun refresh(seconds:Int,permission:Boolean=true) { repo.refresh(usage(seconds),listOf(settings),listOf(app),permission) }
        val state get()=repo.state.value.apps.getValue(app.id)
    }
    @Test fun below80PercentDoesNotRemindAndUsesExactSeconds() {
        val f=Fixture();f.refresh(1919)
        assertTrue(f.sent.isEmpty());assertEquals(481L,f.state.remainingSeconds)
        assertEquals(79,f.state.percent);assertFalse(f.state.reached)
    }
    @Test fun exactly80RemindsOnceWithCorrectAppUsageAndLimit() {
        val f=Fixture();f.refresh(1920);f.refresh(1920);f.refresh(1930)
        val reminder=f.sent.single()
        assertEquals(80,reminder.key.threshold);assertEquals("Instagram",reminder.appName)
        assertEquals(1920L,reminder.usedSeconds);assertEquals(2400L,reminder.limitSeconds)
        assertEquals(80,f.state.percent)
    }
    @Test fun reaching100ProducesLimitReachedAndNeverNegativeRemaining() {
        val f=Fixture();f.refresh(1920);f.refresh(2400);f.refresh(2500)
        assertEquals(listOf(80,100),f.sent.map { it.key.threshold })
        assertTrue(f.state.reached);assertEquals("Limit reached",f.state.statusLabel)
        assertEquals(0L,f.state.remainingSeconds);assertEquals(104,f.state.percent)
    }
    @Test fun jumpPast100SendsOnlyOvertimeAndCovers80ForThatDay() {
        val f=Fixture();f.refresh(2500)
        assertEquals(listOf(100),f.sent.map { it.key.threshold })
        f.settings=f.settings.copy(dailyLimitMinutes=50);f.refresh(2500)
        assertFalse(f.state.reached);assertEquals(83,f.state.percent)
        assertEquals(1,f.sent.size)
    }
    @Test fun notificationDedupePersistsAcrossRepositoryRecreation() {
        val f=Fixture();f.refresh(2400)
        val restored=DailyLimitRepository(f.storage,LimitReminderSink { f.sent+=it;true },f.clock)
        restored.refresh(f.usage(2400),listOf(f.settings),listOf(f.app))
        assertEquals(1,f.sent.size)
    }
    @Test fun changingSavedBaseLimitRecalculatesAndCanCrossNewThreshold() {
        val f=Fixture();f.refresh(1800)
        assertEquals(600L,f.state.remainingSeconds)
        f.settings=f.settings.copy(dailyLimitMinutes=30);f.refresh(1800)
        assertTrue(f.state.reached);assertEquals(100,f.sent.single().key.threshold)
        f.settings=f.settings.copy(dailyLimitMinutes=60);f.refresh(1800)
        assertFalse(f.state.reached);assertEquals(1800L,f.state.remainingSeconds)
        assertEquals(1,f.sent.size)
    }
    @Test fun extensionsAreTodayOnlyAuditedAndIdempotent() {
        val f=Fixture();f.refresh(2400)
        assertTrue(f.repo.extendToday(f.app.id,15,"extension-action"))
        assertTrue(f.repo.extendToday(f.app.id,15,"extension-action"))
        assertEquals(1,f.repo.extensions.size);assertEquals(55*60L,f.state.limitSeconds)
        assertEquals(15*60L,f.state.remainingSeconds);assertFalse(f.state.reached)
        val entry=f.repo.extensions.single()
        assertEquals(f.clock.date,entry.date);assertEquals(40,entry.baseLimitMinutes);assertEquals(1000L,entry.createdAtMillis)
        assertEquals(1,f.sent.size)
    }
    @Test fun midnightDoesNotDeleteAuditAndOldUsageIsNotAssumedToBeTodaysZero() {
        val f=Fixture();f.refresh(2400);f.repo.extendToday(f.app.id,15,"day-one")
        val old=f.clock.date;f.clock.date=old.plusDays(1)
        f.repo.refresh(f.usage(2400,date=old),listOf(f.settings),listOf(f.app))
        assertEquals(LimitAvailability.Stale,f.state.availability);assertNull(f.state.usedSeconds)
        assertEquals(0L,f.state.extraMinutes);assertEquals(1,f.repo.extensions.size)
        assertFalse(f.repo.extendToday(f.app.id,15,"stale-action"))
        f.refresh(1920)
        assertEquals(2,f.sent.size);assertEquals(f.clock.date,f.sent.last().key.date)
        assertEquals(old,f.repo.extensions.first().date)
    }
    @Test fun revokedPermissionSafelyClearsDerivedUsageWithoutDuplicateAfterRegrant() {
        val f=Fixture();f.refresh(1920);f.refresh(2400,permission=false)
        assertEquals(LimitAvailability.PermissionRequired,f.state.availability)
        assertNull(f.state.usedSeconds);assertFalse(f.state.reached);assertEquals(1,f.sent.size)
        assertFalse(f.repo.extendToday(f.app.id,5,"denied"))
        f.refresh(2400)
        assertEquals(listOf(80,100),f.sent.map { it.key.threshold })
    }
    @Test fun mockMissingAndNoDataNeverGenerateRealLimitOrReminder() {
        val f=Fixture()
        for(usage in listOf(f.usage(9999,mock=true),f.usage(9999,access=UsageAccess.NoData),
            f.usage(9999,access=UsageAccess.Locked),f.usage(9999,access=UsageAccess.Error),f.usage(9999).copy(secondsByApp=emptyMap()))) {
            f.repo.refresh(usage,listOf(f.settings),listOf(f.app))
            assertNull(f.state.remainingSeconds);assertFalse(f.state.reached)
        }
        assertTrue(f.sent.isEmpty())
    }
    @Test fun pausedManagementNeverNotifiesAndReenableUsesSavedLimit() {
        val f=Fixture();f.settings=f.settings.copy(isManaged=false);f.refresh(3000)
        assertTrue(f.sent.isEmpty());assertEquals("Paused",f.state.statusLabel)
        assertFalse(f.repo.extendToday(f.app.id,10,"paused"))
        f.settings=f.settings.copy(isManaged=true);f.refresh(3000)
        assertEquals(100,f.sent.single().key.threshold)
    }
    @Test fun deniedNotificationDeliveryDoesNotConsumeThresholdAndCanRetry() {
        val f=Fixture();f.canNotify=false;f.refresh(1920);f.refresh(1920)
        assertTrue(f.sent.isEmpty());assertTrue(f.storage.ledger.satisfied.isEmpty())
        f.canNotify=true;f.refresh(1920);f.refresh(1920)
        assertEquals(1,f.sent.size)
    }
    @Test fun notificationExceptionsDoNotCrashOrConsumeDeliveryKey() {
        val f=Fixture()
        val repo=DailyLimitRepository(f.storage,LimitReminderSink { throw SecurityException("revoked") },f.clock)
        repo.refresh(f.usage(2500),listOf(f.settings),listOf(f.app))
        assertTrue(repo.state.value.apps.getValue(f.app.id).reached)
        assertTrue(f.storage.ledger.satisfied.isEmpty())
    }
    @Test fun unavailableStoragePreventsNotificationsAndExtensionWithoutCrash() {
        val f=Fixture()
        val storage=object:DailyLimitStorage {
            override fun read()=DailyLimitLedger()
            override fun write(ledger:DailyLimitLedger) { throw IllegalStateException("storage full") }
        }
        val repo=DailyLimitRepository(storage,LimitReminderSink { f.sent+=it;true },f.clock)
        repo.refresh(f.usage(2500),listOf(f.settings),listOf(f.app))
        assertTrue(f.sent.isEmpty());assertFalse(repo.extendToday(f.app.id,5,"failure"))
    }
    @Test fun intentDurationWarnsButDoesNotChangeAndroidUsageOrLimit() {
        val f=Fixture();f.refresh(2100)
        assertNull(sessionDailyLimitWarning(f.state,5))
        assertTrue(sessionDailyLimitWarning(f.state,10)!!.contains("exceeds"))
        assertTrue(sessionDailyLimitWarning(null,10)!!.contains("unavailable"))
        val usage=f.usage(2100).copy(sessions=500,intentions=MockIntentions.records)
        f.repo.refresh(usage,listOf(f.settings),listOf(f.app))
        assertEquals(2100L,f.state.usedSeconds);assertEquals(300L,f.state.remainingSeconds)
    }
    @Test fun codecPreservesExtensionHistoryAndAllReminderKeys() {
        val f=Fixture();f.refresh(1920);f.repo.extendToday(f.app.id,15,"round-trip")
        assertEquals(f.storage.ledger,DailyLimitCodec.decode(DailyLimitCodec.encode(f.storage.ledger)))
        f.clock.date=f.clock.date.minusDays(1);f.refresh(1920)
        f.clock.date=f.clock.date.plusDays(1);f.refresh(2400)
        assertEquals(1,f.sent.count { it.key.date==f.clock.date && it.key.threshold==80 })
    }
    @Test fun unknownInstallationAndInvalidExtensionInputsNeverInventUsage() {
        val f=Fixture();f.repo.refresh(f.usage(3000),listOf(f.settings),listOf(f.app.copy(installed=false)))
        assertEquals(LimitAvailability.NotInstalled,f.state.availability);assertNull(f.state.usedSeconds);assertTrue(f.sent.isEmpty())
        f.refresh(100)
        assertFalse(f.repo.extendToday(f.app.id,0,"zero"));assertFalse(f.repo.extendToday(f.app.id,1441,"large"))
        assertFalse(f.repo.extendToday(f.app.id,15,""));assertTrue(f.repo.extensions.isEmpty())
    }
}
