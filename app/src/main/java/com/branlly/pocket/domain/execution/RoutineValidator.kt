package com.branlly.pocket.domain.execution

import com.branlly.pocket.domain.model.ShortcutDefinition

data class RoutineValidationIssue(
    val nodeId: String?,
    val code: String,
    val message: String,
)

class RoutineValidator(
    private val registry: ActionRegistry,
    private val context: ActionValidationContext,
) {
    fun validate(routine: ShortcutDefinition): List<RoutineValidationIssue> = buildList {
        if (routine.name.isBlank()) add(RoutineValidationIssue(null, "missing_name", "Donnez un nom au raccourci."))
        if (routine.nodes.isEmpty()) add(RoutineValidationIssue(null, "missing_action", "Ajoutez au moins une action."))
        routine.nodes.forEach { node ->
            if (node.conditions.isNotEmpty()) {
                add(
                    RoutineValidationIssue(
                        node.id.value,
                        "unsupported_conditions",
                        "Les conditions de node ne sont pas encore exécutées.",
                    ),
                )
            }
            registry.validate(node.action, context).forEach { error ->
                add(RoutineValidationIssue(node.id.value, error.code, error.message))
            }
        }
    }
}
