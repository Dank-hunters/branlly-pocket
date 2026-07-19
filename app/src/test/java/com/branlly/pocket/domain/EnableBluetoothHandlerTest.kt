package com.branlly.pocket.domain

import android.bluetooth.BluetoothAdapter
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.workflow.CapabilityResolver
import com.branlly.pocket.platform.android.BluetoothRequestResultRegistry
import com.branlly.pocket.platform.android.BluetoothSystemRequestResult
import com.branlly.pocket.platform.android.actions.BluetoothCapabilities
import com.branlly.pocket.platform.android.actions.BluetoothEnableGateway
import com.branlly.pocket.platform.android.actions.BluetoothEnableRequestResult
import com.branlly.pocket.platform.android.actions.EnableBluetoothHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnableBluetoothHandlerTest {
    @Test
    fun `already enabled completes without system request`() = runBlocking {
        val fixture = Fixture(state = BluetoothAdapter.STATE_ON)

        assertEquals(ActionResult.Completed, fixture.handler().execute(ShortcutAction.EnableBluetooth(), context()))
        assertEquals(0, fixture.requests)
    }

    @Test
    fun `accepted request verifies STATE_ON and needs no second click`() = runBlocking {
        val fixture = Fixture(state = BluetoothAdapter.STATE_OFF, requestResult = BluetoothEnableRequestResult.Enabled)

        assertEquals(ActionResult.Completed, fixture.handler().execute(ShortcutAction.EnableBluetooth(), context(userInitiated = true)))
        assertEquals(1, fixture.requests)
    }

    @Test
    fun `refused request cancels action`() = runBlocking {
        val fixture = Fixture(state = BluetoothAdapter.STATE_OFF, requestResult = BluetoothEnableRequestResult.Refused)

        val result = fixture.handler().execute(ShortcutAction.EnableBluetooth(), context(userInitiated = true))

        assertTrue(result is ActionResult.Cancelled)
        assertEquals(1, fixture.requests)
    }

    @Test
    fun `background execution requests generic user continuation before system dialog`() = runBlocking {
        val fixture = Fixture(state = BluetoothAdapter.STATE_OFF, canShowRequest = false)

        val result = fixture.handler().execute(ShortcutAction.EnableBluetooth(), context())

        assertTrue(result is ActionResult.UserActionRequired)
        assertEquals(0, fixture.requests)
    }

    @Test
    fun `activity result registry handles result after waiter recreation`() = runBlocking {
        val registry = BluetoothRequestResultRegistry()
        val waiter = async { registry.await("request", 1_000) }
        yield()

        registry.publish("request", BluetoothSystemRequestResult.ACCEPTED)

        assertEquals(BluetoothSystemRequestResult.ACCEPTED, waiter.await())
        assertFalse(registry.hasPendingWork())
    }

    @Test
    fun `late activity result is consumed exactly once`() = runBlocking {
        val registry = BluetoothRequestResultRegistry()
        registry.publish("request", BluetoothSystemRequestResult.REFUSED)

        assertEquals(BluetoothSystemRequestResult.REFUSED, registry.await("request", 1_000))
        assertFalse(registry.hasPendingWork())
    }

    private class Fixture(
        var state: Int,
        private val requestResult: BluetoothEnableRequestResult = BluetoothEnableRequestResult.Enabled,
        private val canShowRequest: Boolean = true,
    ) {
        var requests = 0

        private val resolver = CapabilityResolver<ShortcutAction.EnableBluetooth, BluetoothCapabilities> {
            BluetoothCapabilities(
                hasConnectPermission = true,
                adapterAvailable = true,
                state = state,
                canShowSystemRequestNow = canShowRequest,
            )
        }
        private val gateway = BluetoothEnableGateway { _, _ ->
            requests++
            if (requestResult is BluetoothEnableRequestResult.Enabled) state = BluetoothAdapter.STATE_ON
            requestResult
        }

        fun handler() = EnableBluetoothHandler(resolver, gateway)
    }

    private fun context(userInitiated: Boolean = false) = ActionExecutionContext(
        executionId = "execution",
        routineId = ShortcutId.new(),
        nodeId = NodeId.new(),
        logger = ExecutionLogger { _, _ -> },
        userInitiated = userInitiated,
    )
}
