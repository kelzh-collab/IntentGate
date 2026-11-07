package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Only Home daily aggregation and its separation from stored Intent-session timing. */
class HomeDailyRemainingTest {
    private val date=LocalDate.of(2026,11,3)
    private val a=AppDefinition("a","Instagram","com.instagram.android",R.drawable.ic_app_icon_placeholder,true)
    private val b=AppDefinition("b","YouTube","com.google.android.youtube",R.drawable.ic_app_icon_placeholder,true)
    private val c=AppDefinition("c","Chrome","com.android.chrome",R.drawable.ic_app_icon_placeholder,true)
    private val apps=listOf(ManagedApp("a",dailyLimitMinutes=37),ManagedApp("b",dailyLimitMinutes=20),ManagedApp("c",false,100))
    private val catalog=listOf(a,b,c)
    private val ledger=DailyLimitLedger(extensions=listOf(
        DailyLimitExtension("today","a",date,13,1,37),DailyLimitExtension("old","b",date.minusDays(1),99,0,20)))
    private fun raw(seconds:Map<String,Int> = mapOf("a" to 1080,"b" to 1500,"c" to 0))=
        DailyUsage(seconds,0,emptyList(),false,date=date)
    private fun projected(usage:DailyUsage=raw(),settings:List<ManagedApp> = apps,book:DailyLimitLedger=ledger):DailyUsage =
        usage.copy(dailyLimits=calculateDailyLimits(usage,settings,catalog,book,date).apps)
    private fun remaining(usage:DailyUsage=projected(),settings:List<ManagedApp> = apps)=homeDailyRemaining(usage,settings,date)

