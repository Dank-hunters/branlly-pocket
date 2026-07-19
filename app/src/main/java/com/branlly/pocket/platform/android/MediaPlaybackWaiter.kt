package com.branlly.pocket.platform.android

/** Phase 1 seam: Android MediaSession monitoring is attached in phase 2. */
interface MediaPlaybackWaiter {
    suspend fun waitForPlayback(
        packageName: String,
        timeoutMs: Long,
    ): MediaWaitResult
}

sealed interface MediaWaitResult {
    data object Playing : MediaWaitResult

    data object TimedOut : MediaWaitResult

    data class Failed(
        val reason: String,
    ) : MediaWaitResult
}
