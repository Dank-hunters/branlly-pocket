package com.branlly.pocket.domain.media

import com.branlly.pocket.domain.model.MediaSelectionPolicy
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import java.util.concurrent.atomic.AtomicReference

enum class MediaExecutionState {
    PRECHECK,
    CAPTURE_BASELINE,
    START_OBSERVATION,
    BUILD_PLAN,
    EXECUTE_OPERATION,
    AWAIT_OUTCOME,
    AWAIT_USER_LAUNCH,
    AWAIT_MANUAL_PLAY,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
}

enum class MediaConfirmationLevel {
    PLAYBACK_AND_CONTENT_CONFIRMED,
    PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED,
    PREEXISTING_PLAYBACK,
}

enum class MediaOperationStatus {
    NOT_STARTED,
    RUNNING,
    EFFECT_APPLIED,
    AWAITING_OUTCOME,
    COMPLETED,
    FAILED,
    SKIPPED,
}

/** Durable ownership of the one non-transactional direct command in a logical attempt. */
enum class MediaDispatchFence { OPEN, RESERVED, DISPATCHED, OBSERVING, CONFIRMED, TERMINAL_UNCONFIRMED }

enum class MediaOperationType { DIRECT_URI, MEDIA_SESSION, PROVIDER_SEARCH, PROVIDER_AUTOMATION, MANUAL_ASSISTANCE }

enum class MediaBaselinePlaybackState { NONE, STOPPED, PAUSED, PLAYING, UNKNOWN }

enum class MediaBaselineMetadataState { ABSENT, PARTIAL, COMPLETE }

data class MediaContentFingerprint(
    val mediaId: String? = null,
    val activeQueueItemId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMillis: Long? = null,
    val mediaUri: String? = null,
) {
    fun isComparable(): Boolean =
        !mediaId.isNullOrBlank() || activeQueueItemId != null ||
            listOf(title, artist, album, durationMillis?.toString(), mediaUri).any { !it.isNullOrBlank() }

    fun differsFrom(previous: MediaContentFingerprint): Boolean {
        fun changed(
            current: String?,
            baseline: String?,
        ): Boolean = !current.isNullOrBlank() && !baseline.isNullOrBlank() && current != baseline
        val technicalChanged =
            (!mediaId.isNullOrBlank() && !previous.mediaId.isNullOrBlank() && mediaId != previous.mediaId) ||
                (activeQueueItemId != null && previous.activeQueueItemId != null && activeQueueItemId != previous.activeQueueItemId)
        return technicalChanged ||
            changed(title, previous.title) ||
            changed(artist, previous.artist) ||
            changed(album, previous.album) ||
            changed(mediaUri, previous.mediaUri) ||
            (durationMillis != null && previous.durationMillis != null && durationMillis != previous.durationMillis)
    }
}

data class MediaBaselineSession(
    val sessionId: String,
    val playbackState: MediaBaselinePlaybackState,
    val content: MediaContentFingerprint = MediaContentFingerprint(),
)

data class MediaSessionBaseline(
    val playingSessionIds: Set<String>,
    val knownSessionIds: Set<String>,
    val sessionPresent: Boolean = knownSessionIds.isNotEmpty(),
    val packageName: String? = null,
    val playbackState: MediaBaselinePlaybackState = if (playingSessionIds.isNotEmpty()) MediaBaselinePlaybackState.PLAYING else MediaBaselinePlaybackState.NONE,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val mediaUri: String? = null,
    val sessionId: String? = null,
    val positionMillis: Long? = null,
    val capturedAtMillis: Long = 0,
    val metadataState: MediaBaselineMetadataState = MediaBaselineMetadataState.ABSENT,
    val sessions: List<MediaBaselineSession> = emptyList(),
)

data class MediaObservedSession(
    val sessionId: String,
    val packageName: String,
    val playbackState: MediaBaselinePlaybackState,
    val content: MediaContentFingerprint = MediaContentFingerprint(),
)

