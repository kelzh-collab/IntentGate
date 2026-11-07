package com.intentgate.app

import com.intentgate.app.session.CountdownState
import com.intentgate.app.session.SessionStatus
import org.junit.Assert.*
import org.junit.Test

class CountdownStateTest {
    @Test fun pausedTimeNeverCountsAndResumeHasNoJump() {
        val running = CountdownState(remainingMillis = 10_000).resume(1_000).tick(3_250)
        assertEquals(7_750L, running.remainingMillis)
        val paused = running.pause(4_000)
        assertEquals(7_000L, paused.remainingMillis)
        assertEquals(paused, paused.tick(50_000))
        assertEquals(6_000L, paused.resume(50_000).tick(51_000).remainingMillis)
    }
    @Test fun expiryIsClampedAndCannotResume() {
        val ended = CountdownState(remainingMillis = 1_000).resume(100).tick(5_000)
        assertEquals(SessionStatus.Ended, ended.status)
        assertEquals(0L, ended.remainingSeconds)
        assertEquals(0f, ended.progress)
        assertEquals(ended, ended.resume(6_000))
    }
    @Test fun repeatedResumeAndBackwardsClockCannotDiscardTime() {
        val running = CountdownState(remainingMillis = 10_000).resume(1_000)
        assertEquals(running, running.resume(2_000))
        assertEquals(running, running.tick(500))
        assertEquals(8_000L, running.tick(3_000).remainingMillis)
    }
    @Test fun finishAndFractionalSecondDisplay() {
        assertEquals(2L, CountdownState(remainingMillis = 1_001).remainingSeconds)
        assertEquals(SessionStatus.Ended, CountdownState().end().status)
        assertEquals(0L, CountdownState().end().remainingMillis)
    }
}
