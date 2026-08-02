package com.branlly.pocket.platform.android.actions

import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.media.MediaConfirmationLevel
import com.branlly.pocket.domain.media.MediaExecutionCheckpoint
import com.branlly.pocket.domain.media.MediaExecutionCheckpointCodec
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionResult
import com.branlly.pocket.domain.media.MediaExecutionSession
import com.branlly.pocket.domain.media.MediaExecutionState
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaOperation
import com.branlly.pocket.domain.media.MediaOperationStatus
import com.branlly.pocket.domain.media.MediaOperationType
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.MediaLaunchMode
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Coordinates one frozen plan, one observer and one terminal result for PLAY_MEDIA. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayMediaCoordinator(
    private val launcher: ExternalActivityGateway,
    private val adapter: MediaProviderAdapter,
    private val commands: MediaSessionCommandGateway,
    private val observerFactory: (String, MediaSessionBaseline?) -> MediaOutcomeObserver,
    private val guidance: ManualMediaGuidance,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
        workflowCheckpoint: ActionWorkflowCheckpoint? = null,
    ): ActionResult =
        coroutineScope {
            val restoredCheckpoint = workflowCheckpoint?.let { decodeCheckpoint(it, context) }
            if (workflowCheckpoint != null && restoredCheckpoint == null) {
                return@coroutineScope ActionResult.Failed("Le checkpoint PLAY_MEDIA V3 est invalide.")
            }
            val session: MediaExecutionSession
            val observer: MediaOutcomeObserver
            if (restoredCheckpoint == null) {
                observer = observerFactory(action.targetPackage, null)
                val now = nowMillis()
                val deadline = now + action.timeoutMs
                session =
                    MediaExecutionSession(
                        executionId = context.executionId,
                        routineId = context.routineId,
                        nodeId = context.nodeId,
                        targetPackage = action.targetPackage,
                        searchQuery = action.searchQuery,
                        mediaUri = action.mediaUri,
                        selectionPolicy = action.selectionPolicy,
                        baseline = observer.baseline,
                        plan = buildPlan(action),
                        automaticDeadlineMillis = minOf(deadline, now + AUTOMATIC_BUDGET_MILLIS),
                        globalDeadlineMillis = deadline,
                        startedAtMillis = now,
                    )
                session.move(MediaExecutionState.AWAIT_OUTCOME)
            } else {
                session =
                    MediaExecutionSession.restore(
                        restoredCheckpoint,
                        action.targetPackage,
                        action.searchQuery,
                        action.mediaUri,
                        action.selectionPolicy,
                    )
                observer = observerFactory(action.targetPackage, restoredCheckpoint.baseline)
            }
            if (!planMatchesLaunchMode(session.checkpoint().plan, action.launchMode)) {
                observer.close()
                return@coroutineScope ActionResult.Failed("Le checkpoint PLAY_MEDIA ne correspond pas au mode de lancement enregistré.")
            }
            if (session.globalDeadlineMillis <= nowMillis()) {
                observer.close()
                return@coroutineScope ActionResult.TimedOut("Le workflow PLAY_MEDIA V3 a expiré.")
            }
            val outcome =
                async(start = CoroutineStart.UNDISPATCHED) {
                    observer.awaitOutcome((session.globalDeadlineMillis - nowMillis()).coerceAtLeast(1))
                }
            try {
                session
                    .currentOperation()
                    ?.takeIf { it.status in setOf(MediaOperationStatus.EFFECT_APPLIED, MediaOperationStatus.AWAITING_OUTCOME) }
                    ?.let { observer.onOperationDispatched(it.commandedSessionId) }
                outcome.getCompletedOrNull()?.let { return@coroutineScope finish(session, it) }
                when (session.state()) {
                    MediaExecutionState.AWAIT_USER_LAUNCH -> {
                        return@coroutineScope resumeUserLaunch(session, action, context, outcome, observer)
                    }

                    MediaExecutionState.AWAIT_MANUAL_PLAY -> {
                        if (!session.checkpoint().manualGuidanceShown && session.markManualGuidanceShown()) guidance.show(action, context)
                        return@coroutineScope finish(session, outcome.await())
                    }

                    else -> {
                        Unit
                    }
                }
                executePlan(session, action, context, outcome, observer)
            } finally {
                outcome.cancel()
                observer.close()
                guidance.clear()
            }
        }

    private suspend fun executePlan(
        session: MediaExecutionSession,
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
        outcome: kotlinx.coroutines.Deferred<MediaObservedOutcome>,
        observer: MediaOutcomeObserver,
    ): ActionResult {
        var directFailureReason: DirectFailureReason? = null
        while (nowMillis() < session.globalDeadlineMillis) {
            outcome.getCompletedOrNull()?.let { return finish(session, it) }
            val active = session.currentOperation()
            if (active?.status == MediaOperationStatus.AWAITING_OUTCOME || active?.status == MediaOperationStatus.EFFECT_APPLIED) {
                val observed = awaitAutomaticOutcome(session, outcome)
                if (observed != null) {
                    logObservedOutcome(context, observed)
                    return finish(session, observed)
                }
                context.logger.log(
                    "PLAY_MEDIA_OUTCOME_TIMEOUT",
                    mapOf("nodeId" to context.nodeId.value, "operationType" to active.type.name),
                )
                if (active.type == MediaOperationType.MEDIA_SESSION) {
                    directFailureReason = DirectFailureReason.PLAYBACK_NOT_CONFIRMED
                    if (action.launchMode == MediaLaunchMode.BACKGROUND_ONLY) {
                        return finish(session, MediaExecutionResult.Failed(BACKGROUND_ONLY_FAILURE))
                    }
                }
                session.finishOperation(active.id, MediaOperationStatus.COMPLETED)
                continue
            }
            val operation =
                session.nextOperation()
                    ?: return finish(
                        session,
                        if (action.launchMode == MediaLaunchMode.BACKGROUND_ONLY) {
                            MediaExecutionResult.Failed(BACKGROUND_ONLY_FAILURE)
                        } else {
                            MediaExecutionResult.TimedOut("Aucune opération média restante.")
                        },
                    )
            if (!session.startOperation(operation.id)) return finish(session, MediaExecutionResult.Failed("Opération média déjà exécutée."))
            context.logger.log(
                "PLAY_MEDIA_OPERATION_STARTED",
                mapOf(
                    "nodeId" to context.nodeId.value,
                    "operationId" to operation.id,
                    "operationType" to operation.type.name,
                    "launchMode" to action.launchMode.name,
                ),
            )
            if (
                operation.type == MediaOperationType.PROVIDER_SEARCH ||
                (operation.type == MediaOperationType.DIRECT_URI && action.launchMode == MediaLaunchMode.OPEN_PLAYER)
            ) {
                informPlayerOpening(action, context, directFailureReason)
            }
            val attempted = attempt(session, operation, action, context, observer)
            context.logger.log(
                "PLAY_MEDIA_OPERATION_RESULT",
                mapOf("nodeId" to context.nodeId.value, "operationId" to operation.id, "result" to attempted::class.simpleName),
            )
            when (attempted) {
                is Attempt.UserLaunchRequired -> {
                    val checkpoint =
                        session.suspendForUser(operation.id)
                            ?: return finish(session, MediaExecutionResult.Failed("Une continuation identique a déjà été consommée."))
                    return ActionResult.UserActionRequired(attempted.reason, checkpoint.toWorkflowCheckpoint())
                }

                is Attempt.Failed -> {
                    if (operation.type == MediaOperationType.MEDIA_SESSION) {
                        directFailureReason = attempted.reason
                        context.logger.log(
                            "PLAY_MEDIA_DIRECT_FAILED",
                            mapOf("nodeId" to context.nodeId.value, "reason" to attempted.reason.name),
                        )
                        if (action.launchMode == MediaLaunchMode.BACKGROUND_ONLY) {
                            context.logger.log(
                                "PLAY_MEDIA_BACKGROUND_ONLY_FAILED",
                                mapOf("nodeId" to context.nodeId.value, "reason" to attempted.reason.name),
                            )
                            return finish(session, MediaExecutionResult.Failed(BACKGROUND_ONLY_FAILURE))
                        }
                    }
                    session.finishOperation(operation.id, MediaOperationStatus.FAILED)
                }

                Attempt.Opened -> {
                    if (operation.type == MediaOperationType.MANUAL_ASSISTANCE) {
                        session.finishOperation(operation.id, MediaOperationStatus.AWAITING_OUTCOME)
                        if (session.markManualGuidanceShown()) guidance.show(action, context)
                        return finish(session, outcome.await())
                    }
                    session.finishOperation(operation.id, MediaOperationStatus.AWAITING_OUTCOME)
                    val observed = awaitAutomaticOutcome(session, outcome)
                    if (observed != null) {
                        logObservedOutcome(context, observed)
                        return finish(session, observed)
                    }
                    context.logger.log(
                        "PLAY_MEDIA_OUTCOME_TIMEOUT",
                        mapOf("nodeId" to context.nodeId.value, "operationType" to operation.type.name),
                    )
                    if (operation.type == MediaOperationType.MEDIA_SESSION) {
                        directFailureReason = DirectFailureReason.PLAYBACK_NOT_CONFIRMED
                        if (action.launchMode == MediaLaunchMode.BACKGROUND_ONLY) {
                            return finish(session, MediaExecutionResult.Failed(BACKGROUND_ONLY_FAILURE))
                        }
                    }
                    session.finishOperation(operation.id, MediaOperationStatus.COMPLETED)
                }
            }
        }
        return finish(session, MediaExecutionResult.TimedOut("La lecture multimédia a expiré."))
    }

    private suspend fun resumeUserLaunch(
        session: MediaExecutionSession,
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
        outcome: kotlinx.coroutines.Deferred<MediaObservedOutcome>,
        observer: MediaOutcomeObserver,
    ): ActionResult {
        outcome.getCompletedOrNull()?.let { return finish(session, it) }
        if (!context.userInitiated || !session.consumeContinuation()) {
            return finish(session, MediaExecutionResult.Failed("La continuation PLAY_MEDIA a déjà été consommée."))
        }
        val operation =
            session.currentOperation()
                ?: return finish(session, MediaExecutionResult.Failed("L’opération média à reprendre est absente."))
        return when (val attempted = attempt(session, operation, action, context, observer)) {
            is Attempt.UserLaunchRequired -> {
                finish(
                    session,
                    MediaExecutionResult.Failed("Android bloque toujours le même lancement après validation utilisateur."),
                )
            }

            is Attempt.Failed -> {
                finish(session, MediaExecutionResult.Failed("Le lancement média repris a échoué."))
            }

            Attempt.Opened -> {
                session.finishOperation(operation.id, MediaOperationStatus.AWAITING_OUTCOME)
                executePlan(session, action, context, outcome, observer)
            }
        }
    }

    private suspend fun awaitAutomaticOutcome(
        session: MediaExecutionSession,
        outcome: kotlinx.coroutines.Deferred<MediaObservedOutcome>,
    ): MediaObservedOutcome? {
        val remaining =
            minOf(
                session.automaticDeadlineMillis - nowMillis(),
                OPERATION_RESPONSE_MILLIS,
            ).coerceAtLeast(0)
        return if (remaining == 0L) outcome.getCompletedOrNull() else withTimeoutOrNull(remaining) { outcome.await() }
    }

    private fun planMatchesLaunchMode(
        plan: MediaExecutionPlan,
        mode: MediaLaunchMode,
    ): Boolean =
        when (mode) {
            MediaLaunchMode.AUTOMATIC -> {
                true
            }

            MediaLaunchMode.BACKGROUND_ONLY -> {
                plan.operations.all { it.type == MediaOperationType.MEDIA_SESSION }
            }

            MediaLaunchMode.OPEN_PLAYER -> {
                plan.operations.none { it.type == MediaOperationType.MEDIA_SESSION }
            }
        }

    private fun buildPlan(action: ShortcutAction.PlayMedia): MediaExecutionPlan =
        MediaExecutionPlan(
            when (action.launchMode) {
                MediaLaunchMode.AUTOMATIC -> {
                    buildList {
                        if (!action.mediaUri.isNullOrBlank()) add(MediaOperation("direct_uri", MediaOperationType.DIRECT_URI, true))
                        add(MediaOperation("media_session", MediaOperationType.MEDIA_SESSION, true))
                        add(MediaOperation("provider_search", MediaOperationType.PROVIDER_SEARCH, true))
                        if (action.allowManualFallback) {
                            add(
                                MediaOperation("manual_assistance", MediaOperationType.MANUAL_ASSISTANCE, false),
                            )
                        }
                    }
                }

                MediaLaunchMode.BACKGROUND_ONLY -> {
                    listOf(MediaOperation("media_session", MediaOperationType.MEDIA_SESSION, true))
                }

                MediaLaunchMode.OPEN_PLAYER -> {
                    buildList {
                        // URI actions have no query for provider search. Reuse the existing
                        // activity-opening URI operation without ever sending a media command.
                        if (!action.mediaUri.isNullOrBlank()) {
                            add(MediaOperation("direct_uri", MediaOperationType.DIRECT_URI, true))
                        } else {
                            add(MediaOperation("provider_search", MediaOperationType.PROVIDER_SEARCH, true))
                        }
                        if (action.allowManualFallback) {
                            add(
                                MediaOperation("manual_assistance", MediaOperationType.MANUAL_ASSISTANCE, false),
                            )
                        }
                    }
                }
            },
        )

    private suspend fun attempt(
        session: MediaExecutionSession,
        operation: MediaOperation,
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
        observer: MediaOutcomeObserver,
    ): Attempt =
        when (operation.type) {
            MediaOperationType.DIRECT_URI -> {
                observer.capturePreDispatchState()
                launch(adapter.buildDirectContentIntent(action.request()), action.targetAppLabel, context).also {
                    if (it is Attempt.Opened) observer.onOperationDispatched(null)
                }
            }

            MediaOperationType.MEDIA_SESSION -> {
                context.logger.log(
                    "PLAY_MEDIA_DIRECT_ATTEMPT",
                    mapOf("nodeId" to context.nodeId.value, "launchMode" to action.launchMode.name),
                )
                observer.capturePreDispatchState()
                when (val command = commands.request(action)) {
                    is MediaSessionCommandResult.Sent -> {
                        context.logger.log(
                            "PLAY_MEDIA_DIRECT_COMMAND_SENT",
                            mapOf("nodeId" to context.nodeId.value, "sessionId" to command.sessionId),
                        )
                        session.recordCommandedSession(operation.id, command.sessionId)
                        observer.onOperationDispatched(command.sessionId)
                        Attempt.Opened
                    }

                    is MediaSessionCommandResult.NotSupported -> {
                        Attempt.Failed(command.reason.toDirectFailureReason())
                    }

                    is MediaSessionCommandResult.Failed -> {
                        Attempt.Failed(command.reason.toDirectFailureReason())
                    }
                }
            }

            MediaOperationType.PROVIDER_SEARCH -> {
                context.logger.log(
                    if (action.launchMode ==
                        MediaLaunchMode.OPEN_PLAYER
                    ) {
                        "PLAY_MEDIA_OPEN_PLAYER_REQUESTED"
                    } else {
                        "PLAY_MEDIA_PROVIDER_SEARCH_FALLBACK"
                    },
                    mapOf(
                        "nodeId" to context.nodeId.value,
                        "targetPackage" to action.targetPackage,
                        "launchMode" to action.launchMode.name,
                    ),
                )
                observer.capturePreDispatchState()
                launch(adapter.buildSearchIntent(action.request()), action.targetAppLabel, context).also {
                    if (it is Attempt.Opened) observer.onOperationDispatched(null)
                }
            }

            MediaOperationType.MANUAL_ASSISTANCE -> {
                Attempt.Opened
            }

            MediaOperationType.PROVIDER_AUTOMATION -> {
                Attempt.Failed(DirectFailureReason.COMMAND_ERROR)
            }
        }

    private fun informPlayerOpening(
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
        directFailure: DirectFailureReason?,
    ) {
        val message =
            if (action.launchMode == MediaLaunchMode.OPEN_PLAYER) {
                "Ouverture du lecteur demandée."
            } else {
                "Ouverture du lecteur : ${directFailure?.message ?: "lecture non confirmée"}."
            }
        guidance.showInfo(message, context)
        context.logger.log(
            "PLAY_MEDIA_PLAYER_OPENING",
            mapOf(
                "nodeId" to context.nodeId.value,
                "launchMode" to action.launchMode.name,
                "reason" to (directFailure?.name ?: "USER_SELECTED_OPEN_PLAYER"),
            ),
        )
    }

    private fun String.toDirectFailureReason() =
        when {
            contains("Aucune session", ignoreCase = true) -> DirectFailureReason.NO_TARGET_SESSION
            contains("compatible", ignoreCase = true) || contains("support", ignoreCase = true) -> DirectFailureReason.COMMAND_NOT_SUPPORTED
            contains("refus", ignoreCase = true) || contains("rejet", ignoreCase = true) -> DirectFailureReason.COMMAND_REJECTED
            else -> DirectFailureReason.COMMAND_ERROR
        }

    private fun logObservedOutcome(
        context: ActionExecutionContext,
        outcome: MediaObservedOutcome,
    ) {
        if (outcome is MediaObservedOutcome.PlaybackStarted) {
            context.logger.log(
                "PLAY_MEDIA_PLAYING_OBSERVED",
                mapOf(
                    "nodeId" to context.nodeId.value,
                    "sessionId" to outcome.sessionId,
                    "contentConfirmed" to outcome.contentConfirmed,
                    "proof" to outcome.proof,
                ),
            )
        }
    }

    private suspend fun launch(
        intent: android.content.Intent?,
        label: String,
        context: ActionExecutionContext,
    ): Attempt {
        if (intent == null) return Attempt.Failed(DirectFailureReason.COMMAND_ERROR)
        return when (val result = launcher.launch(intent, label, context)) {
            ActionResult.Completed -> Attempt.Opened
            is ActionResult.UserActionRequired -> Attempt.UserLaunchRequired(result.reason)
            else -> Attempt.Failed(DirectFailureReason.COMMAND_ERROR)
        }
    }

    private fun decodeCheckpoint(
        workflow: ActionWorkflowCheckpoint,
        context: ActionExecutionContext,
    ): MediaExecutionCheckpoint? {
        if (workflow.stateKey != CHECKPOINT_STATE || workflow.actionKind != ActionKind.PLAY_MEDIA ||
            workflow.executionId != context.executionId || workflow.actionId != context.nodeId || workflow.routineId != context.routineId
        ) {
            return null
        }
        val checkpoint = workflow.payload[CHECKPOINT_PAYLOAD]?.let(MediaExecutionCheckpointCodec::decode) ?: return null
        return checkpoint.takeIf {
            it.executionId == context.executionId && it.nodeId == context.nodeId && it.routineId == context.routineId &&
                it.state in RESTORABLE_STATES && it.operationId != null
        }
    }

    private fun MediaExecutionCheckpoint.toWorkflowCheckpoint(): ActionWorkflowCheckpoint =
        ActionWorkflowCheckpoint(
            actionId = nodeId,
            executionId = executionId,
            routineId = routineId,
            actionKind = ActionKind.PLAY_MEDIA,
            stateKey = CHECKPOINT_STATE,
            payload = mapOf(CHECKPOINT_PAYLOAD to MediaExecutionCheckpointCodec.encode(this)),
            startedAtMillis = startedAtMillis,
            expiresAtMillis = globalDeadlineMillis,
            version = MediaExecutionCheckpointCodec.VERSION,
        )

    private fun finish(
        session: MediaExecutionSession,
        outcome: Any,
    ): ActionResult {
        val result =
            when (outcome) {
                is MediaObservedOutcome.PlaybackStarted -> {
                    MediaExecutionResult.Completed(
                        if (outcome.contentConfirmed) {
                            MediaConfirmationLevel.PLAYBACK_AND_CONTENT_CONFIRMED
                        } else {
                            MediaConfirmationLevel.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED
                        },
                    )
                }

                is MediaObservedOutcome.Unavailable -> {
                    MediaExecutionResult.Failed(outcome.reason)
                }

                MediaObservedOutcome.TimedOut -> {
                    MediaExecutionResult.TimedOut("La lecture multimédia a expiré.")
                }

                is MediaExecutionResult -> {
                    outcome
                }

                else -> {
                    MediaExecutionResult.Failed("Résultat média invalide.")
                }
            }
        session.terminate(result)
        return when (result) {
            is MediaExecutionResult.Completed -> ActionResult.Completed
            is MediaExecutionResult.Failed -> ActionResult.Failed(result.reason, result.recoverable)
            is MediaExecutionResult.Cancelled -> ActionResult.Cancelled(result.reason)
            is MediaExecutionResult.TimedOut -> ActionResult.TimedOut(result.reason)
            is MediaExecutionResult.UserLaunchRequired -> ActionResult.Failed("Résultat de suspension média inattendu.")
        }
    }

    private sealed interface Attempt {
        data object Opened : Attempt

        data class Failed(
            val reason: DirectFailureReason,
        ) : Attempt

        data class UserLaunchRequired(
            val reason: String,
        ) : Attempt
    }

    private enum class DirectFailureReason(
        val message: String,
    ) {
        NO_TARGET_SESSION("aucune session compatible"),
        COMMAND_NOT_SUPPORTED("commande directe non prise en charge"),
        COMMAND_REJECTED("commande multimédia refusée"),
        COMMAND_ERROR("erreur de commande directe"),
        PLAYBACK_NOT_CONFIRMED("lecture non confirmée"),
    }

    private companion object {
        const val BACKGROUND_ONLY_FAILURE = "La lecture en arrière-plan n’est pas disponible pour ce lecteur dans l’état actuel."
        const val AUTOMATIC_BUDGET_MILLIS = 20_000L
        const val OPERATION_RESPONSE_MILLIS = 4_000L
        const val CHECKPOINT_STATE = "media_execution_v3"
        const val CHECKPOINT_PAYLOAD = "checkpoint"
        val RESTORABLE_STATES =
            setOf(
                MediaExecutionState.EXECUTE_OPERATION,
                MediaExecutionState.AWAIT_OUTCOME,
                MediaExecutionState.AWAIT_USER_LAUNCH,
                MediaExecutionState.AWAIT_MANUAL_PLAY,
            )
    }
}

private fun ShortcutAction.PlayMedia.request() = MediaOpenRequest(AppTarget(targetPackage, activityName), searchQuery, mediaUri)

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> kotlinx.coroutines.Deferred<T>.getCompletedOrNull(): T? =
    if (isCompleted && !isCancelled) runCatching { getCompleted() }.getOrNull() else null
