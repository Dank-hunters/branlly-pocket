package com.branlly.pocket.domain

import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.media.ExactPackagePlaybackTracker
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.media.MediaPlaybackConfirmation
import com.branlly.pocket.domain.media.MediaPlaybackStatus
import com.branlly.pocket.domain.media.MediaPlaybackStrategy
import com.branlly.pocket.domain.media.MediaSessionSnapshot
import com.branlly.pocket.domain.media.MediaStrategyContext
import com.branlly.pocket.domain.media.MediaStrategyResult
import com.branlly.pocket.domain.media.PlayMediaWorkflow
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.ActionWorkflowContext
import com.branlly.pocket.domain.workflow.BoundedActionWorkflowRunner
import com.branlly.pocket.domain.workflow.CapabilityResolver
import com.branlly.pocket.platform.android.MediaPlaybackWaiter
import com.branlly.pocket.platform.android.MediaWaitResult
import com.branlly.pocket.platform.android.actions.MediaSessionCommandGateway
import com.branlly.pocket.platform.android.actions.MediaSessionCommandResult
import com.branlly.pocket.platform.android.actions.MediaSessionPlaybackStrategy
import com.branlly.pocket.platform.android.actions.PlayMediaHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayMediaWorkflowTest {
    @Test
    fun `simple form values validate`() {
        assertTrue(handler().validate(action(), validation(installed = true)).isEmpty())
    }

    @Test
    fun `missing package is rejected on its field`() {
        val errors = handler().validate(action(packageName = "missing.player"), validation(installed = false))
        assertTrue(errors.any { it.code == "missing_package" })
    }

    @Test
    fun `missing search without URI is rejected`() {
        val errors = handler().validate(action(query = ""), validation(installed = true))
        assertTrue(errors.any { it.code == "missing_search" })
    }

    @Test
    fun `direct URI strategy has priority`() = runBlocking {
        val calls = mutableListOf<String>()
        val direct = strategy("direct_uri", 10, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED))
        val session = strategy("media_session", 20, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED))

        val result = run(action(uri = "https://example.test/media"), listOf(session, direct))

        assertEquals(ActionResult.Completed, result)
        assertEquals(listOf("direct_uri"), calls)
    }

    @Test
    fun `recoverable direct URI failure advances to MediaSession once`() = runBlocking {
        val calls = mutableListOf<String>()
        val direct = strategy("direct_uri", 10, calls, MediaStrategyResult.RecoverableFailure("unsupported"))
        val session = strategy("media_session", 20, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED))

        assertEquals(ActionResult.Completed, run(action(uri = "https://example.test/media"), listOf(direct, session)))
        assertEquals(listOf("direct_uri", "media_session"), calls)
    }

    @Test
    fun `MediaSession supported command still requires STATE_PLAYING`() = runBlocking {
        val gateway = MediaSessionCommandGateway { MediaSessionCommandResult.Sent("playFromSearch") }
        val playing = MediaPlaybackWaiter { _, _ -> MediaWaitResult.Playing }
        val strategy = MediaSessionPlaybackStrategy(gateway, playing)

        val result = strategy.execute(mediaContext())

        assertTrue(result is MediaStrategyResult.StartedPlayback)
    }

    @Test
    fun `ignored MediaSession command is recoverable and never Completed`() = runBlocking {
        val gateway = MediaSessionCommandGateway { MediaSessionCommandResult.Sent("play") }
        val paused = MediaPlaybackWaiter { _, _ -> MediaWaitResult.TimedOut }

        val result = MediaSessionPlaybackStrategy(gateway, paused).execute(mediaContext())

        assertTrue(result is MediaStrategyResult.RecoverableFailure)
    }

    @Test
    fun `unsupported MediaSession command passes to next strategy`() = runBlocking {
        val calls = mutableListOf<String>()
        val unsupported = strategy("media_session", 20, calls, MediaStrategyResult.NotSupported("no action"))
        val provider = strategy("provider_intent", 30, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED))

        assertEquals(ActionResult.Completed, run(action(), listOf(unsupported, provider)))
        assertEquals(listOf("media_session", "provider_intent"), calls)
    }

    @Test
    fun `wrong package and PAUSED never confirm playback`() {
        val tracker = ExactPackagePlaybackTracker("target.player")
        assertFalse(tracker.observe(listOf(MediaSessionSnapshot("1", "other.player", MediaPlaybackStatus.PLAYING))))
        assertFalse(tracker.observe(listOf(MediaSessionSnapshot("2", "target.player", MediaPlaybackStatus.PAUSED))))
        assertTrue(tracker.observedTargetSession)
    }

    @Test
    fun `PLAYING from recreated exact session confirms playback`() {
        val tracker = ExactPackagePlaybackTracker("target.player")
        assertFalse(tracker.observe(listOf(MediaSessionSnapshot("old", "target.player", MediaPlaybackStatus.PAUSED))))
        assertTrue(tracker.observe(listOf(MediaSessionSnapshot("new", "target.player", MediaPlaybackStatus.PLAYING))))
    }

    @Test
    fun `manual fallback may complete only after playback confirmation`() = runBlocking {
        val calls = mutableListOf<String>()
        val manual = strategy("manual_fallback", 40, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED))
        assertEquals(ActionResult.Completed, run(action(), listOf(manual)))
        assertEquals(listOf("manual_fallback"), calls)
    }

    @Test
    fun `global timeout interrupts current strategy`() = runBlocking {
        val slow = object : MediaPlaybackStrategy {
            override val id = "slow"
            override val priority = 1
            override val timeoutMillis: Long? = null
            override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) = true
            override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult {
                delay(500)
                return MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED)
            }
        }
        assertTrue(run(action(timeout = 15_000), listOf(slow), runnerTimeout = 100) is ActionResult.TimedOut)
    }

    @Test
    fun `cancelled strategy cancels workflow`() = runBlocking {
        val cancelled = strategy("manual", 1, mutableListOf(), MediaStrategyResult.Cancelled("cancel"))
        assertTrue(run(action(), listOf(cancelled)) is ActionResult.Cancelled)
    }

    @Test
    fun `each failed strategy is attempted at most once`() = runBlocking {
        val calls = mutableListOf<String>()
        val strategies = listOf(
            strategy("one", 1, calls, MediaStrategyResult.RecoverableFailure("one")),
            strategy("two", 2, calls, MediaStrategyResult.RecoverableFailure("two")),
            strategy("manual", 3, calls, MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED)),
        )
        assertEquals(ActionResult.Completed, run(action(), strategies))
        assertEquals(listOf("one", "two", "manual"), calls)
    }

    @Test
    fun `strategy cleanup always runs`() = runBlocking {
        var cleaned = false
        val strategy = object : MediaPlaybackStrategy {
            override val id = "cleanup"
            override val priority = 1
            override val timeoutMillis: Long? = null
            override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) = true
            override suspend fun execute(context: MediaStrategyContext) = MediaStrategyResult.TerminalFailure("failure")
            override suspend fun cleanup() { cleaned = true }
        }
        run(action(), listOf(strategy))
        assertTrue(cleaned)
    }

    @Test
    fun `UserActionRequired carries minimal resumable checkpoint`() = runBlocking {
        val waiting = strategy("provider", 1, mutableListOf(), MediaStrategyResult.UserActionRequired("tap"))
        val result = run(action(), listOf(waiting)) as ActionResult.UserActionRequired
        assertNotNull(result.workflowCheckpoint)
        assertEquals("provider", result.workflowCheckpoint?.payload?.get("pending"))
        assertFalse(result.workflowCheckpoint!!.payload.containsKey("intent"))
    }

    private fun handler() = PlayMediaHandler(
        capabilityResolver = CapabilityResolver { capabilities() },
        strategyFactory = { emptyList() },
    )

    private fun validation(installed: Boolean) = object : ActionValidationContext {
        override fun isPackageInstalled(packageName: String) = installed
        override fun isPackageLaunchable(packageName: String) = true
    }

    private fun action(
        packageName: String = "target.player",
        query: String = "Fichu Django",
        uri: String? = null,
        timeout: Long = 120_000,
    ) = ShortcutAction.PlayMedia("Target Player", packageName, searchQuery = query, mediaUri = uri, timeoutMs = timeout)

    private fun capabilities() = MediaCapabilitySnapshot(
        packageInstalled = true,
        packageLaunchable = true,
        exactActivityAvailable = true,
        directUriProvided = true,
        providerAdapterId = "fake",
        providerCapabilities = emptySet(),
        notificationListenerAuthorized = true,
        notificationListenerAvailable = true,
        exactPackageSessionCount = 1,
        transportActions = 0,
        manualFallbackAllowed = true,
        advancedAutomationAllowed = false,
        advancedAutomationAvailable = false,
    )

    private fun strategy(
        id: String,
        priority: Int,
        calls: MutableList<String>,
        result: MediaStrategyResult,
    ) = object : MediaPlaybackStrategy {
        override val id = id
        override val priority = priority
        override val timeoutMillis: Long? = null
        override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) = true
        override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult {
            calls += id
            return result
        }
    }

    private suspend fun run(
        action: ShortcutAction.PlayMedia,
        strategies: List<MediaPlaybackStrategy>,
        runnerTimeout: Long = 5_000,
    ): ActionResult {
        val execution = executionContext()
        val workflowContext = ActionWorkflowContext(
            actionId = execution.nodeId,
            executionId = execution.executionId,
            routineId = execution.routineId,
            actionKind = action.kind,
            startedAtMillis = 1,
            expiresAtMillis = 100_000,
            logger = execution.logger,
        )
        return BoundedActionWorkflowRunner(PlayMediaWorkflow.MAX_TRANSITIONS, runnerTimeout)
            .run(PlayMediaWorkflow(action, execution, capabilities(), strategies, nowMillis = { 2 }), workflowContext)
            .result
    }

    private fun executionContext() = ActionExecutionContext(
        "execution",
        ShortcutId.new(),
        NodeId.new(),
        ExecutionLogger { _, _ -> },
    )

    private fun mediaContext() = MediaStrategyContext(action(), executionContext(), capabilities(), 1_000)
}
