package com.branlly.pocket.domain.model

/** Retourne uniquement l'action qu'un widget Android peut lancer sans interaction supplémentaire. */
fun ShortcutDefinition.widgetExecutableAction(): ShortcutAction? {
    val action = nodes.firstOrNull(ActionNode::enabled)?.action ?: return null
    return when (action) {
        is ShortcutAction.OpenApplication -> {
            action.takeIf {
                (it.packageName as? InputValue.Fixed<String>)?.value?.isNotBlank() == true
            }
        }

        is ShortcutAction.OpenRoute -> {
            action.takeIf {
                (it.navigationPackage as? InputValue.Fixed<String>)?.value?.isNotBlank() == true &&
                    (it.destination as? InputValue.Fixed<String>)?.value?.trim()?.isNotBlank() == true
            }
        }

        else -> {
            null
        }
    }
}
