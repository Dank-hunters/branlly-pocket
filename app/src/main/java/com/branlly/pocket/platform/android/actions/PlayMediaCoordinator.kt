package com.branlly.pocket.platform.android.actions

import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.media.MediaConfirmationLevel
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionResult
import com.branlly.pocket.domain.media.MediaExecutionSession
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaOperation
import com.branlly.pocket.domain.media.MediaOperationStatus
import com.branlly.pocket.domain.media.MediaOperationType
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.model.ShortcutAction
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
    private val observerFactory: (String) -> MediaOutcomeObserver,
    private val guidance: ManualMediaGuidance,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
    ): ActionResult =
        coroutineScope {
            val observer = observerFactory(action.targetPackage)
            val deadline = nowMillis() + action.timeoutMs
            val plan =
                MediaExecutionPlan(
                    buildList {
                        if (!action.mediaUri.isNullOrBlank()) add(MediaOperation("direct_uri", MediaOperationType.DIRECT_URI, true))
                        add(MediaOperation("media_session", MediaOperationType.MEDIA_SESSION, true))
                        add(MediaOperation("provider_search", MediaOperationType.PROVIDER_SEARCH, true))
                        if (action.allowManualFallback) {
                            add(
                                MediaOperation("manual_assistance", MediaOperationType.MANUAL_ASSISTANCE, false),
                            )
                        }
                    },
                )
            val session =
                MediaExecutionSession(
                    executionId = context.executionId,
                    routineId = context.routineId,
                    nodeId = context.nodeId,
                    targetPackage = action.targetPackage,
                    searchQuery = action.searchQuery,
                    mediaUri = action.mediaUri,
                    selectionPolicy = action.selectionPolicy,
                    baseline = observer.baseline,
                    plan = plan,
                    automaticDeadlineMillis = minOf(deadline, nowMillis() + AUTOMATIC_BUDGET_MILLIS),
                    globalDeadlineMillis = deadline,
                )
            // UNDISTPATCHED registers MediaSession callbacks before any operation launches an activity.
            val outcome = async(start = CoroutineStart.UNDISPATCHED) { observer.awaitOutcome((deadline - nowMillis()).coerceAtLeast(1)) }
            try {
                session.move(com.branlly.pocket.domain.media.MediaExecutionState.AWAIT_OUTCOME)
                while (nowMillis() < deadline) {
                    outcome.getCompletedOrNull()?.let { return@coroutineScope finish(session, it) }
                    val operation =
                        session.nextOperation()
                            ?: return@coroutineScope finish(session, MediaExecutionResult.TimedOut("Aucune opération média restante."))
                    session.startOperation(operation.id)
                    when (val attempted = attempt(operation, action, context)) {
                        is Attempt.UserLaunchRequired -> {
                            session.finishOperation(operation.id, MediaOperationStatus.FAILED)
                            return@coroutineScope finish(
                                session,
                                MediaExecutionResult.UserLaunchRequired(attempted.reason, session.checkpoint()),
                            )
                        }

                        is Attempt.Failed -> {
                            session.finishOperation(operation.id, MediaOperationStatus.FAILED)
                        }

                        Attempt.Opened -> {
                            session.finishOperation(operation.id, MediaOperationStatus.COMPLETED)
                            if (operation.type == MediaOperationType.MANUAL_ASSISTANCE) {
                                if (session.markManualGuidanceShown()) guidance.show(action, context)
                                return@coroutineScope finish(session, outcome.await())
                            }
                            val remainingAutomatic = (session.automaticDeadlineMillis - nowMillis()).coerceAtLeast(0)
                            val observed = withTimeoutOrNull(remainingAutomatic) { outcome.await() }
                            if (observed != null) return@coroutineScope finish(session, observed)
                        }
                    }
                }
                finish(session, MediaExecutionResult.TimedOut("La lecture multimédia a expiré."))
            } finally {
                outcome.cancel()
                observer.close()
                guidance.clear()
            }
        }

    private suspend fun attempt(
        operation: MediaOperation,
        action: ShortcutAction.PlayMedia,
        context: ActionExecutionContext,
    ): Attempt =
        when (operation.type) {
            MediaOperationType.DIRECT_URI -> {
                launch(adapter.buildDirectContentIntent(action.request()), action.targetAppLabel, context)
            }

            MediaOperationType.MEDIA_SESSION -> {
                when (val result = commands.request(action)) {
                    is MediaSessionCommandResult.Sent -> Attempt.Opened
                    is MediaSessionCommandResult.NotSupported, is MediaSessionCommandResult.Failed -> Attempt.Failed
                }
            }

            MediaOperationType.PROVIDER_SEARCH -> {
                launch(adapter.buildSearchIntent(action.request()), action.targetAppLabel, context)
            }

            MediaOperationType.MANUAL_ASSISTANCE -> {
                Attempt.Opened
            }

            MediaOperationType.PROVIDER_AUTOMATION -> {
                Attempt.Failed
            }
        }

    private suspend fun launch(
        intent: android.content.Intent?,
        label: String,
        context: ActionExecutionContext,
    ): Attempt {
        if (intent == null) return Attempt.Failed
        return when (val result = launcher.launch(intent, label, context)) {
            ActionResult.Completed -> Attempt.Opened
            is ActionResult.UserActionRequired -> Attempt.UserLaunchRequired(result.reason)
            else -> Attempt.Failed
        }
    }

    private fun finish(
        session: MediaExecutionSession,
        outcome: Any,
    ): ActionResult {
        val result =
            when (outcome) {
                is MediaObservedOutcome.PlaybackStarted -> {
                    MediaExecutionResult.Completed(
                        if (outcome.contentConfirmed) MediaConfirmationLevel.PLAYBACK_AND_CONTENT_CONFIRMED else MediaConfirmationLevel.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED,
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
            is MediaExecutionResult.UserLaunchRequired -> ActionResult.UserActionRequired(result.reason, null)
        }
    }

    private sealed interface Attempt {
        data object Opened : Attempt

        data object Failed : Attempt

        data class UserLaunchRequired(
            val reason: String,
        ) : Attempt
    }

    private companion object {
        const val AUTOMATIC_BUDGET_MILLIS = 20_000L
    }
}

private fun ShortcutAction.PlayMedia.request() = MediaOpenRequest(AppTarget(targetPackage, activityName), searchQuery, mediaUri)

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> kotlinx.coroutines.Deferred<T>.getCompletedOrNull(): T? =
    if (isCompleted && !isCancelled) runCatching { getCompleted() }.getOrNull() else null
