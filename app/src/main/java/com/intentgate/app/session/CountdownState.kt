package com.intentgate.app.session

enum class SessionStatus { Running, Paused, Ended }

/** Monotonic-clock reducer: paused/background intervals never count. */
data class CountdownState(
    val durationMillis: Long = 30 * 60_000L,
    val remainingMillis: Long = 24 * 60_000L + 36_000L,
    val status: SessionStatus = SessionStatus.Paused,
    val lastTickMillis: Long? = null,
) {
    val remainingSeconds: Long get() = (remainingMillis + 999) / 1000
    val progress: Float get() = (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    fun tick(now: Long): CountdownState {
        val previous = lastTickMillis ?: return this
        if (status != SessionStatus.Running || now < previous) return this
        val left = (remainingMillis - (now - previous)).coerceAtLeast(0)
        return copy(remainingMillis = left, status = if (left == 0L) SessionStatus.Ended else status,
            lastTickMillis = if (left == 0L) null else now)
    }
    fun resume(now: Long): CountdownState =
        if (status == SessionStatus.Ended || status == SessionStatus.Running) this
        else copy(status = SessionStatus.Running, lastTickMillis = now)
    fun pause(now: Long): CountdownState = tick(now).let {
        if (it.status == SessionStatus.Ended) it else it.copy(status = SessionStatus.Paused, lastTickMillis = null)
    }
    fun end(): CountdownState = copy(remainingMillis = 0, status = SessionStatus.Ended, lastTickMillis = null)
}
