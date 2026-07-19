package com.branlly.pocket.platform.android

import android.content.Context
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionRegistry
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ErrorStrategy
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidExecutionLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Generic ordered engine. It knows handlers and results, never providers or Android action details. */
class ShortcutExecutor(
    private val registry: ActionRegistry,
    private val logger: ExecutionLogger = ExecutionLogger { _, _ -> },
) {
    constructor(
        context: Context,
        registry: ActionRegistry = AndroidActionRegistry.create(context.applicationContext),
    ) : this(registry, AndroidExecutionLogger(context.packageName))
    suspend fun execute(
        shortcut: ShortcutDefinition,
        executionId: String = UUID.randomUUID().toString(),
        startIndex: Int = 0,
        userInitiatedNodeId: NodeId? = null,
    ): RoutineExecutionResult {
        logger.log(
            "ROUTINE_START",
            mapOf(
                "executionId" to executionId,
                "routineId" to shortcut.id.value,
                "routineName" to shortcut.name,
                "nodeCount" to shortcut.nodes.size,
            ),
        )
        require(startIndex in 0..shortcut.nodes.size) { "Invalid start index $startIndex" }
        for (index in startIndex until shortcut.nodes.size) {
            val node = shortcut.nodes[index]
            if (!node.enabled) {
                logger.log("NODE_SKIPPED_DISABLED", fields(executionId, shortcut, node, index))
                continue
            }
            logger.log("ACTION_START", fields(executionId, shortcut, node, index))
            val isResumedNode = index == startIndex && userInitiatedNodeId == node.id
            if (node.delayBeforeMillis > 0 && !isResumedNode) delay(node.delayBeforeMillis)
            val result = try {
                executeWithStrategy(shortcut, node, executionId, isResumedNode)
            } catch (_: CancellationException) {
                ActionResult.Cancelled()
            } catch (error: Throwable) {
                ActionResult.Failed(error.message ?: "Erreur Android inattendue.")
            }
            logger.log(
                "ACTION_RESULT",
                fields(executionId, shortcut, node, index) + mapOf("result" to result::class.simpleName, "detail" to result),
            )
            if (result is ActionResult.UserActionRequired) {
                logger.log("ROUTINE_WAITING_USER_ACTION", fields(executionId, shortcut, node, index) + mapOf("result" to result))
                return RoutineExecutionResult.WaitingUserAction(node.id, index, node.action.kind, result.reason)
            }
            if (result !is ActionResult.Completed) {
                val canContinue = node.errorStrategy is ErrorStrategy.Continue &&
                    (result is ActionResult.Failed || result is ActionResult.TimedOut)
                if (!canContinue) {
                    logger.log("ROUTINE_STOPPED", fields(executionId, shortcut, node, index) + mapOf("result" to result))
                    return RoutineExecutionResult.Stopped(node.id, result)
                }
            }
        }
        logger.log("ROUTINE_COMPLETED", mapOf("executionId" to executionId, "routineId" to shortcut.id.value))
        return RoutineExecutionResult.Completed
    }

    private suspend fun executeWithStrategy(
        shortcut: ShortcutDefinition,
        node: ActionNode,
        executionId: String,
        userInitiated: Boolean,
    ): ActionResult {
        suspend fun attempt(): ActionResult {
            val context = ActionExecutionContext(executionId, shortcut.id, node.id, logger, userInitiated)
            return if (node.timeoutMillis == null) {
                registry.execute(node.action, context)
            } else {
                withTimeoutOrNull(node.timeoutMillis) { registry.execute(node.action, context) }
                    ?: ActionResult.TimedOut()
            }
        }

        var result = attempt()
        val retry = node.errorStrategy as? ErrorStrategy.Retry ?: return result
        repeat(retry.attempts - 1) {
            if (result is ActionResult.Completed || result is ActionResult.Cancelled ||
                result is ActionResult.UserActionRequired || result is ActionResult.PermissionRequired
            ) {
                return result
            }
            if (retry.delayMillis > 0) delay(retry.delayMillis)
            result = attempt()
        }
        return result
    }

    private fun fields(
        executionId: String,
        shortcut: ShortcutDefinition,
        node: ActionNode,
        index: Int,
    ): Map<String, Any?> =
        mapOf(
            "executionId" to executionId,
            "routineId" to shortcut.id.value,
            "nodeId" to node.id.value,
            "index" to index,
            "kind" to node.action.kind,
        )
}

sealed interface RoutineExecutionResult {
    data object Completed : RoutineExecutionResult

    data class Stopped(
        val nodeId: NodeId,
        val result: ActionResult,
    ) : RoutineExecutionResult

    data class WaitingUserAction(
        val nodeId: NodeId,
        val nodeIndex: Int,
        val actionKind: ActionKind,
        val reason: String,
        val continuationId: String? = null,
    ) : RoutineExecutionResult

    data class ValidationFailed(
        val messages: List<String>,
    ) : RoutineExecutionResult

    data class Cancelled(val reason: String) : RoutineExecutionResult

    data class ContinuationRejected(val reason: String) : RoutineExecutionResult

    data object AlreadyRunning : RoutineExecutionResult
}
