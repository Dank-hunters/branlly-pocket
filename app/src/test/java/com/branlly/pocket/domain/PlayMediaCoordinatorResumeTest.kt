package com.branlly.pocket.domain

import android.content.Intent
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.media.MediaExecutionCheckpoint
import com.branlly.pocket.domain.media.MediaExecutionCheckpointCodec
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionState
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaOperation
import com.branlly.pocket.domain.media.MediaOperationStatus
import com.branlly.pocket.domain.media.MediaOperationType
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.media.MediaProviderCapability
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint
import com.branlly.pocket.platform.android.actions.AppTarget
import com.branlly.pocket.platform.android.actions.ExternalActivityGateway
import com.branlly.pocket.platform.android.actions.ManualMediaGuidance
import com.branlly.pocket.platform.android.actions.MediaOpenRequest
import com.branlly.pocket.platform.android.actions.MediaProviderAdapter
import com.branlly.pocket.platform.android.actions.MediaSessionCommandGateway
import com.branlly.pocket.platform.android.actions.MediaSessionCommandResult
import com.branlly.pocket.platform.android.actions.PlayMediaCoordinator
import com.branlly.pocket.platform.android.actions.ProviderVerificationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayMediaCoordinatorResumeTest {
    @Test
    fun `playing during suspension completes without replaying pending operation`() =
        runBlocking {
            val fixture = Fixture(MediaObservedOutcome.PlaybackStarted("new", false, false))
            val result = fixture.coordinator().execute(action(), context(), workflow(checkpoint(MediaExecutionState.AWAIT_USER_LAUNCH)))

            assertEquals(ActionResult.Completed, result)
            assertEquals(0, fixture.commandCalls)
            assertEquals(0, fixture.launchCalls)
            assertEquals(fixture.baseline, fixture.restoredBaseline)
        }

    @Test
    fun `pending operation executes once after continuation claim`() =
        runBlocking {
            val fixture = Fixture()
            fixture.completeOnCommand = true
            val result = fixture.coordinator().execute(action(), context(), workflow(checkpoint(MediaExecutionState.AWAIT_USER_LAUNCH)))

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.commandCalls)
            assertEquals(1, fixture.closeCalls)
        }

    @Test
    fun `manual wait resumes without reopening provider`() =
        runBlocking {
            val fixture = Fixture(MediaObservedOutcome.PlaybackStarted("new", false, false))
            val manual =
                checkpoint(
                    state = MediaExecutionState.AWAIT_MANUAL_PLAY,
                    operation =
                        MediaOperation(
                            "manual_assistance",
                            MediaOperationType.MANUAL_ASSISTANCE,
                            false,
                            MediaOperationStatus.AWAITING_OUTCOME,
                            effectApplied = true,
                            executionCount = 1,
                        ),
                    continuationCreated = false,
                    continuationConsumed = false,
                    manualShown = true,
                )
            val result = fixture.coordinator().execute(action(), context(), workflow(manual))

            assertEquals(ActionResult.Completed, result)
            assertEquals(0, fixture.launchCalls)
            assertEquals(0, fixture.guidanceShows)
        }

    private class Fixture(
        initialOutcome: MediaObservedOutcome? = null,
    ) {
        val baseline = MediaSessionBaseline(emptySet(), emptySet(), capturedAtMillis = 10)
        private val outcome = CompletableDeferred<MediaObservedOutcome>().apply { initialOutcome?.let(::complete) }
        var restoredBaseline: MediaSessionBaseline? = null
        var commandCalls = 0
        var launchCalls = 0
        var closeCalls = 0
        var guidanceShows = 0
        var completeOnCommand = false

        fun coordinator() =
            PlayMediaCoordinator(
                launcher =
                    object : ExternalActivityGateway {
                        override fun canResolve(intent: Intent) = true

                        override suspend fun launch(
                            intent: Intent,
                            label: String,
                            executionContext: ActionExecutionContext,
                        ): ActionResult {
                            launchCalls++
                            return ActionResult.Completed
                        }
                    },
                adapter =
                    object : MediaProviderAdapter {
                        override val id = "fake"
                        override val verificationStatus = ProviderVerificationStatus.TESTED
                        override val capabilities = emptySet<MediaProviderCapability>()

                        override fun supports(target: AppTarget) = true

                        override fun buildDirectContentIntent(request: MediaOpenRequest): Intent? = null

                        override fun buildSearchIntent(request: MediaOpenRequest): Intent? = null
                    },
                commands =
                    MediaSessionCommandGateway {
                        commandCalls++
                        if (completeOnCommand) outcome.complete(MediaObservedOutcome.PlaybackStarted("new", false, false))
                        MediaSessionCommandResult.Sent("play")
                    },
                observerFactory = { _, restored ->
                    restoredBaseline = restored
                    object : MediaOutcomeObserver {
                        override val baseline = restored ?: this@Fixture.baseline

                        override suspend fun awaitOutcome(timeoutMillis: Long) = outcome.await()

                        override fun close() {
                            closeCalls++
                        }
                    }
                },
                guidance =
                    object : ManualMediaGuidance {
                        override fun show(
                            action: ShortcutAction.PlayMedia,
                            executionContext: ActionExecutionContext,
                        ) {
                            guidanceShows++
                        }
                    },
                nowMillis = { 100 },
            )
    }

    private fun action() = ShortcutAction.PlayMedia("Player", "target.player", searchQuery = "query")

    private fun context() =
        ActionExecutionContext(
            executionId = "execution",
            routineId = ShortcutId("routine"),
            nodeId = NodeId("node"),
            logger = ExecutionLogger { _, _ -> },
            userInitiated = true,
        )

    private fun checkpoint(
        state: MediaExecutionState,
        operation: MediaOperation =
            MediaOperation(
                "media_session",
                MediaOperationType.MEDIA_SESSION,
                true,
                MediaOperationStatus.RUNNING,
                executionCount = 1,
            ),
        continuationCreated: Boolean = true,
        continuationConsumed: Boolean = false,
        manualShown: Boolean = false,
    ) = MediaExecutionCheckpoint(
        executionId = "execution",
        routineId = ShortcutId("routine"),
        nodeId = NodeId("node"),
        startedAtMillis = 10,
        automaticDeadlineMillis = 500,
        globalDeadlineMillis = 1_000,
        state = state,
        stateVersion = 3,
        operationId = operation.id,
        continuationCreated = continuationCreated,
        continuationConsumed = continuationConsumed,
        continuationKey = if (continuationCreated) "execution:node:${operation.id}:3" else null,
        manualGuidanceShown = manualShown,
        baseline = MediaSessionBaseline(emptySet(), emptySet(), capturedAtMillis = 10),
        plan = MediaExecutionPlan(listOf(operation)),
    )

    private fun workflow(checkpoint: MediaExecutionCheckpoint) =
        ActionWorkflowCheckpoint(
            actionId = checkpoint.nodeId,
            executionId = checkpoint.executionId,
            routineId = checkpoint.routineId,
            actionKind = ActionKind.PLAY_MEDIA,
            stateKey = "media_execution_v3",
            payload = mapOf("checkpoint" to MediaExecutionCheckpointCodec.encode(checkpoint)),
            startedAtMillis = checkpoint.startedAtMillis,
            expiresAtMillis = checkpoint.globalDeadlineMillis,
        )
}
