package com.branlly.pocket.platform.android

import com.branlly.pocket.domain.execution.ActiveExecution
import com.branlly.pocket.domain.execution.ContinuationClaim
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.execution.RoutineContinuation
import com.branlly.pocket.domain.execution.RoutineExecutionStateStore
import com.branlly.pocket.domain.model.ShortcutId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExecutionLockCleanupTest {
    @Test fun `exception releases execution lock and next routine can start`() =
        runBlocking {
            val store = FakeStore()
            runCatching {
                runWithExecutionLock(store, "failed", ShortcutId("first"), 0, 10_000) { error("load failure") }
            }
            assertFalse(store.running)
            val result =
                runWithExecutionLock(store, "next", ShortcutId("second"), 1, 10_001) {
                    store.finish("next")
                    RoutineExecutionResult.Completed
                }
            assertEquals(RoutineExecutionResult.Completed, result)
            assertFalse(store.running)
        }

    private class FakeStore : RoutineExecutionStateStore {
        var running = false

        override fun begin(
            executionId: String,
            routineId: ShortcutId,
            expiresAtMillis: Long,
            nowMillis: Long,
        ): Boolean {
            if (running) return false
            running = true
            return true
        }

        override fun finish(executionId: String) {
            running = false
        }

        override fun waitForUser(continuation: RoutineContinuation) = false

        override fun claim(
            identity: ContinuationIdentity,
            nowMillis: Long,
        ): ContinuationClaim = ContinuationClaim.Missing

        override fun cancel(
            identity: ContinuationIdentity,
            nowMillis: Long,
        ): ContinuationClaim = ContinuationClaim.Missing

        override fun active(nowMillis: Long): ActiveExecution? = null
    }
}
