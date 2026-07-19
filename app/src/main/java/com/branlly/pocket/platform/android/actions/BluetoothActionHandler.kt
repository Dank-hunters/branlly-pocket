package com.branlly.pocket.platform.android.actions

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionHandler
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ActionValidationError
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.ActionProgress
import com.branlly.pocket.domain.workflow.ActionWorkflow
import com.branlly.pocket.domain.workflow.ActionWorkflowCheckpoint
import com.branlly.pocket.domain.workflow.ActionWorkflowContext
import com.branlly.pocket.domain.workflow.ActionWorkflowState
import com.branlly.pocket.domain.workflow.ActionWorkflowStep
import com.branlly.pocket.domain.workflow.BoundedActionWorkflowRunner
import com.branlly.pocket.domain.workflow.CapabilityResolver

data class BluetoothCapabilities(
    val hasConnectPermission: Boolean,
    val adapterAvailable: Boolean,
    val state: Int,
    val canShowSystemRequestNow: Boolean,
)

class AndroidBluetoothCapabilityResolver(
    context: Context,
) : CapabilityResolver<ShortcutAction.EnableBluetooth, BluetoothCapabilities> {
    private val appContext = context.applicationContext

    override fun resolve(action: ShortcutAction.EnableBluetooth): BluetoothCapabilities {
        val permission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val adapter = appContext.getSystemService(BluetoothManager::class.java).adapter
        val state = if (permission && adapter != null) runCatching { adapter.state }.getOrDefault(BluetoothAdapter.STATE_OFF) else BluetoothAdapter.STATE_OFF
        val process = appContext.getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
        return BluetoothCapabilities(
            hasConnectPermission = permission,
            adapterAvailable = adapter != null,
            state = state,
            canShowSystemRequestNow = process?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
        )
    }
}

sealed interface BluetoothEnableRequestResult {
    data object Enabled : BluetoothEnableRequestResult
    data object Refused : BluetoothEnableRequestResult
    data object PermissionDenied : BluetoothEnableRequestResult
    data object TimedOut : BluetoothEnableRequestResult
    data class Failed(val reason: String) : BluetoothEnableRequestResult
}

fun interface BluetoothEnableGateway {
    suspend fun requestEnable(requestId: String, timeoutMillis: Long): BluetoothEnableRequestResult
}

enum class EnableBluetoothState(override val key: String) : ActionWorkflowState {
    CHECKING_STATE("checking_state"),
    REQUESTING_ENABLE("requesting_enable"),
    VERIFYING_STATE("verifying_state"),
}

class EnableBluetoothWorkflow(
    private val action: ShortcutAction.EnableBluetooth,
    private val executionContext: ActionExecutionContext,
    private val capabilityResolver: CapabilityResolver<ShortcutAction.EnableBluetooth, BluetoothCapabilities>,
    private val gateway: BluetoothEnableGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ActionWorkflow<EnableBluetoothState> {
    override val initialState = EnableBluetoothState.CHECKING_STATE

    override suspend fun transition(
        state: EnableBluetoothState,
        context: ActionWorkflowContext,
    ): ActionWorkflowStep<EnableBluetoothState> = when (state) {
        EnableBluetoothState.CHECKING_STATE -> {
            val capabilities = capabilityResolver.resolve(action)
            when {
                !capabilities.adapterAvailable -> ActionWorkflowStep.Failed("Aucun adaptateur Bluetooth n’est disponible.")
                capabilities.hasConnectPermission && capabilities.state == BluetoothAdapter.STATE_ON -> ActionWorkflowStep.Completed
                !executionContext.userInitiated && !capabilities.canShowSystemRequestNow -> {
                    val checkpoint = ActionWorkflowCheckpoint(
                        actionId = context.actionId,
                        executionId = context.executionId,
                        routineId = context.routineId,
                        actionKind = context.actionKind,
                        stateKey = EnableBluetoothState.REQUESTING_ENABLE.key,
                        payload = emptyMap(),
                        startedAtMillis = context.startedAtMillis,
                        expiresAtMillis = context.expiresAtMillis,
                    )
                    ActionWorkflowStep.UserActionRequired(
                        "Touchez pour afficher la demande système d’activation du Bluetooth.",
                        checkpoint,
                    )
                }
                else -> ActionWorkflowStep.ContinueInternally(
                    EnableBluetoothState.REQUESTING_ENABLE,
                    ActionProgress.WaitingForSystemConfirmation("Activation du Bluetooth"),
                )
            }
        }
        EnableBluetoothState.REQUESTING_ENABLE -> {
            val remaining = (context.expiresAtMillis - nowMillis()).coerceAtLeast(1L)
            when (val result = gateway.requestEnable("${context.executionId}:${context.actionId.value}", remaining)) {
                BluetoothEnableRequestResult.Enabled -> ActionWorkflowStep.ContinueInternally(EnableBluetoothState.VERIFYING_STATE)
                BluetoothEnableRequestResult.Refused -> ActionWorkflowStep.Cancelled("Activation Bluetooth refusée.")
                BluetoothEnableRequestResult.PermissionDenied -> ActionWorkflowStep.Cancelled("Permission Bluetooth refusée.")
                BluetoothEnableRequestResult.TimedOut -> ActionWorkflowStep.TimedOut("La demande Bluetooth a expiré.")
                is BluetoothEnableRequestResult.Failed -> ActionWorkflowStep.Failed(result.reason)
            }
        }
        EnableBluetoothState.VERIFYING_STATE -> {
            val capabilities = capabilityResolver.resolve(action)
            if (capabilities.hasConnectPermission && capabilities.state == BluetoothAdapter.STATE_ON) {
                ActionWorkflowStep.Completed
            } else {
                ActionWorkflowStep.Failed("Android n’a pas confirmé l’activation du Bluetooth.")
            }
        }
    }
}

class EnableBluetoothHandler(
    private val capabilityResolver: CapabilityResolver<ShortcutAction.EnableBluetooth, BluetoothCapabilities>,
    private val gateway: BluetoothEnableGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ActionHandler<ShortcutAction.EnableBluetooth> {
    override val kind = ActionKind.ENABLE_BLUETOOTH

    override fun validate(
        action: ShortcutAction.EnableBluetooth,
        context: ActionValidationContext,
    ): List<ActionValidationError> = emptyList()

    override suspend fun execute(
        action: ShortcutAction.EnableBluetooth,
        context: ActionExecutionContext,
    ): ActionResult {
        val startedAt = nowMillis()
        val workflowContext = ActionWorkflowContext(
            actionId = context.nodeId,
            executionId = context.executionId,
            routineId = context.routineId,
            actionKind = action.kind,
            startedAtMillis = startedAt,
            expiresAtMillis = startedAt + action.timeoutMillis,
            logger = context.logger,
        )
        return BoundedActionWorkflowRunner(maxTransitions = 4, timeoutMillis = action.timeoutMillis)
            .run(EnableBluetoothWorkflow(action, context, capabilityResolver, gateway, nowMillis), workflowContext)
            .result
    }
}
