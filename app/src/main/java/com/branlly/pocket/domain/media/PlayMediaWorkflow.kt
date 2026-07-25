package com.branlly.pocket.domain.media

import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.model.MediaErrorStrategy
import com.branlly.pocket.domain.model.MediaSelectionPolicy
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.ActionProgress
import com.branlly.pocket.domain.workflow.ActionWorkflow
import com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint
import com.branlly.pocket.domain.workflow.ActionWorkflowContext
import com.branlly.pocket.domain.workflow.ActionWorkflowState
import com.branlly.pocket.domain.workflow.ActionWorkflowStep

enum class MediaProviderCapability {
    CAN_OPEN_APP,
    CAN_OPEN_SEARCH,
    CAN_OPEN_DIRECT_CONTENT,
    CAN_REQUEST_PLAYBACK,
    CAN_AUTOMATE_RESULT_SELECTION,
    CAN_VERIFY_CONTENT,
}

data class MediaCapabilitySnapshot(
    val packageInstalled: Boolean,
    val packageLaunchable: Boolean,
    val exactActivityAvailable: Boolean,
    val directUriProvided: Boolean,
    val providerAdapterId: String?,
    val providerCapabilities: Set<MediaProviderCapability>,
    val notificationListenerAuthorized: Boolean,
    val notificationListenerAvailable: Boolean,
    val exactPackageSessionCount: Int,
    val transportActions: Long,
    val manualFallbackAllowed: Boolean,
    val advancedAutomationAllowed: Boolean,
    val advancedAutomationAvailable: Boolean,
)

enum class MediaPlaybackStatus { PLAYING, PAUSED, OTHER }

data class MediaSessionSnapshot(
    val sessionId: String,
    val packageName: String,
    val status: MediaPlaybackStatus,
)

class ExactPackagePlaybackTracker(private val targetPackage: String) {
    var observedTargetSession: Boolean = false
        private set

    fun observe(sessions: List<MediaSessionSnapshot>): Boolean {
        val targets = sessions.filter { it.packageName == targetPackage }
        if (targets.isNotEmpty()) observedTargetSession = true
        return targets.any { it.status == MediaPlaybackStatus.PLAYING }
    }
}

enum class MediaPlaybackConfirmation {
    PLAYBACK_CONFIRMED,
    CONTENT_CONFIRMED,
    PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED,
}

sealed interface MediaStrategyResult {
    data class StartedPlayback(val confirmation: MediaPlaybackConfirmation) : MediaStrategyResult
    data class AwaitingPlayback(val reason: String) : MediaStrategyResult
    data class NotSupported(val reason: String) : MediaStrategyResult
    data class RecoverableFailure(val reason: String) : MediaStrategyResult
    data class TerminalFailure(val reason: String) : MediaStrategyResult
    data class UserActionRequired(val reason: String) : MediaStrategyResult
    data class Cancelled(val reason: String) : MediaStrategyResult
    data class TimedOut(val reason: String) : MediaStrategyResult
}

data class MediaStrategyContext(
    val action: ShortcutAction.PlayMedia,
    val executionContext: ActionExecutionContext,
    val capabilities: MediaCapabilitySnapshot,
    val remainingTimeoutMillis: Long,
)

interface MediaPlaybackStrategy {
    val id: String
    val priority: Int
    val timeoutMillis: Long?

    fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot): Boolean

    suspend fun execute(context: MediaStrategyContext): MediaStrategyResult

    suspend fun cleanup() = Unit
}

enum class PlayMediaState(override val key: String) : ActionWorkflowState {
    RESOLVING_TARGET("resolving_target"),
    RESOLVING_CAPABILITIES("resolving_capabilities"),
    TRYING_STRATEGY("trying_strategy"),
    COMPLETED("completed"),
}

