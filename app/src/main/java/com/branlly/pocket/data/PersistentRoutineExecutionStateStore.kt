package com.branlly.pocket.data

import android.content.Context
import com.branlly.pocket.domain.execution.ActiveExecution
import com.branlly.pocket.domain.execution.ContinuationClaim
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.execution.ExecutionStatus
import com.branlly.pocket.domain.execution.RoutineContinuation
import com.branlly.pocket.domain.execution.RoutineExecutionStateStore
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import org.json.JSONObject

/** One persistent active execution. Every transition is serialized by [lock] and committed synchronously. */
class PersistentRoutineExecutionStateStore(context: Context) : RoutineExecutionStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val shortcutCodec = SavedShortcutStore(context.applicationContext)

    override fun begin(executionId: String, routineId: ShortcutId, expiresAtMillis: Long, nowMillis: Long): Boolean =
        synchronized(lock) {
            val current = read()
            if (current != null && current.expiresAtMillis > nowMillis) return@synchronized false
            write(ActiveExecution(executionId, routineId, ExecutionStatus.RUNNING, expiresAtMillis))
            true
        }

    override fun waitForUser(continuation: RoutineContinuation): Boolean = synchronized(lock) {
        val current = read() ?: return@synchronized false
        if (current.executionId != continuation.executionId || current.routineId != continuation.routineId ||
            current.status != ExecutionStatus.RUNNING
        ) return@synchronized false
        write(current.copy(status = ExecutionStatus.WAITING_USER_ACTION, expiresAtMillis = continuation.expiresAtMillis, continuation = continuation))
        true
    }

    override fun claim(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim = synchronized(lock) {
        val current = read() ?: return@synchronized ContinuationClaim.Missing
        val continuation = current.continuation ?: return@synchronized ContinuationClaim.Missing
        if (!matches(continuation, identity)) return@synchronized ContinuationClaim.Mismatch
        if (continuation.expiresAtMillis <= nowMillis) {
            clear()
            return@synchronized ContinuationClaim.Expired
        }
        if (current.status != ExecutionStatus.WAITING_USER_ACTION) return@synchronized ContinuationClaim.AlreadyConsumed
        write(current.copy(status = ExecutionStatus.RUNNING))
        ContinuationClaim.Claimed(continuation)
    }

    override fun cancel(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim = synchronized(lock) {
        val current = read() ?: return@synchronized ContinuationClaim.Missing
        val continuation = current.continuation ?: return@synchronized ContinuationClaim.Missing
        if (!matches(continuation, identity)) return@synchronized ContinuationClaim.Mismatch
        if (continuation.expiresAtMillis <= nowMillis) {
            clear()
            return@synchronized ContinuationClaim.Expired
        }
        if (current.status != ExecutionStatus.WAITING_USER_ACTION) return@synchronized ContinuationClaim.AlreadyConsumed
        clear()
        ContinuationClaim.Claimed(continuation)
    }

    override fun finish(executionId: String) = synchronized(lock) {
        if (read()?.executionId == executionId) clear()
    }

    override fun active(nowMillis: Long): ActiveExecution? = synchronized(lock) {
        val current = read() ?: return@synchronized null
        if (current.expiresAtMillis <= nowMillis) {
            clear()
            null
        } else current
    }

    private fun write(active: ActiveExecution) {
        val value = JSONObject()
            .put("executionId", active.executionId)
            .put("routineId", active.routineId.value)
            .put("status", active.status.name)
            .put("expiresAt", active.expiresAtMillis)
        active.continuation?.let { continuation ->
            value.put(
                "continuation",
                JSONObject()
                    .put("continuationId", continuation.continuationId)
                    .put("executionId", continuation.executionId)
                    .put("routineId", continuation.routineId.value)
                    .put("nodeId", continuation.nodeId.value)
                    .put("nodeIndex", continuation.nodeIndex)
                    .put("actionKind", continuation.actionKind.name)
                    .put("actionParameters", continuation.actionParameters)
                    .put("routineSnapshot", shortcutCodec.encodeSnapshot(continuation.routineSnapshot))
                    .put("createdAt", continuation.createdAtMillis)
                    .put("expiresAt", continuation.expiresAtMillis),
            )
        }
        check(preferences.edit().putString(KEY_ACTIVE, value.toString()).commit())
    }

    private fun read(): ActiveExecution? = runCatching {
        val raw = preferences.getString(KEY_ACTIVE, null) ?: return null
        val value = JSONObject(raw)
        val continuation = value.optJSONObject("continuation")?.let { item ->
            val snapshot = shortcutCodec.decodeSnapshot(item.getString("routineSnapshot")) ?: return null
            RoutineContinuation(
                continuationId = item.getString("continuationId"),
                executionId = item.getString("executionId"),
                routineId = ShortcutId(item.getString("routineId")),
                nodeId = NodeId(item.getString("nodeId")),
                nodeIndex = item.getInt("nodeIndex"),
                actionKind = ActionKind.valueOf(item.getString("actionKind")),
                actionParameters = item.getString("actionParameters"),
                routineSnapshot = snapshot,
                createdAtMillis = item.getLong("createdAt"),
                expiresAtMillis = item.getLong("expiresAt"),
            )
        }
        ActiveExecution(
            executionId = value.getString("executionId"),
            routineId = ShortcutId(value.getString("routineId")),
            status = ExecutionStatus.valueOf(value.getString("status")),
            expiresAtMillis = value.getLong("expiresAt"),
            continuation = continuation,
        )
    }.getOrNull()

    private fun clear() {
        check(preferences.edit().remove(KEY_ACTIVE).commit())
    }

    private fun matches(continuation: RoutineContinuation, identity: ContinuationIdentity): Boolean =
        continuation.continuationId == identity.continuationId &&
            continuation.executionId == identity.executionId &&
            continuation.routineId == identity.routineId &&
            continuation.nodeId == identity.nodeId

    private companion object {
        const val PREFERENCES = "routine_execution_state"
        const val KEY_ACTIVE = "active"
        val lock = Any()
    }
}
