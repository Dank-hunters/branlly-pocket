package com.branlly.pocket.domain.media

/** Event source owned by one media session. Implementations never decide the node progression. */
interface MediaOutcomeObserver : AutoCloseable {
    val baseline: MediaSessionBaseline

    suspend fun awaitOutcome(timeoutMillis: Long): MediaObservedOutcome

    /** Captures the target state immediately before an operation is sent. */
    fun capturePreDispatchState() = Unit

    /** Records an operation actually dispatched; null means no session-specific correlation is available. */
    fun onOperationDispatched(commandedSessionId: String?) = Unit

    override fun close()
}

sealed interface MediaObservedOutcome {
    data class PlaybackStarted(
        val sessionId: String,
        val contentConfirmed: Boolean,
        val preexisting: Boolean,
        val proof: String = "new_session_playing",
    ) : MediaObservedOutcome

    data object TimedOut : MediaObservedOutcome

    data class Unavailable(
        val reason: String,
    ) : MediaObservedOutcome
}
