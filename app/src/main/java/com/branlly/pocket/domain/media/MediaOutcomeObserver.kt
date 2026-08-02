package com.branlly.pocket.domain.media

/** Event source owned by one media session. Implementations never decide the node progression. */
interface MediaOutcomeObserver : AutoCloseable {
    val baseline: MediaSessionBaseline

    suspend fun awaitOutcome(timeoutMillis: Long): MediaObservedOutcome

    /** Captures the target state immediately before an operation is sent. */
    fun capturePreDispatchState() = Unit

    /** Records an operation actually dispatched; null means no session-specific correlation is available. */
    fun onOperationDispatched(
        commandedSessionId: String?,
        commandedController: ObservableMediaController? = null,
    ) = Unit

    override fun close()
}

/**
 * Android-free handle for the exact controller selected by a media command.
 * It remains valid for the lifetime of one observation only and must not be persisted.
 */
interface ObservableMediaController {
    val sessionId: String
    val packageName: String

    fun snapshot(): MediaObservedSession?

    /** Registers a change listener and returns an idempotent subscription cleanup. */
    fun subscribe(listener: () -> Unit): AutoCloseable
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
