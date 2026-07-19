package com.branlly.pocket.domain

import com.branlly.pocket.data.ActionJsonCodecRegistry
import com.branlly.pocket.domain.execution.ActionEditorKey
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionHandler
import com.branlly.pocket.domain.execution.ActionRegistry
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ActiveExecution
import com.branlly.pocket.domain.execution.ContinuationClaim
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.execution.ExecutionStatus
import com.branlly.pocket.domain.execution.RegisteredAction
import com.branlly.pocket.domain.execution.RoutineContinuation
import com.branlly.pocket.domain.execution.RoutineExecutionStateStore
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.platform.android.RoutineExecutionResult
import com.branlly.pocket.platform.android.ShortcutExecutor
import com.branlly.pocket.platform.android.isContinuationConsistent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineContinuationFlowTest {
    @Test
    fun `UserActionRequired waits then resumes blocked node and every remaining node`() = runBlocking {
        val calls = mutableListOf<String>()
        val app = ShortcutAction.OpenApplication(InputValue.Fixed("example.external.application"))
        val wait = ShortcutAction.Wait(100)
        val final = ShortcutAction.Notification("done", "done")
        val registry = ActionRegistry(
            listOf(
                registration(ActionKind.OPEN_APPLICATION, ShortcutAction.OpenApplication::class.java, app) { _, context ->
                    calls += "external:${context.userInitiated}"
                    if (context.userInitiated) ActionResult.Completed else ActionResult.UserActionRequired("confirmation")
                },
                registration(ActionKind.WAIT, ShortcutAction.Wait::class.java, wait) { _, _ -> calls += "wait"; ActionResult.Completed },
                registration(ActionKind.NOTIFICATION, ShortcutAction.Notification::class.java, final) { _, _ -> calls += "final"; ActionResult.Completed },
            ),
        )
        val routine = routine(app, wait, final)
        val executor = ShortcutExecutor(registry)

        val first = executor.execute(routine, "execution")

        assertTrue(first is RoutineExecutionResult.WaitingUserAction)
        first as RoutineExecutionResult.WaitingUserAction
        assertEquals(listOf("external:false"), calls)

        val resumed = executor.execute(routine, "execution", first.nodeIndex, first.nodeId)

        assertEquals(RoutineExecutionResult.Completed, resumed)
        assertEquals(listOf("external:false", "external:true", "wait", "final"), calls)
    }

    @Test
    fun `open application route and settings use identical generic continuation semantics`() = runBlocking {
        val actions = listOf<ShortcutAction>(
            ShortcutAction.OpenApplication(InputValue.Fixed("example.application")),
            ShortcutAction.OpenRoute(InputValue.Fixed("example.navigation"), InputValue.Fixed("destination")),
            ShortcutAction.OpenSettings(SettingsPanel.WIFI),
        )
        for (action in actions) {
            var resumedCalls = 0
            val registration = when (action) {
                is ShortcutAction.OpenApplication -> registration(action.kind, ShortcutAction.OpenApplication::class.java, action) { _, context ->
                    if (context.userInitiated) { resumedCalls++; ActionResult.Completed } else ActionResult.UserActionRequired("user")
                }
                is ShortcutAction.OpenRoute -> registration(action.kind, ShortcutAction.OpenRoute::class.java, action) { _, context ->
                    if (context.userInitiated) { resumedCalls++; ActionResult.Completed } else ActionResult.UserActionRequired("user")
                }
                is ShortcutAction.OpenSettings -> registration(action.kind, ShortcutAction.OpenSettings::class.java, action) { _, context ->
                    if (context.userInitiated) { resumedCalls++; ActionResult.Completed } else ActionResult.UserActionRequired("user")
                }
                else -> error("unexpected")
            }
            val executor = ShortcutExecutor(ActionRegistry(listOf(registration)))
            val routine = routine(action)
            val waiting = executor.execute(routine, "generic") as RoutineExecutionResult.WaitingUserAction
            assertEquals(RoutineExecutionResult.Completed, executor.execute(routine, "generic", waiting.nodeIndex, waiting.nodeId))
            assertEquals(1, resumedCalls)
        }
    }

    @Test
    fun `claim is atomic and two taps cannot consume twice`() {
        val fixture = stateFixture()
        assertTrue(fixture.store.begin("execution", fixture.routine.id, 10_000, 0))
        assertTrue(fixture.store.waitForUser(fixture.continuation))
        assertTrue(fixture.store.claim(fixture.identity, 2) is ContinuationClaim.Claimed)
        assertEquals(ContinuationClaim.AlreadyConsumed, fixture.store.claim(fixture.identity, 3))
    }

    @Test
    fun `expired continuation is refused and lock released`() {
        val fixture = stateFixture(expiresAt = 10)
        fixture.store.begin("execution", fixture.routine.id, 10, 0)
        fixture.store.waitForUser(fixture.continuation)
        assertEquals(ContinuationClaim.Expired, fixture.store.claim(fixture.identity, 10))
        assertTrue(fixture.store.begin("new", fixture.routine.id, 100, 11))
    }

    @Test
    fun `cancel prevents resume and releases lock`() {
        val fixture = stateFixture()
        fixture.store.begin("execution", fixture.routine.id, 10_000, 0)
        fixture.store.waitForUser(fixture.continuation)
        assertTrue(fixture.store.cancel(fixture.identity, 2) is ContinuationClaim.Claimed)
        assertEquals(ContinuationClaim.Missing, fixture.store.claim(fixture.identity, 3))
        assertTrue(fixture.store.begin("new", fixture.routine.id, 20_000, 3))
    }

    @Test
    fun `old execution and different routine cannot consume active continuation`() {
        val fixture = stateFixture()
        fixture.store.begin("execution", fixture.routine.id, 10_000, 0)
        fixture.store.waitForUser(fixture.continuation)
        assertEquals(
            ContinuationClaim.Mismatch,
            fixture.store.claim(fixture.identity.copy(executionId = "old-execution"), 2),
        )
        assertEquals(
            ContinuationClaim.Mismatch,
            fixture.store.claim(fixture.identity.copy(routineId = ShortcutId.new()), 2),
        )
        assertTrue(fixture.store.claim(fixture.identity, 2) is ContinuationClaim.Claimed)
    }

    @Test
    fun `waiting execution stays active and refuses another routine`() {
        val fixture = stateFixture()
        fixture.store.begin("execution", fixture.routine.id, 10_000, 0)
        fixture.store.waitForUser(fixture.continuation)
        assertEquals(ExecutionStatus.WAITING_USER_ACTION, fixture.store.active(2)?.status)
        assertFalse(fixture.store.begin("other", ShortcutId.new(), 10_000, 2))
    }

    @Test
    fun `finish releases execution lock`() {
        val fixture = stateFixture()
        fixture.store.begin("execution", fixture.routine.id, 10_000, 0)
        fixture.store.finish("execution")
        assertTrue(fixture.store.begin("next", fixture.routine.id, 20_000, 2))
    }

    @Test
    fun `continuation identity and serialized action must match snapshot`() {
        val fixture = stateFixture()
        val valid = fixture.continuation.copy(
            actionParameters = ActionJsonCodecRegistry.DEFAULT.encode(fixture.routine.nodes.first().action).toString(),
        )
        assertTrue(isContinuationConsistent(valid))
        assertFalse(isContinuationConsistent(valid.copy(nodeId = NodeId.new())))
        assertFalse(isContinuationConsistent(valid.copy(actionParameters = "{}")))
        assertFalse(isContinuationConsistent(valid.copy(routineId = ShortcutId.new())))
    }

    @Test
    fun `continuation carries no provider dependency`() {
        val fixture = stateFixture()
        val serialized = fixture.continuation.toString().lowercase()
        assertFalse(serialized.contains("waze"))
        assertFalse(serialized.contains("youtube"))
        assertFalse(serialized.contains("spotify"))
    }

    private fun stateFixture(expiresAt: Long = 10_000): Fixture {
        val action = ShortcutAction.OpenApplication(InputValue.Fixed("example.external.application"))
        val routine = routine(action, ShortcutAction.Wait(100))
        val node = routine.nodes.first()
        val continuation = RoutineContinuation(
            continuationId = "continuation",
            executionId = "execution",
            routineId = routine.id,
            nodeId = node.id,
            nodeIndex = 0,
            actionKind = action.kind,
            actionParameters = "{example}",
            routineSnapshot = routine,
            createdAtMillis = 1,
            expiresAtMillis = expiresAt,
        )
        return Fixture(MemoryStateStore(), routine, continuation, ContinuationIdentity("continuation", "execution", routine.id, node.id))
    }

    private data class Fixture(
        val store: MemoryStateStore,
        val routine: ShortcutDefinition,
        val continuation: RoutineContinuation,
        val identity: ContinuationIdentity,
    )

    private class MemoryStateStore : RoutineExecutionStateStore {
        private var current: ActiveExecution? = null

        override fun begin(executionId: String, routineId: ShortcutId, expiresAtMillis: Long, nowMillis: Long): Boolean = synchronized(this) {
            if (current?.expiresAtMillis?.let { it > nowMillis } == true) return@synchronized false
            current = ActiveExecution(executionId, routineId, ExecutionStatus.RUNNING, expiresAtMillis)
            true
        }

        override fun waitForUser(continuation: RoutineContinuation): Boolean = synchronized(this) {
            val active = current ?: return@synchronized false
            if (active.executionId != continuation.executionId || active.routineId != continuation.routineId || active.status != ExecutionStatus.RUNNING) return@synchronized false
            current = active.copy(status = ExecutionStatus.WAITING_USER_ACTION, expiresAtMillis = continuation.expiresAtMillis, continuation = continuation)
            true
        }

        override fun claim(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim = synchronized(this) {
            val active = current ?: return@synchronized ContinuationClaim.Missing
            val continuation = active.continuation ?: return@synchronized ContinuationClaim.Missing
            if (!matches(continuation, identity)) return@synchronized ContinuationClaim.Mismatch
            if (continuation.expiresAtMillis <= nowMillis) { current = null; return@synchronized ContinuationClaim.Expired }
            if (active.status != ExecutionStatus.WAITING_USER_ACTION) return@synchronized ContinuationClaim.AlreadyConsumed
            current = active.copy(status = ExecutionStatus.RUNNING)
            ContinuationClaim.Claimed(continuation)
        }

        override fun cancel(identity: ContinuationIdentity, nowMillis: Long): ContinuationClaim = synchronized(this) {
            val active = current ?: return@synchronized ContinuationClaim.Missing
            val continuation = active.continuation ?: return@synchronized ContinuationClaim.Missing
            if (!matches(continuation, identity)) return@synchronized ContinuationClaim.Mismatch
            if (continuation.expiresAtMillis <= nowMillis) { current = null; return@synchronized ContinuationClaim.Expired }
            if (active.status != ExecutionStatus.WAITING_USER_ACTION) return@synchronized ContinuationClaim.AlreadyConsumed
            current = null
            ContinuationClaim.Claimed(continuation)
        }

        override fun finish(executionId: String) { synchronized(this) { if (current?.executionId == executionId) current = null } }
        override fun active(nowMillis: Long): ActiveExecution? = synchronized(this) {
            current?.takeIf { it.expiresAtMillis > nowMillis }.also { if (it == null) current = null }
        }

        private fun matches(continuation: RoutineContinuation, identity: ContinuationIdentity) =
            continuation.continuationId == identity.continuationId && continuation.executionId == identity.executionId &&
                continuation.routineId == identity.routineId && continuation.nodeId == identity.nodeId
    }

    private fun routine(vararg actions: ShortcutAction) = ShortcutDefinition(
        name = "continuation",
        trigger = Trigger.ManualButton,
        nodes = actions.map { ActionNode(action = it) },
    )

    private fun <A : ShortcutAction> registration(
        kind: ActionKind,
        type: Class<A>,
        default: A,
        execute: suspend (A, ActionExecutionContext) -> ActionResult,
    ): RegisteredAction<A> = RegisteredAction(
        kind,
        type,
        kind.name,
        kind.name,
        ActionCategory.ORGANIZE,
        ActionEditorKey.WAIT,
        { default },
        handler = object : ActionHandler<A> {
            override val kind = kind
            override fun validate(action: A, context: ActionValidationContext) = emptyList<com.branlly.pocket.domain.execution.ActionValidationError>()
            override suspend fun execute(action: A, context: ActionExecutionContext) = execute(action, context)
        },
    )
}
