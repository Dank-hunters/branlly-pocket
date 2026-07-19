package com.branlly.pocket.domain.execution

import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId

enum class ExecutionStatus {
    RUNNING,
    WAITING_USER_ACTION,
}

data class RoutineContinuation(
    val continuationId: String,
    val executionId: String,
    val routineId: ShortcutId,
    val nodeId: NodeId,
    val nodeIndex: Int,
    val actionKind: ActionKind,
    /** Serialized independently so identity can be checked before rebuilding the handler call. */
    val actionParameters: String,
    /** Immutable routine snapshot containing the current and all remaining nodes. */
    val routineSnapshot: ShortcutDefinition,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)

data class ActiveExecution(
    val executionId: String,
    val routineId: ShortcutId,
    val status: ExecutionStatus,
    val expiresAtMillis: Long,
    val continuation: RoutineContinuation? = null,
)

data class ContinuationIdentity(
    val continuationId: String,
    val executionId: String,
    val routineId: ShortcutId,
    val nodeId: NodeId,
)

sealed interface ContinuationClaim {
    data class Claimed(val continuation: RoutineContinuation) : ContinuationClaim
    data object Missing : ContinuationClaim
    data object Expired : ContinuationClaim
    data object AlreadyConsumed : ContinuationClaim
    data object Mismatch : ContinuationClaim
}

interface RoutineExecutionStateStore {
    /** Atomically starts an execution if no non-expired execution is active. */
    fun begin(executionId: String, routineId: ShortcutId, expiresAtMillis: Long, nowMillis: Long): Boolean

    /** Atomically changes RUNNING to WAITING_USER_ACTION. */
    fun waitForUser(continuation: RoutineContinuation): Boolean

    /** Atomically claims WAITING_USER_ACTION and changes it back to RUNNING. */
    fun claim(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim

    /** Atomically cancels only the matching waiting continuation. */
    fun cancel(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim

    /** Clears only the matching active execution. */
    fun finish(executionId: String)

    fun active(nowMillis: Long): ActiveExecution?
}
