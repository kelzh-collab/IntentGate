package com.intentgate.app

import com.intentgate.app.data.ExpiryOverlayMount
import com.intentgate.app.data.ExpiryOverlayPhase
import com.intentgate.app.data.IntentSession
import com.intentgate.app.data.IntentSessionBook
import com.intentgate.app.data.IntentSessionRepository
import com.intentgate.app.data.MemoryIntentSessionStorage
import com.intentgate.app.data.ExpiryDecision
import com.intentgate.app.session.SessionStatus
import org.junit.Assert.*
import org.junit.Test

class ExpiryOverlayMountTest {
    @Test fun immediateRecheckAfterSubmitDoesNotWithdrawAnUnattachedWindow() {
        var now = 0L
        val state = ExpiryOverlayMount { now }
        now = 16L
        assertEquals(ExpiryOverlayPhase.PENDING_ATTACH, state.phase)
        assertTrue(state.awaitingDisplay)
        assertNull(state.failureReason())
        now = 4_999L
        assertNull(state.failureReason())
        assertTrue(state.attached())
        assertEquals(ExpiryOverlayPhase.ATTACHED, state.phase)
        assertTrue(state.awaitingDisplay)
        assertNull(state.failureReason())
        assertTrue(state.drawn())
        assertEquals(ExpiryOverlayPhase.VISIBLE, state.phase)
        assertFalse(state.awaitingDisplay)
    }

    @Test fun twentySecondTimeoutStartsAtConfirmedDrawRatherThanSubmission() {
        var now = 0L
        val state = ExpiryOverlayMount { now }
        now = 4_000L
        state.attached()
        now = 8_000L
        state.drawn()
        now = 27_999L
        assertNull(state.failureReason())
        assertFalse(state.drawn())
        now = 28_000L
        assertEquals("window_timeout", state.failureReason())
    }

    @Test fun actualDetachProducesAStableFailureForNotificationFallback() {
        val state = ExpiryOverlayMount { 0L }
        state.attached()
        state.drawn()
        state.detached()
        assertEquals(ExpiryOverlayPhase.CLOSED, state.phase)
        assertEquals("unexpected_detach", state.failureReason())
        state.detached()
        assertEquals("unexpected_detach", state.failureReason())
        assertFalse(state.attached())
        assertFalse(state.drawn())
    }

    @Test fun attachmentAndFirstDrawHaveSeparateBoundedFailureDeadlines() {
        var now = 0L
        val unattached = ExpiryOverlayMount { now }
        now = 5_000L
        assertEquals("attach_timeout", unattached.failureReason())
        val undrawn = ExpiryOverlayMount { now }
        undrawn.attached()
        now = 10_000L
        assertEquals("first_draw_timeout", undrawn.failureReason())
    }

    @Test fun userOrLifecycleClosureIgnoresLateCallbacksAndCannotCloseTwice() {
        val state = ExpiryOverlayMount { 0L }
        assertTrue(state.close("user_finish"))
        state.detached()
        assertFalse(state.close("duplicate_remove"))
        assertFalse(state.attached())
        assertFalse(state.drawn())
        assertEquals("user_finish", state.failureReason())
    }

    @Test fun pendingMountDefersFallbackAndRealDetachNotifiesOnlyOnceAcrossRestore() {
        val state = ExpiryOverlayMount { 0L }
        val expired = IntentSession("s", "r", "app", "app", "App", 0, "Read", 0L,
            60_000L, 0L, status = SessionStatus.Ended, expiryDecision = ExpiryDecision.Pending)
        val storage = MemoryIntentSessionStorage(IntentSessionBook(sessions = listOf(expired)))
        val sessions = IntentSessionRepository(storage) { 0L }
        var delivered = 0
        fun fallback(repository: IntentSessionRepository) {
            if (state.failureReason() != null) repository.deliverExpiry("s") { delivered++; true }
        }
        fallback(sessions)
        assertEquals(0, delivered)
        state.attached()
        state.drawn()
        state.detached()
        fallback(sessions)
        fallback(sessions)
        fallback(IntentSessionRepository(storage) { 0L })
        assertEquals(1, delivered)
    }
}
