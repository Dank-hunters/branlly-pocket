package com.branlly.pocket.domain

import android.content.Intent
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.platform.android.actions.ExternalActivityGateway
import com.branlly.pocket.platform.android.actions.NavigationProviderAdapter
import com.branlly.pocket.platform.android.actions.NavigationTarget
import com.branlly.pocket.platform.android.actions.OpenRouteHandler
import com.branlly.pocket.platform.android.actions.RouteRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouteRuntimeDestinationTest {
    @Test fun `ask at runtime is valid without fixed destination`() {
        assertTrue(handler().validate(action(), validation()).isEmpty())
    }

    @Test fun `runtime destination is requested then launches configured provider`() =
        runBlocking {
            val fixture = Fixture()
            val first = fixture.handler.execute(action(), context()) as ActionResult.UserActionRequired
            assertEquals("route_destination_v1", first.workflowCheckpoint?.stateKey)
            assertEquals(0, fixture.launches)

            val resumed =
                fixture.handler.execute(
                    action(),
                    context(
                        first.workflowCheckpoint!!.copy(
                            payload =
                                mapOf("destination" to "  Gare de Lyon  "),
                        ),
                    ),
                )
            assertEquals(ActionResult.Completed, resumed)
            assertEquals(1, fixture.launches)
            assertEquals("Gare de Lyon", fixture.destination)
        }

    private class Fixture {
        var launches = 0
        var destination: String? = null
        private val adapter =
            object : NavigationProviderAdapter {
                override fun supports(target: NavigationTarget) = target.packageName == "example.maps"

                override fun buildRouteIntent(request: RouteRequest): Intent {
                    destination = request.destination
                    return Intent("route")
                }
            }
        val handler =
            OpenRouteHandler(
                object : ExternalActivityGateway {
                    override fun canResolve(intent: Intent) = true

                    override suspend fun launch(
                        intent: Intent,
                        label: String,
                        executionContext: ActionExecutionContext,
                    ): ActionResult {
                        launches++
                        return ActionResult.Completed
                    }
                },
                listOf(adapter),
            )
    }

    private fun handler() = Fixture().handler

    private fun action() = ShortcutAction.OpenRoute(InputValue.Fixed("example.maps"), InputValue.AskAtRuntime)

    private fun validation() =
        object : ActionValidationContext {
            override fun isPackageInstalled(packageName: String) = true

            override fun isPackageLaunchable(packageName: String) = true
        }

    private fun context(checkpoint: com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint? = null) =
        ActionExecutionContext(
            "execution",
            ShortcutId("routine"),
            NodeId("node"),
            ExecutionLogger {
                _,
                _,
                ->
            },
            workflowCheckpoint = checkpoint,
        )
}
