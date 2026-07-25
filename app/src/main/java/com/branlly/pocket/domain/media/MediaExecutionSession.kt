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

enum class MediaOperationStatus { NOT_STARTED, RUNNING, COMPLETED, FAILED, SKIPPED }

enum class MediaOperationType { DIRECT_URI, MEDIA_SESSION, PROVIDER_SEARCH, PROVIDER_AUTOMATION, MANUAL_ASSISTANCE }

data class MediaSessionBaseline(
    val playingSessionIds: Set<String>,
    val knownSessionIds: Set<String>,
)

data class MediaOperation(
    val id: String,
    val type: MediaOperationType,
    val automatic: Boolean,
    val status: MediaOperationStatus = MediaOperationStatus.NOT_STARTED,
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
    val continuationConsumed: Boolean,
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
    initialContinuationConsumed: Boolean = false,
    initialManualGuidanceShown: Boolean = false,
    val automaticDeadlineMillis: Long,
    val globalDeadlineMillis: Long,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    private data class MutableState(
        val state: MediaExecutionState,
        val version: Int,
        val operations: List<MediaOperation>,
        val continuationConsumed: Boolean,
        val manualGuidanceShown: Boolean,
        val terminal: MediaExecutionResult? = null,
    )

    private val state =
        AtomicReference(
            MutableState(initialState, initialStateVersion, plan.operations, initialContinuationConsumed, initialManualGuidanceShown),
        )

    fun state(): MediaExecutionState = state.get().state

    fun currentOperation(): MediaOperation? = state.get().operations.firstOrNull { it.status == MediaOperationStatus.RUNNING }

    fun nextOperation(): MediaOperation? = state.get().operations.firstOrNull { it.status == MediaOperationStatus.NOT_STARTED }

    fun terminalResult(): MediaExecutionResult? = state.get().terminal

    fun move(next: MediaExecutionState): Boolean =
        update { current ->
            if (current.terminal != null) null else current.copy(state = next, version = current.version + 1)
        }

    fun startOperation(id: String): Boolean =
        update { current ->
            if (current.terminal != null) return@update null
            val operation =
                current.operations.firstOrNull { it.id == id && it.status == MediaOperationStatus.NOT_STARTED } ?: return@update null
            current.copy(
                state = MediaExecutionState.EXECUTE_OPERATION,
                version = current.version + 1,
                operations = current.operations.map { if (it.id == operation.id) it.copy(status = MediaOperationStatus.RUNNING) else it },
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
                        if (it.id == id &&
                            it.status == MediaOperationStatus.RUNNING
                        ) {
                            it.copy(status = status)
                        } else {
                            it
                        }
                    },
            )
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
            if (current.terminal != null || current.continuationConsumed) {
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
            operationId = current.operations.firstOrNull { it.status == MediaOperationStatus.RUNNING }?.id,
            continuationConsumed = current.continuationConsumed,
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
}
