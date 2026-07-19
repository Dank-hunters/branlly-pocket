package com.branlly.pocket.domain

import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.ActionWorkflow
import com.branlly.pocket.domain.workflow.ActionWorkflowContext
import com.branlly.pocket.domain.workflow.ActionWorkflowState
import com.branlly.pocket.domain.workflow.ActionWorkflowStep
import com.branlly.pocket.domain.workflow.BoundedActionWorkflowRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionWorkflowTest {
    private enum class State(override val key: String) : ActionWorkflowState { FIRST("first"), SECOND("second") }

    @Test
    fun `complex action substeps remain inside bounded workflow`() = runBlocking {
        val visited = mutableListOf<State>()
        val workflow = object : ActionWorkflow<State> {
            override val initialState = State.FIRST
            override suspend fun transition(state: State, context: ActionWorkflowContext): ActionWorkflowStep<State> {
                visited += state
                return if (state == State.FIRST) ActionWorkflowStep.ContinueInternally(State.SECOND) else ActionWorkflowStep.Completed
            }
        }

        val outcome = BoundedActionWorkflowRunner(maxTransitions = 4, timeoutMillis = 1_000).run(workflow, context())

        assertEquals(ActionResult.Completed, outcome.result)
        assertEquals(listOf(State.FIRST, State.SECOND), visited)
        assertEquals(2, outcome.transitionCount)
    }

    @Test
    fun `transition budget stops a workflow without recursion`() = runBlocking {
        val workflow = object : ActionWorkflow<State> {
            override val initialState = State.FIRST
            override suspend fun transition(state: State, context: ActionWorkflowContext) = ActionWorkflowStep.ContinueInternally(state)
        }

        val outcome = BoundedActionWorkflowRunner(maxTransitions = 3, timeoutMillis = 1_000).run(workflow, context())

        assertTrue(outcome.result is ActionResult.Failed)
        assertEquals(3, outcome.transitionCount)
    }

    @Test
    fun `global workflow timeout is enforced`() = runBlocking {
        val workflow = object : ActionWorkflow<State> {
            override val initialState = State.FIRST
            override suspend fun transition(state: State, context: ActionWorkflowContext): ActionWorkflowStep<State> {
                delay(250)
                return ActionWorkflowStep.Completed
            }
        }

        val outcome = BoundedActionWorkflowRunner(maxTransitions = 2, timeoutMillis = 100).run(workflow, context())

        assertTrue(outcome.result is ActionResult.TimedOut)
    }

    private fun context() = ActionWorkflowContext(
        actionId = NodeId.new(),
        executionId = "execution",
        routineId = ShortcutId.new(),
        actionKind = ActionKind.WAIT,
        startedAtMillis = 1,
        expiresAtMillis = 2_000,
        logger = ExecutionLogger { _, _ -> },
    )
}
