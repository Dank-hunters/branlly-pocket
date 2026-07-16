package com.branlly.pocket.platform.android

import android.content.Context
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ExecutionTiming
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import kotlinx.coroutines.delay

/** Exécute dans l'ordre les actions Android actuellement réellement prises en charge. */
class ShortcutExecutor(
    private val context: Context,
) {
    suspend fun execute(shortcut: ShortcutDefinition): ShortcutExecutionResult {
        val actions = shortcut.nodes.filter(ActionNode::enabled).map(ActionNode::action)
        actions.forEachIndexed { index, action ->
            val result = execute(action)
            if (result !is ShortcutExecutionResult.Completed) return result
            val delayMillis = ExecutionTiming.automaticDelayAfter(action, actions.getOrNull(index + 1))
            if (delayMillis > 0) delay(delayMillis)
        }
        val finalAction =
            shortcut.finalForegroundNodeId?.let { id ->
                shortcut.nodes.firstOrNull { it.id == id && it.enabled }?.action
            }
        if (finalAction is ShortcutAction.OpenApplication || finalAction is ShortcutAction.OpenRoute) {
            return execute(finalAction)
        }
        return ShortcutExecutionResult.Completed
    }

    private suspend fun execute(action: ShortcutAction): ShortcutExecutionResult =
        when (action) {
            is ShortcutAction.OpenApplication -> {
                val packageName = (action.packageName as? InputValue.Fixed<String>)?.value
                val searchQuery = (action.searchQuery as? InputValue.Fixed<String>)?.value
                ApplicationLauncher(context).launch(packageName, searchQuery).toExecutionResult()
            }

            is ShortcutAction.OpenRoute -> {
                RouteLauncher(context).launch(action).toExecutionResult()
            }

            is ShortcutAction.Wait -> {
                delay(action.durationMillis)
                ShortcutExecutionResult.Completed
            }

            else -> {
                ShortcutExecutionResult.UnsupportedAction(action.kind.name)
            }
        }
}

sealed interface ShortcutExecutionResult {
    data object Completed : ShortcutExecutionResult

    data class Failed(
        val message: String,
    ) : ShortcutExecutionResult

    data class UnsupportedAction(
        val kind: String,
    ) : ShortcutExecutionResult
}

private fun ApplicationLaunchResult.toExecutionResult(): ShortcutExecutionResult =
    when (this) {
        ApplicationLaunchResult.Launched -> {
            ShortcutExecutionResult.Completed
        }

        ApplicationLaunchResult.RuntimeValueRequired -> {
            ShortcutExecutionResult.Failed(
                "Choisissez une application avant de lancer ce raccourci.",
            )
        }

        ApplicationLaunchResult.InvalidPackage -> {
            ShortcutExecutionResult.Failed("L’application choisie n’est pas valide.")
        }

        ApplicationLaunchResult.MissingApplication -> {
            ShortcutExecutionResult.Failed("L’application choisie n’est plus installée.")
        }

        ApplicationLaunchResult.RejectedBySystem -> {
            ShortcutExecutionResult.Failed("Android a refusé l’ouverture de l’application.")
        }
    }

private fun RouteLaunchResult.toExecutionResult(): ShortcutExecutionResult =
    when (this) {
        RouteLaunchResult.Launched -> {
            ShortcutExecutionResult.Completed
        }

        RouteLaunchResult.MissingApplication -> {
            ShortcutExecutionResult.Failed("L’application de navigation choisie n’est pas installée.")
        }

        RouteLaunchResult.MissingDestination -> {
            ShortcutExecutionResult.Failed("Indiquez une destination.")
        }

        RouteLaunchResult.RuntimeValueRequired -> {
            ShortcutExecutionResult.Failed("Cette action demande une valeur au lancement.")
        }

        RouteLaunchResult.UnsupportedApplication -> {
            ShortcutExecutionResult.Failed(
                "Cette application de navigation n’est pas prise en charge.",
            )
        }

        RouteLaunchResult.RejectedBySystem -> {
            ShortcutExecutionResult.Failed("Android a refusé l’ouverture de l’itinéraire.")
        }
    }
