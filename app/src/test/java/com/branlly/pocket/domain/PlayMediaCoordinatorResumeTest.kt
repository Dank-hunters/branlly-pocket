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
import com.branlly.pocket.domain.model.MediaLaunchMode
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint
import com.branlly.pocket.platform.android.actions.AppTarget
import com.branlly.pocket.platform.android.actions.DirectMediaFailureNotice
import com.branlly.pocket.platform.android.actions.DirectMediaFailureReason
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
    fun `direct failure reasons have user-safe messages`() {
        DirectMediaFailureReason.entries.forEach { reason ->
            assertEquals(false, reason.userMessage.isBlank())
            assertEquals(false, reason.userMessage.contains("MediaSession", ignoreCase = true))
        }
    }

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

    @Test
    fun `direct media session completes before provider search is opened`() =
        runBlocking {
            val fixture = Fixture()
            fixture.completeOnCommand = true

            val result = fixture.coordinator().execute(action(), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.commandCalls)
            assertEquals(listOf("commanded"), fixture.dispatchedSessionIds)
            assertEquals(0, fixture.launchCalls)
            assertEquals(0, fixture.failureNotices.size)
        }

    @Test
    fun `provider search opens only after direct media session failure`() =
        runBlocking {
            val fixture = Fixture()
            fixture.commandResult = MediaSessionCommandResult.NotSupported("unsupported")
            fixture.completeOnLaunch = true

            val result = fixture.coordinator().execute(action(), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.commandCalls)
            assertEquals(1, fixture.launchCalls)
        }

    @Test
    fun `automatic direct failure posts one notice before provider search`() =
        runBlocking {
            val fixture = Fixture()
            fixture.commandResult = MediaSessionCommandResult.NotSupported("Aucune session du package cible.")
            fixture.completeOnLaunch = true

            val result = fixture.coordinator().execute(action(), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.launchCalls)
            assertEquals(1, fixture.failureNotices.size)
            assertEquals(DirectMediaFailureReason.NO_TARGET_SESSION, fixture.failureNotices.single().reason)
            assertEquals(MediaLaunchMode.AUTOMATIC, fixture.failureNotices.single().mode)
        }

    @Test
    fun `disabled notices do not alter automatic fallback`() =
        runBlocking {
            val fixture = Fixture()
            fixture.noticesAvailable = false
            fixture.commandResult = MediaSessionCommandResult.NotSupported("unsupported")
            fixture.completeOnLaunch = true

            val result = fixture.coordinator().execute(action(), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.launchCalls)
            assertEquals(0, fixture.failureNotices.size)
        }

    @Test
    fun `provider search also opens after direct media session runtime failure`() =
        runBlocking {
            val fixture = Fixture()
            fixture.commandResult = MediaSessionCommandResult.Failed("rejected")
            fixture.completeOnLaunch = true

            val result = fixture.coordinator().execute(action(), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(1, fixture.commandCalls)
            assertEquals(1, fixture.launchCalls)
        }

    @Test
    fun `background only fails without opening player when no compatible session exists`() =
        runBlocking {
            val fixture = Fixture()
            fixture.commandResult = MediaSessionCommandResult.NotSupported("Aucune session du package cible.")

            val result = fixture.coordinator().execute(action(MediaLaunchMode.BACKGROUND_ONLY), context())

            assertEquals(
                ActionResult.Failed("Aucune session multimédia compatible n’a été trouvée. Le lecteur n’a pas été ouvert."),
                result,
            )
            assertEquals(1, fixture.commandCalls)
            assertEquals(0, fixture.launchCalls)
            assertEquals(listOf(DirectMediaFailureReason.NO_TARGET_SESSION), fixture.failureNotices.map { it.reason })
        }

    @Test
    fun `background only timeout never falls back to player`() =
        runBlocking {
            val fixture = Fixture()
            val applied =
                checkpoint(
                    state = MediaExecutionState.AWAIT_OUTCOME,
                    operation =
                        MediaOperation(
                            "media_session",
                            MediaOperationType.MEDIA_SESSION,
                            true,
                            MediaOperationStatus.AWAITING_OUTCOME,
                            effectApplied = true,
                            executionCount = 1,
                        ),
                    continuationCreated = false,
                    continuationConsumed = false,
                    automaticDeadlineMillis = 100,
                )

            val result = fixture.coordinator().execute(action(MediaLaunchMode.BACKGROUND_ONLY), context(), workflow(applied))

            assertEquals(
                ActionResult.Failed("La lecture n’a pas été confirmée dans le délai prévu. Le lecteur n’a pas été ouvert."),
                result,
            )
            assertEquals(0, fixture.commandCalls)
            assertEquals(0, fixture.launchCalls)
            assertEquals(listOf(DirectMediaFailureReason.PLAYBACK_NOT_CONFIRMED), fixture.failureNotices.map { it.reason })
        }

    @Test
    fun `open player skips direct media session`() =
        runBlocking {
            val fixture = Fixture()
            fixture.completeOnLaunch = true

            val result = fixture.coordinator().execute(action(MediaLaunchMode.OPEN_PLAYER), context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(0, fixture.commandCalls)
            assertEquals(1, fixture.launchCalls)
        }

    @Test
    fun `open player URI opens directly without a media session command`() =
        runBlocking {
            val fixture = Fixture()
            fixture.directUriIntent = Intent("test.uri")
            fixture.completeOnLaunch = true
            val action =
                ShortcutAction.PlayMedia(
                    "Player",
                    "target.player",
                    searchQuery = "",
                    mediaUri = "https://example.test/media",
                    launchMode = MediaLaunchMode.OPEN_PLAYER,
                )

            val result = fixture.coordinator().execute(action, context())

            assertEquals(ActionResult.Completed, result)
            assertEquals(0, fixture.commandCalls)
            assertEquals(1, fixture.launchCalls)
        }

    @Test
    fun `applied media session command is not replayed from checkpoint`() =
        runBlocking {
            val fixture = Fixture(MediaObservedOutcome.PlaybackStarted("new", false, false))
            val applied =
                checkpoint(
                    state = MediaExecutionState.AWAIT_OUTCOME,
                    operation =
                        MediaOperation(
                            "media_session",
                            MediaOperationType.MEDIA_SESSION,
                            true,
                            MediaOperationStatus.AWAITING_OUTCOME,
                            effectApplied = true,
                            executionCount = 1,
                        ),
                    continuationCreated = false,
                    continuationConsumed = false,
                )

            val result = fixture.coordinator().execute(action(), context(), workflow(applied))

            assertEquals(ActionResult.Completed, result)
            assertEquals(0, fixture.commandCalls)
            assertEquals(0, fixture.launchCalls)
        }

    @Test
    fun `unresolved applied media session checkpoint is not replayed`() =
        runBlocking {
            val fixture = Fixture()
            val applied =
                checkpoint(
                    state = MediaExecutionState.AWAIT_OUTCOME,
                    operation =
                        MediaOperation(
                            "media_session",
                            MediaOperationType.MEDIA_SESSION,
                            true,
                            MediaOperationStatus.AWAITING_OUTCOME,
                            effectApplied = true,
                            executionCount = 1,
                        ),
                    continuationCreated = false,
                    continuationConsumed = false,
                    automaticDeadlineMillis = 100,
                )

            val result = fixture.coordinator().execute(action(), context(), workflow(applied))

            assertEquals(ActionResult.TimedOut("Aucune opération média restante."), result)
            assertEquals(0, fixture.commandCalls)
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
        var completeOnLaunch = false
        var commandResult: MediaSessionCommandResult = MediaSessionCommandResult.Sent("playFromSearch", "commanded")
        var directUriIntent: Intent? = null
        val dispatchedSessionIds = mutableListOf<String?>()
        val failureNotices = mutableListOf<DirectMediaFailureNotice>()
        var noticesAvailable = true

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
                            if (completeOnLaunch) outcome.complete(MediaObservedOutcome.PlaybackStarted("new", false, false))
                            return ActionResult.Completed
                        }
                    },
                adapter =
                    object : MediaProviderAdapter {
                        override val id = "fake"
                        override val verificationStatus = ProviderVerificationStatus.TESTED
                        override val capabilities = emptySet<MediaProviderCapability>()

                        override fun supports(target: AppTarget) = true

                        override fun buildDirectContentIntent(request: MediaOpenRequest): Intent? = directUriIntent

                        override fun buildSearchIntent(request: MediaOpenRequest): Intent = Intent("test.search")
                    },
                commands =
                    MediaSessionCommandGateway {
                        commandCalls++
                        if (completeOnCommand) outcome.complete(MediaObservedOutcome.PlaybackStarted("new", false, false))
                        commandResult
                    },
                observerFactory = { _, restored ->
                    restoredBaseline = restored
                    object : MediaOutcomeObserver {
                        override val baseline = restored ?: this@Fixture.baseline

                        override suspend fun awaitOutcome(timeoutMillis: Long) = outcome.await()

                        override fun onOperationDispatched(
                            commandedSessionId: String?,
                            commandedController: com.branlly.pocket.domain.media.ObservableMediaController?,
                        ) {
                            dispatchedSessionIds += commandedSessionId
                        }

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

                        override fun showDirectFailure(
                            notice: DirectMediaFailureNotice,
                            executionContext: ActionExecutionContext,
                        ): Boolean {
                            if (!noticesAvailable) return false
                            failureNotices += notice
                            return true
                        }
                    },
                nowMillis = { 100 },
            )
    }

    private fun action(mode: MediaLaunchMode = MediaLaunchMode.AUTOMATIC) =
        ShortcutAction.PlayMedia("Player", "target.player", searchQuery = "query", launchMode = mode)

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
        automaticDeadlineMillis: Long = 500,
    ) = MediaExecutionCheckpoint(
        executionId = "execution",
        routineId = ShortcutId("routine"),
        nodeId = NodeId("node"),
        startedAtMillis = 10,
        automaticDeadlineMillis = automaticDeadlineMillis,
        globalDeadlineMillis = 1_000,
        state = state,
        stateVersion = 3,
        operationId = operation.id,
        continuationCreated = continuationCreated,
        continuationConsumed = continuationConsumed,
        continuationKey = if (continuationCreated) "execution:node:${operation.id}:3" else null,
        manualGuidanceShown = manualShown,
        baseline = MediaSessionBaseline(emptySet(), emptySet(), capturedAtMillis = 10),
        plan = MediaExecutionPlan(canonicalPlan(operation)),
    )

    private fun canonicalPlan(operation: MediaOperation): List<MediaOperation> =
        if (operation.type == MediaOperationType.MANUAL_ASSISTANCE) {
            listOf(
                MediaOperation(
                    "media_session",
                    MediaOperationType.MEDIA_SESSION,
                    true,
                    MediaOperationStatus.COMPLETED,
                    effectApplied = true,
                    executionCount = 1,
                ),
                MediaOperation(
                    "provider_search",
                    MediaOperationType.PROVIDER_SEARCH,
                    true,
                    MediaOperationStatus.COMPLETED,
                    effectApplied = true,
                    executionCount = 1,
                ),
                operation,
            )
        } else {
            listOf(
                operation,
                MediaOperation("provider_search", MediaOperationType.PROVIDER_SEARCH, true),
                MediaOperation("manual_assistance", MediaOperationType.MANUAL_ASSISTANCE, false),
            )
        }

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
