package com.branlly.pocket.domain.execution

import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId

sealed interface ActionResult {
    data object Completed : ActionResult

    data class Failed(
        val reason: String,
        val recoverable: Boolean = false,
    ) : ActionResult

    data class TimedOut(
        val reason: String = "L’action a expiré.",
    ) : ActionResult

    data class Cancelled(
        val reason: String = "Exécution annulée.",
    ) : ActionResult

    data class UserActionRequired(
        val reason: String,
    ) : ActionResult

    data class PermissionRequired(
        val reason: String,
        val settingsAction: String? = null,
    ) : ActionResult
}

data class ActionValidationError(
    val code: String,
    val message: String,
)

interface ActionValidationContext {
    fun isPackageInstalled(packageName: String): Boolean

    fun isPackageLaunchable(packageName: String): Boolean
}

fun interface ExecutionLogger {
    fun log(event: String, fields: Map<String, Any?>)
}

data class ActionExecutionContext(
    val executionId: String,
    val routineId: ShortcutId,
    val nodeId: NodeId,
    val logger: ExecutionLogger,
    /** True only for the node explicitly resumed by a user notification tap. */
    val userInitiated: Boolean = false,
)

interface ActionHandler<A : ShortcutAction> {
    val kind: ActionKind

    fun validate(
        action: A,
        context: ActionValidationContext,
    ): List<ActionValidationError>

    suspend fun execute(
        action: A,
        context: ActionExecutionContext,
    ): ActionResult
}

enum class ActionEditorKey {
    APPLICATION,
    MEDIA_WAIT,
    ROUTE,
    SETTINGS,
    VOLUME,
    BRIGHTNESS,
    SOUND_MODE,
    WAIT,
}

data class RegisteredAction<A : ShortcutAction>(
    val kind: ActionKind,
    val actionClass: Class<A>,
    val title: String,
    val description: String,
    val category: com.branlly.pocket.domain.model.ActionCategory,
    val editorKey: ActionEditorKey,
    val createDefault: () -> A,
    val summary: (A) -> String = { title },
    val handler: ActionHandler<A>,
    val visibleInEditor: Boolean = true,
) {
    init {
        require(handler.kind == kind)
    }
}

class ActionRegistry(
    registrations: List<RegisteredAction<out ShortcutAction>>,
) {
    private val byKind = registrations.associateBy { it.kind }

    init {
        require(byKind.size == registrations.size) { "Each ActionKind can only be registered once" }
    }

    val actions: List<RegisteredAction<out ShortcutAction>> = registrations.toList()

    fun registration(kind: ActionKind): RegisteredAction<out ShortcutAction>? = byKind[kind]

    fun visibleActions(): List<RegisteredAction<out ShortcutAction>> = actions.filter(RegisteredAction<*>::visibleInEditor)

    fun summary(action: ShortcutAction): String {
        val registration = byKind[action.kind] ?: return "Action non prise en charge · ${action.kind}"
        if (!registration.actionClass.isInstance(action)) return "Action incohérente · ${action.kind}"
        return summaryTyped(registration, action)
    }

    fun validate(
        action: ShortcutAction,
        context: ActionValidationContext,
    ): List<ActionValidationError> {
        val registration = byKind[action.kind]
            ?: return listOf(ActionValidationError("missing_handler", "Cette action n’est pas prise en charge."))
        if (!registration.actionClass.isInstance(action)) {
            return listOf(ActionValidationError("invalid_action_type", "Le type de cette action est incohérent."))
        }
        return validateTyped(registration, action, context)
    }

    suspend fun execute(
        action: ShortcutAction,
        context: ActionExecutionContext,
    ): ActionResult {
        val registration = byKind[action.kind]
            ?: return ActionResult.Failed("Aucun handler n’est enregistré pour ${action.kind}.")
        if (!registration.actionClass.isInstance(action)) {
            return ActionResult.Failed("Le modèle de l’action ${action.kind} est incohérent.")
        }
        return executeTyped(registration, action, context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun summaryTyped(
        registration: RegisteredAction<out ShortcutAction>,
        action: ShortcutAction,
    ): String = (registration as RegisteredAction<ShortcutAction>).summary(action)

    @Suppress("UNCHECKED_CAST")
    private fun validateTyped(
        registration: RegisteredAction<out ShortcutAction>,
        action: ShortcutAction,
        context: ActionValidationContext,
    ): List<ActionValidationError> =
        (registration as RegisteredAction<ShortcutAction>).handler.validate(action, context)

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeTyped(
        registration: RegisteredAction<out ShortcutAction>,
        action: ShortcutAction,
        context: ActionExecutionContext,
    ): ActionResult =
        (registration as RegisteredAction<ShortcutAction>).handler.execute(action, context)
}
