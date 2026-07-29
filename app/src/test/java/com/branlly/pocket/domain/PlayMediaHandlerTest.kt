package com.branlly.pocket.domain

import android.content.Intent
import android.provider.Settings
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.media.MediaProviderCapability
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.CapabilityResolver
import com.branlly.pocket.platform.android.actions.AppTarget
import com.branlly.pocket.platform.android.actions.ExternalActivityGateway
import com.branlly.pocket.platform.android.actions.MediaOpenRequest
import com.branlly.pocket.platform.android.actions.MediaProviderAdapter
import com.branlly.pocket.platform.android.actions.MediaSessionCommandGateway
import com.branlly.pocket.platform.android.actions.MediaSessionCommandResult
import com.branlly.pocket.platform.android.actions.PlayMediaCoordinator
import com.branlly.pocket.platform.android.actions.PlayMediaHandler
import com.branlly.pocket.platform.android.actions.ProviderVerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayMediaHandlerTest {
    @Test
    fun `simple editor values remain valid`() {
        assertTrue(handler().validate(action(), validation()).isEmpty())
    }

    @Test
    fun `handler delegates PLAY_MEDIA execution to the V3 coordinator`() =
        runBlocking {
            var configuredAction: ShortcutAction.PlayMedia? = null
            val coordinator = coordinator()
            val handler =
                PlayMediaHandler(
                    capabilityResolver = CapabilityResolver { capabilities() },
                    coordinatorFactory = { selected ->
                        configuredAction = selected
                        coordinator
                    },
                )
            val action = action()

            val result = handler.execute(action, context())

            assertEquals(ActionResult.Completed, result)
            assertSame(action, configuredAction)
        }

    @Test
    fun `missing media control access returns settings without creating V3 coordinator`() =
        runBlocking {
            var coordinatorCreated = false
            val handler =
                PlayMediaHandler(
                    capabilityResolver = CapabilityResolver { capabilities().copy(notificationListenerAuthorized = false) },
                    coordinatorFactory = {
                        coordinatorCreated = true
                        coordinator()
                    },
                )

            val result = handler.execute(action(), context())

            assertEquals(
                ActionResult.PermissionRequired(
                    reason = "Autorisez le contrôle de lecture pour confirmer STATE_PLAYING.",
                    settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                ),
                result,
            )
            assertTrue(!coordinatorCreated)
        }

    private fun handler() =
        PlayMediaHandler(
            capabilityResolver = CapabilityResolver { capabilities() },
            coordinatorFactory = { error("Validation must not create a coordinator.") },
        )

    private fun coordinator() =
        PlayMediaCoordinator(
            launcher =
                object : ExternalActivityGateway {
                    override fun canResolve(intent: Intent) = true

                    override suspend fun launch(
                        intent: Intent,
                        label: String,
                        executionContext: ActionExecutionContext,
                    ) = ActionResult.Completed
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
            commands = MediaSessionCommandGateway { MediaSessionCommandResult.NotSupported("unused") },
            observerFactory = { _, baseline ->
                object : MediaOutcomeObserver {
                    override val baseline = baseline ?: MediaSessionBaseline(emptySet(), emptySet(), capturedAtMillis = 1)

                    override suspend fun awaitOutcome(timeoutMillis: Long) =
                        MediaObservedOutcome.PlaybackStarted("session", contentConfirmed = false, preexisting = false)

                    override fun close() = Unit
                }
            },
            guidance = { _, _ -> },
            nowMillis = { 1 },
        )

    private fun action() = ShortcutAction.PlayMedia("Target", "target.player", searchQuery = "query")

    private fun capabilities() =
        MediaCapabilitySnapshot(
            packageInstalled = true,
            packageLaunchable = true,
            exactActivityAvailable = true,
            directUriProvided = false,
            providerAdapterId = "fake",
            providerCapabilities = emptySet(),
            notificationListenerAuthorized = true,
            notificationListenerAvailable = true,
            exactPackageSessionCount = 0,
            transportActions = 0,
            manualFallbackAllowed = true,
            advancedAutomationAllowed = false,
            advancedAutomationAvailable = false,
        )

    private fun validation() =
        object : ActionValidationContext {
            override fun isPackageInstalled(packageName: String) = true

            override fun isPackageLaunchable(packageName: String) = true
        }

    private fun context() =
        ActionExecutionContext(
            executionId = "execution",
            routineId = ShortcutId.new(),
            nodeId = NodeId.new(),
            logger = ExecutionLogger { _, _ -> },
        )
}
