package com.branlly.pocket.domain.execution

import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
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
    fun validate(routine: ShortcutDefinition): List<RoutineValidationIssue> =
        buildList {
            if (routine.name.isBlank()) add(RoutineValidationIssue(null, "missing_name", "Donnez un nom au raccourci."))
            if (routine.nodes.isEmpty()) add(RoutineValidationIssue(null, "missing_action", "Ajoutez au moins une action."))
            val modernMediaPackages =
                routine.nodes.mapNotNull { (it.action as? ShortcutAction.PlayMedia)?.targetPackage }.toSet()
            routine.nodes.forEach { node ->
                val legacyMediaPackage =
                    when (val action = node.action) {
                        is ShortcutAction.OpenApplication -> (action.packageName as? InputValue.Fixed<String>)?.value
                        is ShortcutAction.WaitForMediaPlayback -> (action.packageName as? InputValue.Fixed<String>)?.value
                        else -> null
                    }
                if (legacyMediaPackage in modernMediaPackages) {
                    add(
                        RoutineValidationIssue(
                            node.id.value,
                            "mixed_modern_legacy_media",
                            "N’associez pas PLAY_MEDIA aux anciennes actions média pour la même application.",
                        ),
                    )
                }
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
