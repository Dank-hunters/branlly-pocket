package com.branlly.pocket.domain.workflow

import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import kotlinx.coroutines.withTimeoutOrNull

/** Stable, serializable identifier for one internal workflow step. */
interface ActionWorkflowState {
    val key: String
}

sealed interface ActionProgress {
    data class Resolving(val message: String) : ActionProgress
    data class OpeningProvider(val label: String) : ActionProgress
    data class Searching(val query: String) : ActionProgress
    data class SelectingResult(val description: String) : ActionProgress
    data class WaitingForPlayback(val packageName: String) : ActionProgress
    data class WaitingForSystemConfirmation(val description: String) : ActionProgress
    data class WaitingForUser(val description: String) : ActionProgress
}

data class ActionWorkflowCheckpoint(
    val actionId: NodeId,
    val executionId: String,
    val routineId: ShortcutId,
    val actionKind: ActionKind,
    val stateKey: String,
    val payload: Map<String, String>,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val version: Int = 1,
)

fun interface WorkflowCheckpointSink {
    suspend fun persist(checkpoint: ActionWorkflowCheckpoint)
}

fun interface ActionProgressSink {
    fun emit(progress: ActionProgress)
}

data class ActionWorkflowContext(
    val actionId: NodeId,
    val executionId: String,
    val routineId: ShortcutId,
    val actionKind: ActionKind,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val logger: ExecutionLogger,
    val progressSink: ActionProgressSink = ActionProgressSink {},
    val checkpointSink: WorkflowCheckpointSink = WorkflowCheckpointSink {},
)

sealed interface ActionWorkflowStep<out S : ActionWorkflowState> {
    data class ContinueInternally<S : ActionWorkflowState>(
        val nextState: S,
        val progress: ActionProgress? = null,
        val checkpoint: ActionWorkflowCheckpoint? = null,
    ) : ActionWorkflowStep<S>

    data object Completed : ActionWorkflowStep<Nothing>
    data class Failed(val reason: String, val recoverable: Boolean = false) : ActionWorkflowStep<Nothing>
    data class TimedOut(val reason: String) : ActionWorkflowStep<Nothing>
    data class Cancelled(val reason: String) : ActionWorkflowStep<Nothing>
    data class UserActionRequired(val reason: String, val checkpoint: ActionWorkflowCheckpoint) : ActionWorkflowStep<Nothing>
    data class PermissionRequired(val reason: String, val settingsAction: String? = null) : ActionWorkflowStep<Nothing>
}

interface ActionWorkflow<S : ActionWorkflowState> {
    val initialState: S

    suspend fun transition(state: S, context: ActionWorkflowContext): ActionWorkflowStep<S>
}

data class ActionWorkflowOutcome<S : ActionWorkflowState>(
    val result: ActionResult,
    val lastState: S,
    val transitionCount: Int,
)

/** Iterative and bounded executor: no recursion, no infinite retry and one strategy transition at a time. */
class BoundedActionWorkflowRunner(
    private val maxTransitions: Int = DEFAULT_MAX_TRANSITIONS,
    private val timeoutMillis: Long,
) {
    init {
        require(maxTransitions in 1..MAX_ALLOWED_TRANSITIONS)
        require(timeoutMillis in 100L..MAX_TIMEOUT_MILLIS)
    }

    suspend fun <S : ActionWorkflowState> run(
        workflow: ActionWorkflow<S>,
        context: ActionWorkflowContext,
        restoredState: S? = null,
    ): ActionWorkflowOutcome<S> {
        var state = restoredState ?: workflow.initialState
        var transitions = 0
        val result = withTimeoutOrNull(timeoutMillis) {
            repeat(maxTransitions) {
                transitions++
                context.logger.log(
                    "WORKFLOW_TRANSITION",
                    mapOf(
                        "executionId" to context.executionId,
                        "actionId" to context.actionId.value,
                        "kind" to context.actionKind,
                        "state" to state.key,
                        "transition" to transitions,
                    ),
                )
                when (val step = workflow.transition(state, context)) {
                    is ActionWorkflowStep.ContinueInternally -> {
                        step.progress?.let(context.progressSink::emit)
                        step.checkpoint?.let { context.checkpointSink.persist(it) }
                        state = step.nextState
                    }
                    ActionWorkflowStep.Completed -> return@withTimeoutOrNull ActionResult.Completed
                    is ActionWorkflowStep.Failed -> return@withTimeoutOrNull ActionResult.Failed(step.reason, step.recoverable)
                    is ActionWorkflowStep.TimedOut -> return@withTimeoutOrNull ActionResult.TimedOut(step.reason)
                    is ActionWorkflowStep.Cancelled -> return@withTimeoutOrNull ActionResult.Cancelled(step.reason)
                    is ActionWorkflowStep.UserActionRequired -> {
                        context.checkpointSink.persist(step.checkpoint)
                        return@withTimeoutOrNull ActionResult.UserActionRequired(step.reason, step.checkpoint)
                    }
                    is ActionWorkflowStep.PermissionRequired -> {
                        return@withTimeoutOrNull ActionResult.PermissionRequired(step.reason, step.settingsAction)
                    }
                }
            }
            ActionResult.Failed("Le workflow a dépassé son budget de $maxTransitions transitions.")
        } ?: ActionResult.TimedOut("Le workflow interne a expiré.")
        return ActionWorkflowOutcome(result, state, transitions)
    }

    companion object {
        const val DEFAULT_MAX_TRANSITIONS = 16
        const val MAX_ALLOWED_TRANSITIONS = 64
        const val MAX_TIMEOUT_MILLIS = 30 * 60 * 1_000L
    }
}

/** Pure resolver contract. Implementations calculate capabilities and never execute an action. */
fun interface CapabilityResolver<A, C> {
    fun resolve(action: A): C
}