/** Pure confirmation policy shared by Android callbacks and unit tests. */
fun MediaSessionBaseline.confirmDirectPlayback(
    observed: MediaObservedSession,
    targetPackage: String,
    commandedSessionId: String?,
    commandDispatched: Boolean,
    commandedSessionStillPresent: Boolean,
    allowCommandedSessionPackageMismatch: Boolean = false,
): String? {
    if (!commandDispatched ||
        (
            observed.packageName != targetPackage &&
                !(allowCommandedSessionPackageMismatch && observed.sessionId == commandedSessionId)
        ) ||
        observed.playbackState != MediaBaselinePlaybackState.PLAYING
    ) {
        return null
    }
    val previous = sessions.firstOrNull { it.sessionId == observed.sessionId }
    if (commandedSessionId == null) {
        return when {
            previous == null -> "new_session_playing"
            previous.playbackState != MediaBaselinePlaybackState.PLAYING -> "existing_session_started"
            observed.content.differsFrom(previous.content) -> "content_changed"
            else -> null
        }
    }
    if (observed.sessionId == commandedSessionId) {
        return when {
            previous == null -> "commanded_session_new"
            previous.playbackState != MediaBaselinePlaybackState.PLAYING -> "commanded_session_started"
            observed.content.differsFrom(previous.content) -> "commanded_session_content_changed"
            else -> null
        }
    }
    return if (!commandedSessionStillPresent && previous == null) "replacement_session_playing" else null
}

data class MediaOperation(
    val id: String,
    val type: MediaOperationType,
    val automatic: Boolean,
    val status: MediaOperationStatus = MediaOperationStatus.NOT_STARTED,
    val available: Boolean = true,
    val effectApplied: Boolean = false,
    val executionCount: Int = 0,
    val reason: String? = null,
    val commandedSessionId: String? = null,
    /** Persisted before invoking a non-transactional external media command. */
    val dispatchReserved: Boolean = false,
    /** Checkpoint-safe phase of the attempt-wide command fence. */
    val dispatchFence: MediaDispatchFence = if (dispatchReserved) MediaDispatchFence.RESERVED else MediaDispatchFence.OPEN,
    /** Opaque deterministic key for the logical request, never derived from a controller. */
    val effectKey: String? = null,
)

data class MediaExecutionPlan(
    val operations: List<MediaOperation>,
)

data class MediaExecutionCheckpoint(
    val executionId: String,
    val routineId: ShortcutId,
    val nodeId: NodeId,
    val startedAtMillis: Long,
    val automaticDeadlineMillis: Long,
    val globalDeadlineMillis: Long,
    val state: MediaExecutionState,
    val stateVersion: Int,
    /** Explicit retry generation; process/service resume keeps this unchanged. */
    val attemptGeneration: Int = 0,
    val operationId: String?,
    val continuationCreated: Boolean = false,
    val continuationConsumed: Boolean,
    val continuationKey: String? = null,
    val manualGuidanceShown: Boolean,
    val directFailureNoticeShown: Boolean = false,
    val baseline: MediaSessionBaseline,
    val plan: MediaExecutionPlan,
)

sealed interface MediaExecutionResult {
    data class Completed(
        val confirmation: MediaConfirmationLevel,
    ) : MediaExecutionResult

    data class Failed(
        val reason: String,
        val recoverable: Boolean = false,
    ) : MediaExecutionResult

    data class Cancelled(
        val reason: String,
    ) : MediaExecutionResult

    data class TimedOut(
        val reason: String,
    ) : MediaExecutionResult

    data class UserLaunchRequired(
        val reason: String,
        val checkpoint: MediaExecutionCheckpoint,
    ) : MediaExecutionResult
}

/**
 * The one mutable authority for one PLAY_MEDIA node. Atomic transitions make terminal completion,
 * continuation creation and cleanup exactly-once decisions.
 */
