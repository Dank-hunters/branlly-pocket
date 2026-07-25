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

enum class MediaOperationType { DIRECT_URI, MEDIA_SESSION, PROVIDER_SEARCH, PROVIDER_AUTOMATION, MANUAL_ASSISTANCE }

enum class MediaBaselinePlaybackState { NONE, STOPPED, PAUSED, PLAYING, UNKNOWN }

enum class MediaBaselineMetadataState { ABSENT, PARTIAL, COMPLETE }

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
)

data class MediaOperation(
    val id: String,
    val type: MediaOperationType,
    val automatic: Boolean,
    val status: MediaOperationStatus = MediaOperationStatus.NOT_STARTED,
    val available: Boolean = true,
    val effectApplied: Boolean = false,
    val executionCount: Int = 0,
    val reason: String? = null,
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
    val operationId: String?,
    val continuationCreated: Boolean = false,
    val continuationConsumed: Boolean,
    val continuationKey: String? = null,
    val manualGuidanceShown: Boolean,
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
    initialContinuationCreated: Boolean = false,
    initialContinuationConsumed: Boolean = false,
    initialContinuationKey: String? = null,
    initialManualGuidanceShown: Boolean = false,
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
                            )
                        } else {
                            it
                        }
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
            operationId = current.operations.firstOrNull { it.status in ACTIVE_OPERATION_STATUSES }?.id,
            continuationCreated = current.continuationCreated,
            continuationConsumed = current.continuationConsumed,
            continuationKey = current.continuationKey,
            manualGuidanceShown = current.manualGuidanceShown,
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
                initialContinuationCreated = checkpoint.continuationCreated,
                initialContinuationConsumed = checkpoint.continuationConsumed,
                initialContinuationKey = checkpoint.continuationKey,
                initialManualGuidanceShown = checkpoint.manualGuidanceShown,
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
