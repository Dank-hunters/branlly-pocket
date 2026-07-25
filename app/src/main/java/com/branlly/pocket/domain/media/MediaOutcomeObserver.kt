package com.branlly.pocket.domain.media

/** Event source owned by one media session. Implementations never decide the node progression. */
interface MediaOutcomeObserver : AutoCloseable {
    val baseline: MediaSessionBaseline

    suspend fun awaitOutcome(timeoutMillis: Long): MediaObservedOutcome

    override fun close()
}

sealed interface MediaObservedOutcome {
    data class PlaybackStarted(
        val sessionId: String,
        val contentConfirmed: Boolean,
        val preexisting: Boolean,
    ) : MediaObservedOutcome

    data object TimedOut : MediaObservedOutcome

    data class Unavailable(
        val reason: String,
    ) : MediaObservedOutcome
}
