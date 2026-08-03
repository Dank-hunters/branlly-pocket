package com.branlly.pocket.domain

import com.branlly.pocket.domain.media.MediaDispatchFence
import com.branlly.pocket.domain.media.MediaExecutionCheckpoint
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionResult
import com.branlly.pocket.domain.media.MediaExecutionSession
import com.branlly.pocket.domain.media.MediaExecutionState
import com.branlly.pocket.domain.media.MediaOperation
import com.branlly.pocket.domain.media.MediaOperationStatus
import com.branlly.pocket.domain.media.MediaOperationType
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.MediaSelectionPolicy
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExecutionSessionTest {
    @Test
    fun `restore preserves exact business state and atomic terminal guard`() {
        val checkpoint =
            checkpoint(
                state = MediaExecutionState.AWAIT_OUTCOME,
                operation =
                    MediaOperation(
                        "provider",
                        MediaOperationType.PROVIDER_SEARCH,
                        true,
                        MediaOperationStatus.AWAITING_OUTCOME,
                        effectApplied = true,
                        executionCount = 1,
                    ),
            )
        val session = restore(checkpoint)

        assertEquals(checkpoint, session.checkpoint())
        assertTrue(
            session.terminate(
                MediaExecutionResult.Completed(
                    com.branlly.pocket.domain.media.MediaConfirmationLevel.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED,
                ),
            ),
        )
        assertFalse(session.terminate(MediaExecutionResult.Failed("late callback")))
    }

    @Test
    fun `applied operation is never selected again`() {
        val applied =
            MediaOperation(
                "provider",
                MediaOperationType.PROVIDER_SEARCH,
                true,
                MediaOperationStatus.COMPLETED,
                effectApplied = true,
                executionCount = 1,
            )
        val next = MediaOperation("manual", MediaOperationType.MANUAL_ASSISTANCE, false)
        val session = restore(checkpoint(state = MediaExecutionState.EXECUTE_OPERATION, operation = applied, extra = next))

        assertEquals("manual", session.nextOperation()?.id)
        assertFalse(session.startOperation("provider"))
    }

    @Test
    fun `reserved direct dispatch is never automatically sent twice after restore`() {
        val operation = MediaOperation("session", MediaOperationType.MEDIA_SESSION, true)
        val session = restore(checkpoint(state = MediaExecutionState.EXECUTE_OPERATION, operation = operation))

        assertTrue(session.startOperation("session"))
        assertTrue(session.reserveDispatch("session"))
        assertEquals(MediaDispatchFence.RESERVED, session.currentOperation()?.dispatchFence)
        assertFalse(session.reserveDispatch("session"))
        val restored = restore(session.checkpoint())

        assertTrue(restored.resumeReservedDispatch("session"))
        assertEquals(MediaDispatchFence.OBSERVING, restored.currentOperation()?.dispatchFence)
        assertFalse(restored.startOperation("session"))
        assertFalse(restored.reserveDispatch("session"))
        assertEquals(MediaOperationStatus.AWAITING_OUTCOME, restored.currentOperation()?.status)
    }

    @Test
    fun `dispatch reservation is global to the logical media attempt`() {
        val first = MediaOperation("first", MediaOperationType.MEDIA_SESSION, true)
        val second = MediaOperation("second", MediaOperationType.MEDIA_SESSION, true)
        val session = restore(checkpoint(state = MediaExecutionState.EXECUTE_OPERATION, operation = first, extra = second))

        assertTrue(session.startOperation("first"))
        assertTrue(session.reserveDispatch("first"))
        assertTrue(session.finishOperation("first", MediaOperationStatus.FAILED))
        assertTrue(session.startOperation("second"))
        assertFalse(session.reserveDispatch("second"))
    }

    @Test
    fun `retry generation creates a distinct deterministic effect key`() {
        val first =
            MediaExecutionSession(
                executionId = "execution",
                routineId = ShortcutId("routine"),
                nodeId = NodeId("node"),
                targetPackage = "target.player",
                searchQuery = "query",
                mediaUri = null,
                selectionPolicy = MediaSelectionPolicy.BEST_PLAYABLE_MATCH,
                baseline = MediaSessionBaseline(emptySet(), emptySet()),
                plan = MediaExecutionPlan(listOf(MediaOperation("session", MediaOperationType.MEDIA_SESSION, true))),
                attemptGeneration = 0,
                automaticDeadlineMillis = 100,
                globalDeadlineMillis = 1_000,
            )
        val retry =
            MediaExecutionSession(
                executionId = "execution",
                routineId = ShortcutId("routine"),
                nodeId = NodeId("node"),
                targetPackage = "target.player",
                searchQuery = "query",
                mediaUri = null,
                selectionPolicy = MediaSelectionPolicy.BEST_PLAYABLE_MATCH,
                baseline = MediaSessionBaseline(emptySet(), emptySet()),
                plan = MediaExecutionPlan(listOf(MediaOperation("session", MediaOperationType.MEDIA_SESSION, true))),
                attemptGeneration = 1,
                automaticDeadlineMillis = 100,
                globalDeadlineMillis = 1_000,
            )
        assertTrue(first.startOperation("session"))
        assertTrue(retry.startOperation("session"))
        assertTrue(first.reserveDispatch("session"))
        assertTrue(retry.reserveDispatch("session"))
        assertFalse(first.currentOperation()?.effectKey == retry.currentOperation()?.effectKey)
    }

    @Test
    fun `continuation creation and consumption are exactly once`() {
        val operation =
            MediaOperation("provider", MediaOperationType.PROVIDER_SEARCH, true, MediaOperationStatus.RUNNING, executionCount = 1)
        val session = restore(checkpoint(state = MediaExecutionState.EXECUTE_OPERATION, operation = operation))

        val suspended = session.suspendForUser("provider")
        assertEquals("execution:node:provider:4", suspended?.continuationKey)
        assertNull(session.suspendForUser("provider"))
        assertTrue(session.consumeContinuation())
        assertFalse(session.consumeContinuation())
    }

    private fun restore(checkpoint: MediaExecutionCheckpoint) =
        MediaExecutionSession.restore(
            checkpoint,
            targetPackage = "target.player",
            searchQuery = "query",
            mediaUri = null,
            selectionPolicy = MediaSelectionPolicy.BEST_PLAYABLE_MATCH,
        )

    private fun checkpoint(
        state: MediaExecutionState,
        operation: MediaOperation,
        extra: MediaOperation? = null,
    ) = MediaExecutionCheckpoint(
        executionId = "execution",
        routineId = ShortcutId("routine"),
        nodeId = NodeId("node"),
        startedAtMillis = 10,
        automaticDeadlineMillis = 100,
        globalDeadlineMillis = 1_000,
        state = state,
        stateVersion = 3,
        operationId =
            operation
                .takeIf {
                    it.status in
                        setOf(MediaOperationStatus.RUNNING, MediaOperationStatus.EFFECT_APPLIED, MediaOperationStatus.AWAITING_OUTCOME)
                }?.id,
        continuationConsumed = false,
        manualGuidanceShown = false,
        baseline = MediaSessionBaseline(emptySet(), emptySet()),
        plan = MediaExecutionPlan(listOfNotNull(operation, extra)),
    )
}
