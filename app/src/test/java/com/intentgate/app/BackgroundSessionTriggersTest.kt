package com.intentgate.app

import com.intentgate.app.data.*
import com.intentgate.app.session.SessionStatus
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class BackgroundSessionTriggersTest {
    @Test fun sameAppWindowChangesAndQuickReentryDoNotRepeatPrompt() {
        val entries = DesktopWindowEntries()
        assertTrue(entries.transition("target"))
        assertTrue(entries.canOffer("target", 100L))
        entries.markOffered("target", 100L)
        assertFalse(entries.transition("target"))
        assertTrue(entries.transition("launcher"))
        assertTrue(entries.transition("target"))
        assertFalse(entries.canOffer("target", 3_000L))
        assertTrue(entries.canOffer("target", 10_100L))
    }

    @Test fun deadlineWakesOnlyForCurrentTargetAndDoesNotDebitTimeAway() {
        val session = IntentSession("s", "r", "target", "target", "Target", 0, "Read", 1_000L,
            60_000L, 5_000L, windows = listOf(SessionWindow(1_000L)))
        assertEquals(2_000L, backgroundExpiryDelay(listOf(session), "target"))
        assertEquals(2_000L, backgroundExpiryDelay(listOf(session), null))
        assertEquals(1_000L, backgroundExpiryDelay(listOf(session.copy(remainingMillis = 800L)), "target"))
        assertEquals(5_000L, sessionCheckDelay(listOf(session), "target"))
        assertEquals(30_000L, sessionCheckDelay(listOf(session), "launcher"))
        assertEquals(30_000L, sessionCheckDelay(listOf(session.copy(status = SessionStatus.Paused)), "target"))
        val events = listOf(ForegroundEvent(1_000L, "target", "Main", 1),
            ForegroundEvent(4_000L, "target", "Main", 2), ForegroundEvent(70_000L, "target", "Main", 1))
        assertEquals(3_000L, sessionWindowCredits(session, events, 69_000L, ZoneOffset.UTC).single().first)
        assertEquals(5_000L, sessionWindowCredits(session, events, 72_000L, ZoneOffset.UTC).single().first)
    }

    @Test fun anotherAppsResumeStopsCreditEvenBeforeTargetPauseArrives() {
        val session = IntentSession("s", "r", "target", "target", "Target", 0, "Read", 2_000L,
            60_000L, 60_000L, windows = listOf(SessionWindow(2_000L)))
        val events = listOf(ForegroundEvent(1_000L, "target", "Main", 1),
            ForegroundEvent(5_000L, "other", "Main", 1), ForegroundEvent(50_000L, "target", "Main", 1))
        assertEquals(3_000L, sessionWindowCredits(session, events, 49_000L, ZoneOffset.UTC).single().first)
        assertEquals(5_000L, sessionWindowCredits(session, events, 52_000L, ZoneOffset.UTC).single().first)
        assertEquals("target", latestForegroundResume(events)?.packageName)
    }
}