    @Test fun sumsClampedPerAppRemaindersWithCustomLimitAndTodaysExtensionOnly() {
        val result=remaining()
        assertTrue(result.available);assertEquals(2,result.appCount)
        assertEquals(70*60L,result.limitSeconds)
        // (37 + 13 - 18) + max(0,20-25); paused Chrome and yesterday's extension are excluded.
        assertEquals(32*60L,result.remainingSeconds)
        assertEquals(32f/70,result.progress,.0001f)
    }
    @Test fun usageSettingsExtensionsAndManagementChangesRecalculateFromSharedProjection() {
        assertEquals(31*60L,remaining(projected(raw(mapOf("a" to 1140,"b" to 1500,"c" to 0)))).remainingSeconds)
        val edited=apps.map { if(it.appId=="a") it.copy(dailyLimitMinutes=187) else it }
        assertEquals(182*60L,remaining(projected(settings=edited),edited).remainingSeconds)
        val extended=ledger.copy(extensions=ledger.extensions+DailyLimitExtension("extra","b",date,12,2,20))
        assertEquals(39*60L,remaining(projected(book=extended)).remainingSeconds)
        val paused=apps.map { it.copy(isManaged=false) }
        val empty=remaining(projected(settings=paused),paused)
        assertEquals(0,empty.appCount);assertEquals(0L,empty.remainingSeconds);assertEquals(0f,empty.progress)
        // Do not briefly display yesterday's settings while the shared limit flow catches up.
        assertNull(remaining(projected(),edited).remainingSeconds)
    }
    @Test fun unavailableAndDemoDataNeverProduceAPartialOrFabricatedTotal() {
        for(access in listOf(UsageAccess.PermissionRequired,UsageAccess.NoData,UsageAccess.Locked,UsageAccess.Loading,UsageAccess.Error,UsageAccess.Demo)) {
            val result=remaining(projected().copy(access=access))
            assertFalse(result.available);assertNull(result.remainingSeconds);assertEquals(0f,result.progress)
        }
        assertNull(remaining(projected().copy(isMock=true)).remainingSeconds)
        val missing=raw().copy(secondsByApp=mapOf("a" to 1080))
        assertNull(remaining(projected(missing)).remainingSeconds)
        val oldLimit=projected().dailyLimits.mapValues { it.value.copy(date=date.minusDays(1)) }
        assertNull(remaining(projected().copy(dailyLimits=oldLimit)).remainingSeconds)
        assertNull(remaining(projected().copy(date=date.minusDays(1))).remainingSeconds)
        assertNull(remaining(projected().copy(dailyLimits=emptyMap())).remainingSeconds)
        assertNull(remaining(projected().copy(secondsByApp=mapOf("a" to 1081,"b" to 1500,"c" to 0))).remainingSeconds)
    }
    @Test fun noLimitAppsAndExhaustedBudgetsAreHandledWithoutNegativeValuesOrDivisionByZero() {
        val none=listOf(ManagedApp("a",dailyLimitMinutes=0))
        val result=remaining(projected(settings=none),none)
        assertFalse(result.hasLimits);assertEquals(0L,result.limitSeconds);assertEquals(0f,result.progress)
        val exhausted=remaining(projected(raw(mapOf("a" to 5000,"b" to 3000,"c" to 0))))
        assertEquals(0L,exhausted.remainingSeconds);assertEquals(0f,exhausted.progress)
    }
    @Test fun arbitraryTotalsUseHoursMinutesAndKeepExactSecondsInTheModel() {
        assertEquals("0h 01m",formatDailyRemaining(1));assertEquals("0h 37m",formatDailyRemaining(37*60))
        assertEquals("3h 07m",formatDailyRemaining(187*60));assertEquals("120h 00m",formatDailyRemaining(120*3600))
        assertEquals("0h 00m",formatDailyRemaining(0));assertEquals("0h 00m",formatDailyRemaining(-1))
        assertEquals(1919L,remaining(projected(raw(mapOf("a" to 1081,"b" to 1500,"c" to 0)))).remainingSeconds)
    }
    @Test fun homeDoesNotStartOrDebitSessionAndSavedSessionControlsRemainIndependent() {
        var now=date.atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val storage=MemoryIntentSessionStorage();val sessions=IntentSessionRepository(storage){now}
        val before=sessions.state.value
        repeat(2) { remaining() }
        assertEquals(before,sessions.state.value);assertNull(sessions.state.value.active)
        val event=ForegroundDetectionRecord("home-time",DetectionKind.Enter,a.id,a.packageName,now,now,0,0,"Eligible",true)
        val request=requireNotNull(sessions.offer(event,a,true,setOf(a.id)))
        assertNull(sessions.state.value.active) // Detection alone still does not start the timer.
        val session=requireNotNull(sessions.start(request.key,"Reply to messages",7){true})
        assertEquals(7*60_000L,session.remainingMillis)
        now+=120_000;sessions.credit(session.id,session.windows,listOf(120_000L to now))
        val raw=raw();val shared=projectIntentSessions(UsageSnapshot(projected(raw)),sessions.state.value,ZoneOffset.UTC).today
        assertEquals(raw.secondsByApp,shared.secondsByApp)
        assertEquals(32*60L,remaining(shared).remainingSeconds)
        assertEquals(5*60_000L,sessions.state.value.active!!.remainingMillis)
        sessions.pause();assertEquals(SessionStatus.Paused,sessions.state.value.active!!.status)
        sessions.resume();assertEquals(SessionStatus.Running,sessions.state.value.active!!.status)
        val copy=MemoryIntentSessionStorage(IntentSessionCodec.decode(IntentSessionCodec.encode(storage.book)))
        val restored=IntentSessionRepository(copy){now}
        assertEquals(SessionStatus.Paused,restored.state.value.active!!.status)
        assertEquals(5*60_000L,restored.state.value.active!!.remainingMillis)
        restored.resume();restored.end()
        assertNull(restored.state.value.active);assertEquals(SessionStatus.Ended,restored.state.value.sessions.last().status)
        assertEquals(32*60L,remaining().remainingSeconds)
    }
    @Test fun newHomeMessagesHaveEnglishAndChineseResources() {
        fun resources(folder:String):Map<String,String> {
            val path=listOf(File("src/main/res/$folder/strings.xml"),File("app/src/main/res/$folder/strings.xml")).first { it.exists() }
            val nodes=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path).getElementsByTagName("string")
            return (0 until nodes.length).associate { nodes.item(it).let { node -> node.attributes.getNamedItem("name").nodeValue to node.textContent } }
        }
        val en=resources("values");val zh=resources("values-zh-rCN")
        val keys=listOf("ig_daily_remaining_title","ig_daily_budget_total","ig_hours_minutes","ig_daily_managed_apps","ig_daily_remaining_details",
            "ig_daily_budget_remaining","ig_no_session_started","ig_no_active_daily_limits","ig_managed_app_not_installed","ig_today_demo_unavailable")
        keys.forEach { key -> assertTrue(en.getValue(key).isNotBlank());assertTrue(zh.getValue(key).any { it in '一'..'鿿' }) }
    }
}