class PlayMediaWorkflow(
    private val action: ShortcutAction.PlayMedia,
    private val executionContext: ActionExecutionContext,
    private val capabilities: MediaCapabilitySnapshot,
    strategies: List<MediaPlaybackStrategy>,
    restoredCheckpoint: ActionWorkflowCheckpoint? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ActionWorkflow<PlayMediaState> {
    override val initialState: PlayMediaState = restoredCheckpoint
        ?.stateKey
        ?.let { key -> PlayMediaState.entries.firstOrNull { it.key == key } }
        ?: PlayMediaState.RESOLVING_TARGET

    private val orderedStrategies = strategies.sortedBy(MediaPlaybackStrategy::priority)
    private val attempted = restoredCheckpoint?.payload?.get(KEY_ATTEMPTED)
        ?.split(',')
        ?.filter(String::isNotBlank)
        ?.toMutableSet()
        ?: mutableSetOf()
    private var pendingStrategyId: String? = restoredCheckpoint?.payload?.get(KEY_PENDING)?.ifBlank { null }

    override suspend fun transition(
        state: PlayMediaState,
        context: ActionWorkflowContext,
    ): ActionWorkflowStep<PlayMediaState> = when (state) {
        PlayMediaState.RESOLVING_TARGET -> {
            context.logger.log("PLAY_MEDIA_START", baseFields(context))
            ActionWorkflowStep.ContinueInternally(
                PlayMediaState.RESOLVING_CAPABILITIES,
                ActionProgress.Resolving("Résolution du lecteur multimédia"),
            )
        }
        PlayMediaState.RESOLVING_CAPABILITIES -> {
            context.logger.log(
                "CAPABILITIES_RESOLVED",
                baseFields(context) + mapOf(
                    "adapter" to capabilities.providerAdapterId,
                    "sessionCount" to capabilities.exactPackageSessionCount,
                    "listenerAvailable" to capabilities.notificationListenerAvailable,
                ),
            )
            ActionWorkflowStep.ContinueInternally(PlayMediaState.TRYING_STRATEGY)
        }
        PlayMediaState.TRYING_STRATEGY -> executeNextStrategy(context)
        PlayMediaState.COMPLETED -> {
            context.logger.log("PLAY_MEDIA_COMPLETED", baseFields(context))
            ActionWorkflowStep.Completed
        }
    }

    private suspend fun executeNextStrategy(context: ActionWorkflowContext): ActionWorkflowStep<PlayMediaState> {
        val strategy = pendingStrategyId
            ?.let { pending -> orderedStrategies.firstOrNull { it.id == pending } }
            ?: orderedStrategies.firstOrNull { candidate ->
                candidate.id !in attempted && candidate.isAvailable(action, capabilities)
            }
            ?: return ActionWorkflowStep.Failed("Aucune stratégie média disponible.")
        pendingStrategyId = null
        context.logger.log("STRATEGY_SELECTED", baseFields(context) + mapOf("strategy" to strategy.id))
        context.logger.log("STRATEGY_STARTED", baseFields(context) + mapOf("strategy" to strategy.id))
        val remaining = (context.expiresAtMillis - nowMillis()).coerceAtLeast(1L)
        val strategyTimeout = strategy.timeoutMillis?.coerceAtMost(remaining) ?: remaining
        val result = try {
            strategy.execute(MediaStrategyContext(action, executionContext, capabilities, strategyTimeout))
        } finally {
            strategy.cleanup()
            context.logger.log("PLAY_MEDIA_CLEANUP", baseFields(context) + mapOf("strategy" to strategy.id))
        }
        return when (result) {
            is MediaStrategyResult.StartedPlayback -> {
                attempted += strategy.id
                if (action.selectionPolicy == MediaSelectionPolicy.EXACT_MATCH &&
                    result.confirmation != MediaPlaybackConfirmation.CONTENT_CONFIRMED
                ) {
                    val reason = "Lecture confirmée, mais contenu exact non vérifiable."
                    context.logger.log("PLAY_MEDIA_FAILED", baseFields(context) + mapOf("strategy" to strategy.id, "reason" to reason))
                    return ActionWorkflowStep.Failed(reason)
                }
                context.logger.log(
                    "STRATEGY_SUCCEEDED",
                    baseFields(context) + mapOf("strategy" to strategy.id, "confirmation" to result.confirmation),
                )
                context.logger.log("PLAYBACK_CONFIRMED", baseFields(context) + mapOf("confirmation" to result.confirmation))
                ActionWorkflowStep.ContinueInternally(PlayMediaState.COMPLETED)
            }
            is MediaStrategyResult.UserActionRequired -> {
                pendingStrategyId = strategy.id
                val checkpoint = checkpoint(context, strategy.id)
                context.logger.log("WAITING_FOR_USER", baseFields(context) + mapOf("strategy" to strategy.id))
                ActionWorkflowStep.UserActionRequired(result.reason, checkpoint)
            }
            is MediaStrategyResult.TerminalFailure -> {
                attempted += strategy.id
                context.logger.log("PLAY_MEDIA_FAILED", baseFields(context) + mapOf("strategy" to strategy.id, "reason" to result.reason))
                ActionWorkflowStep.Failed(result.reason)
            }
            is MediaStrategyResult.Cancelled -> ActionWorkflowStep.Cancelled(result.reason)
            is MediaStrategyResult.TimedOut -> ActionWorkflowStep.TimedOut(result.reason)
            is MediaStrategyResult.AwaitingPlayback -> recoverable(context, strategy, result.reason)
            is MediaStrategyResult.NotSupported -> recoverable(context, strategy, result.reason)
            is MediaStrategyResult.RecoverableFailure -> recoverable(context, strategy, result.reason)
        }
    }

    private fun recoverable(
        context: ActionWorkflowContext,
        strategy: MediaPlaybackStrategy,
        reason: String,
    ): ActionWorkflowStep<PlayMediaState> {
        attempted += strategy.id
        context.logger.log("STRATEGY_FAILED", baseFields(context) + mapOf("strategy" to strategy.id, "reason" to reason))
        if (action.errorStrategy == MediaErrorStrategy.STOP_ON_FIRST_FAILURE) return ActionWorkflowStep.Failed(reason, recoverable = true)
        return ActionWorkflowStep.ContinueInternally(
            PlayMediaState.TRYING_STRATEGY,
            checkpoint = checkpoint(context, pending = ""),
        )
    }

    private fun checkpoint(context: ActionWorkflowContext, pending: String): ActionWorkflowCheckpoint = ActionWorkflowCheckpoint(
        actionId = context.actionId,
        executionId = context.executionId,
        routineId = context.routineId,
        actionKind = context.actionKind,
        stateKey = PlayMediaState.TRYING_STRATEGY.key,
        payload = mapOf(KEY_ATTEMPTED to attempted.sorted().joinToString(","), KEY_PENDING to pending),
        startedAtMillis = context.startedAtMillis,
        expiresAtMillis = context.expiresAtMillis,
    )

    private fun baseFields(context: ActionWorkflowContext): Map<String, Any?> = mapOf(
        "executionId" to context.executionId,
        "nodeId" to context.actionId.value,
        "targetPackage" to action.targetPackage,
    )

    companion object {
        const val MAX_TRANSITIONS = 12
        private const val KEY_ATTEMPTED = "attempted"
        private const val KEY_PENDING = "pending"
    }
}