class MediaExecutionSession(
    val executionId: String,
    val routineId: ShortcutId,
    val nodeId: NodeId,
    val targetPackage: String,
    val searchQuery: String,
    val mediaUri: String?,
    val selectionPolicy: MediaSelectionPolicy,
    val baseline: MediaSessionBaseline,
    val plan: MediaExecutionPlan,
    initialState: MediaExecutionState = MediaExecutionState.PRECHECK,
    initialStateVersion: Int = 0,
    val attemptGeneration: Int = 0,
    initialContinuationCreated: Boolean = false,
    initialContinuationConsumed: Boolean = false,
    initialContinuationKey: String? = null,
    initialManualGuidanceShown: Boolean = false,
    initialDirectFailureNoticeShown: Boolean = false,
    val automaticDeadlineMillis: Long,
    val globalDeadlineMillis: Long,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    private data class MutableState(
        val state: MediaExecutionState,
        val version: Int,
        val operations: List<MediaOperation>,
        val continuationCreated: Boolean,
        val continuationConsumed: Boolean,
        val continuationKey: String?,
        val manualGuidanceShown: Boolean,
        val directFailureNoticeShown: Boolean,
        val terminal: MediaExecutionResult? = null,
    )

    private val state =
        AtomicReference(
            MutableState(
                initialState,
                initialStateVersion,
                plan.operations,
                initialContinuationCreated,
                initialContinuationConsumed,
                initialContinuationKey,
                initialManualGuidanceShown,
                initialDirectFailureNoticeShown,
            ),
        )

    fun state(): MediaExecutionState = state.get().state

    fun currentOperation(): MediaOperation? = state.get().operations.firstOrNull { it.status in ACTIVE_OPERATION_STATUSES }

    fun nextOperation(): MediaOperation? =
        state.get().operations.firstOrNull {
            it.available && it.status == MediaOperationStatus.NOT_STARTED && it.executionCount == 0
        }

    fun terminalResult(): MediaExecutionResult? = state.get().terminal

    fun move(next: MediaExecutionState): Boolean =
        update { current ->
            if (current.terminal != null) null else current.copy(state = next, version = current.version + 1)
        }

    fun startOperation(id: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            val operation =
                current.operations.firstOrNull {
                    it.id == id && it.available && it.status == MediaOperationStatus.NOT_STARTED && it.executionCount == 0
                } ?: return@update null
            current.copy(
                state = MediaExecutionState.EXECUTE_OPERATION,
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id ==
                            operation.id
                        ) {
                            it.copy(status = MediaOperationStatus.RUNNING, executionCount = it.executionCount + 1)
                        } else {
                            it
                        }
                    },
            )
        }

    fun finishOperation(
        id: String,
        status: MediaOperationStatus,
    ): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            current.copy(
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == id && it.status in ACTIVE_OPERATION_STATUSES) {
                            it.copy(
                                status = status,
                                effectApplied = it.effectApplied || status in EFFECT_APPLIED_STATUSES,
                                dispatchFence =
                                    when {
                                        status == MediaOperationStatus.AWAITING_OUTCOME && it.dispatchFence == MediaDispatchFence.DISPATCHED -> MediaDispatchFence.OBSERVING

                                        // COMPLETED here means the operation window ended without
                                        // a proof; only an observed terminal result may confirm.
                                        status == MediaOperationStatus.COMPLETED && it.dispatchFence != MediaDispatchFence.OPEN -> MediaDispatchFence.TERMINAL_UNCONFIRMED

                                        status in setOf(MediaOperationStatus.FAILED, MediaOperationStatus.SKIPPED) &&
                                            it.dispatchFence != MediaDispatchFence.OPEN -> MediaDispatchFence.TERMINAL_UNCONFIRMED

                                        else -> it.dispatchFence
                                    },
                            )
                        } else {
                            it
                        }
                    },
            )
        }

    /**
     * Creates the durable at-most-once barrier immediately before the Android call.
     * A restored reservation is deliberately never retried automatically.
     */
    fun reserveDispatch(operationId: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            val operation = current.operations.singleOrNull { it.id == operationId } ?: return@update null
            // A reservation belongs to the whole logical PLAY_MEDIA attempt, not to a
            // controller. Once persisted, another discovered session must never authorize
            // another automatic search command in this checkpoint.
            if (
                operation.status != MediaOperationStatus.RUNNING ||
                operation.dispatchReserved ||
                current.operations.any { it.id != operationId && it.dispatchReserved }
            ) {
                return@update null
            }
            current.copy(
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == operationId) {
                            it.copy(
                                dispatchReserved = true,
                                dispatchFence = MediaDispatchFence.RESERVED,
                                effectKey = effectKey(operationId),
                            )
                        } else {
                            it
                        }
                    },
            )
        }

    /** Turns an ambiguous restored reservation into observation without a second send. */
    fun resumeReservedDispatch(operationId: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            val operation = current.operations.singleOrNull { it.id == operationId } ?: return@update null
            if (operation.status != MediaOperationStatus.RUNNING || !operation.dispatchReserved) return@update null
            current.copy(
                state = MediaExecutionState.AWAIT_OUTCOME,
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == operationId) {
                            it.copy(
                                status = MediaOperationStatus.AWAITING_OUTCOME,
                                effectApplied = true,
                                dispatchFence = MediaDispatchFence.OBSERVING,
                            )
                        } else {
                            it
                        }
                    },
            )
        }

    fun markDispatchPerformed(operationId: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            current.copy(
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == operationId &&
                            it.dispatchFence == MediaDispatchFence.RESERVED
                        ) {
                            it.copy(dispatchFence = MediaDispatchFence.DISPATCHED)
                        } else {
                            it
                        }
                    },
            )
        }

    fun markObservingDispatch(operationId: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            current.copy(
                state = MediaExecutionState.AWAIT_OUTCOME,
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == operationId &&
                            it.dispatchFence == MediaDispatchFence.DISPATCHED
                        ) {
                            it.copy(dispatchFence = MediaDispatchFence.OBSERVING)
                        } else {
                            it
                        }
                    },
            )
        }

    fun confirmCurrentDispatch(): Boolean =
        update { current ->
            val active =
                current.operations.singleOrNull {
                    it.dispatchFence in setOf(MediaDispatchFence.DISPATCHED, MediaDispatchFence.OBSERVING)
                } ?: return@update null
            if (active.dispatchFence !in setOf(MediaDispatchFence.DISPATCHED, MediaDispatchFence.OBSERVING)) return@update null
            current.copy(
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == active.id) it.copy(dispatchFence = MediaDispatchFence.CONFIRMED) else it
                    },
            )
        }

    fun recordCommandedSession(
        operationId: String,
        sessionId: String?,
    ): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            current.copy(
                version = current.version + 1,
                operations =
                    current.operations.map {
                        if (it.id == operationId && it.status in ACTIVE_OPERATION_STATUSES) it.copy(commandedSessionId = sessionId) else it
                    },
            )
        }

    fun suspendForUser(operationId: String): MediaExecutionCheckpoint? {
        val changed =
            update { current ->
                val operation = current.operations.singleOrNull { it.id == operationId } ?: return@update null
                if (current.terminal != null || current.continuationCreated || operation.status != MediaOperationStatus.RUNNING ||
                    operation.effectApplied
                ) {
                    return@update null
                }
                val nextVersion = current.version + 1
                current.copy(
                    state = MediaExecutionState.AWAIT_USER_LAUNCH,
                    version = nextVersion,
                    continuationCreated = true,
                    continuationKey = "$executionId:${nodeId.value}:$operationId:$nextVersion",
                )
            }
        return if (changed) checkpoint() else null
    }

    fun markManualGuidanceShown(): Boolean =
        update { current ->
            if (current.terminal != null || current.manualGuidanceShown) {
                null
            } else {
                current.copy(state = MediaExecutionState.AWAIT_MANUAL_PLAY, version = current.version + 1, manualGuidanceShown = true)
            }
        }

    fun markDirectFailureNoticeShown(): Boolean =
        update { current ->
            if (current.terminal != null || current.directFailureNoticeShown) {
                null
            } else {
                current.copy(version = current.version + 1, directFailureNoticeShown = true)
            }
        }

    fun consumeContinuation(): Boolean =
        update { current ->
            if (current.terminal != null || current.state != MediaExecutionState.AWAIT_USER_LAUNCH ||
                !current.continuationCreated || current.continuationConsumed
            ) {
                null
            } else {
                current.copy(state = MediaExecutionState.EXECUTE_OPERATION, version = current.version + 1, continuationConsumed = true)
            }
        }

    fun checkpoint(): MediaExecutionCheckpoint {
        val current = state.get()
        return MediaExecutionCheckpoint(
            executionId = executionId,
            routineId = routineId,
            nodeId = nodeId,
            startedAtMillis = startedAtMillis,
            automaticDeadlineMillis = automaticDeadlineMillis,
            globalDeadlineMillis = globalDeadlineMillis,
            state = current.state,
            stateVersion = current.version,
            attemptGeneration = attemptGeneration,
            operationId = current.operations.firstOrNull { it.status in ACTIVE_OPERATION_STATUSES }?.id,
            continuationCreated = current.continuationCreated,
            continuationConsumed = current.continuationConsumed,
            continuationKey = current.continuationKey,
            manualGuidanceShown = current.manualGuidanceShown,
            directFailureNoticeShown = current.directFailureNoticeShown,
            baseline = baseline,
            plan = MediaExecutionPlan(current.operations),
        )
    }

    fun terminate(result: MediaExecutionResult): Boolean =
        update { current ->
            if (current.terminal != null) {
                null
            } else {
                current.copy(state = terminalState(result), version = current.version + 1, terminal = result)
            }
        }

    private fun update(transform: (MutableState) -> MutableState?): Boolean {
        while (true) {
            val current = state.get()
            val next = transform(current) ?: return false
            if (state.compareAndSet(current, next)) return true
        }
    }

    private fun effectKey(operationId: String): String {
        val commandType = if (mediaUri.isNullOrBlank()) "play_from_search" else "play_from_uri"
        val request = mediaUri?.takeIf(String::isNotBlank) ?: searchQuery
        return "$executionId:${nodeId.value}:$attemptGeneration:$operationId:$targetPackage:$commandType:${queryFingerprint(request)}"
    }

    private fun queryFingerprint(query: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(query.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private fun terminalState(result: MediaExecutionResult): MediaExecutionState =
        when (result) {
            is MediaExecutionResult.Completed -> MediaExecutionState.COMPLETED
            is MediaExecutionResult.Failed -> MediaExecutionState.FAILED
            is MediaExecutionResult.Cancelled -> MediaExecutionState.CANCELLED
            is MediaExecutionResult.TimedOut -> MediaExecutionState.TIMED_OUT
            is MediaExecutionResult.UserLaunchRequired -> MediaExecutionState.AWAIT_USER_LAUNCH
        }

    companion object {
        fun restore(
            checkpoint: MediaExecutionCheckpoint,
            targetPackage: String,
            searchQuery: String,
            mediaUri: String?,
            selectionPolicy: MediaSelectionPolicy,
        ): MediaExecutionSession =
            MediaExecutionSession(
                executionId = checkpoint.executionId,
                routineId = checkpoint.routineId,
                nodeId = checkpoint.nodeId,
                targetPackage = targetPackage,
                searchQuery = searchQuery,
                mediaUri = mediaUri,
                selectionPolicy = selectionPolicy,
                baseline = checkpoint.baseline,
                plan = checkpoint.plan,
                initialState = checkpoint.state,
                initialStateVersion = checkpoint.stateVersion,
                attemptGeneration = checkpoint.attemptGeneration,
                initialContinuationCreated = checkpoint.continuationCreated,
                initialContinuationConsumed = checkpoint.continuationConsumed,
                initialContinuationKey = checkpoint.continuationKey,
                initialManualGuidanceShown = checkpoint.manualGuidanceShown,
                initialDirectFailureNoticeShown = checkpoint.directFailureNoticeShown,
                automaticDeadlineMillis = checkpoint.automaticDeadlineMillis,
                globalDeadlineMillis = checkpoint.globalDeadlineMillis,
                startedAtMillis = checkpoint.startedAtMillis,
            )

        private val EFFECT_APPLIED_STATUSES =
            setOf(
                MediaOperationStatus.EFFECT_APPLIED,
                MediaOperationStatus.AWAITING_OUTCOME,
                MediaOperationStatus.COMPLETED,
            )
        private val ACTIVE_OPERATION_STATUSES =
            setOf(
                MediaOperationStatus.RUNNING,
                MediaOperationStatus.EFFECT_APPLIED,
                MediaOperationStatus.AWAITING_OUTCOME,
            )
    }
}
