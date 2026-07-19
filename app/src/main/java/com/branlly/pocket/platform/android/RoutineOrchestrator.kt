package com.branlly.pocket.platform.android

import android.content.Context
import com.branlly.pocket.data.ActionJsonCodecRegistry
import com.branlly.pocket.data.PersistentRoutineExecutionStateStore
import com.branlly.pocket.domain.execution.ContinuationClaim
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.execution.RoutineContinuation
import com.branlly.pocket.domain.execution.RoutineValidator
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
import org.json.JSONObject
import java.util.UUID

/** Single persistent entry point for new runs, notification resumes, cancellation and expiration. */
object RoutineOrchestrator {
    const val DEFAULT_CONTINUATION_TTL_MILLIS = 10 * 60 * 1_000L
    private const val MAX_ACTIVE_EXECUTION_MILLIS = 24 * 60 * 60 * 1_000L

    suspend fun execute(
        context: Context,
        routine: ShortcutDefinition,
        executionId: String,
        continuationTtlMillis: Long = DEFAULT_CONTINUATION_TTL_MILLIS,
    ): RoutineExecutionResult {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val store = PersistentRoutineExecutionStateStore(appContext)
        if (!store.begin(executionId, routine.id, now + MAX_ACTIVE_EXECUTION_MILLIS, now)) {
            return RoutineExecutionResult.AlreadyRunning
        }
        return executeClaimed(appContext, routine, executionId, 0, null, null, continuationTtlMillis)
    }

    suspend fun resume(
        context: Context,
        identity: ContinuationIdentity,
        continuationTtlMillis: Long = DEFAULT_CONTINUATION_TTL_MILLIS,
    ): RoutineExecutionResult {
        val appContext = context.applicationContext
        val store = PersistentRoutineExecutionStateStore(appContext)
        val notifications = AndroidContinuationNotificationGateway(appContext)
        return when (val claim = store.claim(identity, System.currentTimeMillis())) {
            is ContinuationClaim.Claimed -> {
                notifications.dismiss(identity.continuationId)
                val continuation = claim.continuation
                if (!isContinuationConsistent(continuation)) {
                    store.finish(continuation.executionId)
                    notifications.showMessage("Cette continuation ne correspond plus à la routine.")
                    RoutineExecutionResult.ContinuationRejected("Continuation incohérente.")
                } else {
                    executeClaimed(
                        appContext,
                        continuation.routineSnapshot,
                        continuation.executionId,
                        continuation.nodeIndex,
                        continuation.nodeId,
                        continuation.continuationId,
                        continuationTtlMillis,
                    )
                }
            }
            ContinuationClaim.Expired -> reject(notifications, identity, "Cette continuation a expiré.")
            ContinuationClaim.AlreadyConsumed -> reject(notifications, identity, "Cette continuation a déjà été utilisée.")
            ContinuationClaim.Mismatch -> reject(notifications, identity, "Cette notification ne correspond pas à l’exécution active.")
            ContinuationClaim.Missing -> reject(notifications, identity, "Cette continuation n’existe plus.")
        }
    }

    fun cancel(context: Context, identity: ContinuationIdentity, expired: Boolean = false): RoutineExecutionResult {
        val appContext = context.applicationContext
        val store = PersistentRoutineExecutionStateStore(appContext)
        val notifications = AndroidContinuationNotificationGateway(appContext)
        val claim = store.cancel(identity, if (expired) Long.MAX_VALUE else System.currentTimeMillis())
        notifications.remove(identity.continuationId)
        return when (claim) {
            is ContinuationClaim.Claimed -> {
                notifications.showMessage(if (expired) "La continuation a expiré." else "Routine annulée.")
                RoutineExecutionResult.Cancelled(if (expired) "Continuation expirée." else "Annulation utilisateur.")
            }
            ContinuationClaim.Expired -> {
                notifications.showMessage("La continuation a expiré.")
                RoutineExecutionResult.Cancelled("Continuation expirée.")
            }
            ContinuationClaim.AlreadyConsumed -> RoutineExecutionResult.ContinuationRejected("Continuation déjà consommée.")
            ContinuationClaim.Mismatch -> RoutineExecutionResult.ContinuationRejected("Identité de continuation incorrecte.")
            ContinuationClaim.Missing -> RoutineExecutionResult.ContinuationRejected("Continuation absente.")
        }
    }

    private suspend fun executeClaimed(
        context: Context,
        routine: ShortcutDefinition,
        executionId: String,
        startIndex: Int,
        userInitiatedNodeId: com.branlly.pocket.domain.model.NodeId?,
        claimedContinuationId: String?,
        continuationTtlMillis: Long,
    ): RoutineExecutionResult {
        val store = PersistentRoutineExecutionStateStore(context)
        val registry = AndroidActionRegistry.create(context)
        val issues = RoutineValidator(registry, AndroidActionValidationContext(context)).validate(routine)
        if (issues.isNotEmpty()) {
            store.finish(executionId)
            return RoutineExecutionResult.ValidationFailed(issues.map { it.message })
        }
        val result = ShortcutExecutor(context, registry).execute(routine, executionId, startIndex, userInitiatedNodeId)
        if (result is RoutineExecutionResult.WaitingUserAction) {
            val now = System.currentTimeMillis()
            val node = routine.nodes[result.nodeIndex]
            val continuation = RoutineContinuation(
                continuationId = UUID.randomUUID().toString(),
                executionId = executionId,
                routineId = routine.id,
                nodeId = result.nodeId,
                nodeIndex = result.nodeIndex,
                actionKind = result.actionKind,
                actionParameters = ActionJsonCodecRegistry.DEFAULT.encode(node.action).toString(),
                routineSnapshot = routine,
                createdAtMillis = now,
                expiresAtMillis = now + continuationTtlMillis,
            )
            if (!store.waitForUser(continuation)) {
                store.finish(executionId)
                claimedContinuationId?.let { AndroidContinuationNotificationGateway(context).remove(it) }
                return RoutineExecutionResult.Stopped(result.nodeId, com.branlly.pocket.domain.execution.ActionResult.Failed("Impossible de persister la continuation."))
            }
            val notifications = AndroidContinuationNotificationGateway(context)
            claimedContinuationId?.let(notifications::remove)
            notifications.post(continuation, result.reason)
            return result.copy(continuationId = continuation.continuationId)
        }
        store.finish(executionId)
        claimedContinuationId?.let { AndroidContinuationNotificationGateway(context).remove(it) }
        return result
    }

    private fun reject(
        notifications: AndroidContinuationNotificationGateway,
        identity: ContinuationIdentity,
        message: String,
    ): RoutineExecutionResult.ContinuationRejected {
        notifications.remove(identity.continuationId)
        notifications.showMessage(message)
        return RoutineExecutionResult.ContinuationRejected(message)
    }
}

internal fun isContinuationConsistent(continuation: RoutineContinuation): Boolean {
    val routine = continuation.routineSnapshot
    if (routine.id != continuation.routineId || continuation.nodeIndex !in routine.nodes.indices) return false
    val node = routine.nodes[continuation.nodeIndex]
    if (node.id != continuation.nodeId || node.action.kind != continuation.actionKind) return false
    return runCatching {
        ActionJsonCodecRegistry.DEFAULT.decode(JSONObject(continuation.actionParameters)) == node.action
    }.getOrDefault(false)
}
