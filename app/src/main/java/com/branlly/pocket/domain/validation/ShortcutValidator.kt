package com.branlly.pocket.domain.validation

import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.Trigger

interface DeviceCapabilities {
    fun isPackageInstalled(packageName: String): Boolean
    fun isTriggerAvailable(trigger: Trigger): Boolean
}

data class ValidationIssue(
    val severity: Severity,
    val code: String,
    val message: String,
    val nodeId: NodeId? = null,
)

enum class Severity { ERROR, WARNING }

class ShortcutValidator(
    private val capabilities: DeviceCapabilities,
    private val shortcutExists: (ShortcutId) -> Boolean,
) {
    fun validate(shortcut: ShortcutDefinition): List<ValidationIssue> = buildList {
        if (shortcut.name.isBlank()) error("missing_name", "Donnez un nom au raccourci.")
        if (shortcut.nodes.isEmpty()) error("missing_action", "Ajoutez au moins une action.")
        if (!capabilities.isTriggerAvailable(shortcut.trigger)) {
            error("trigger_unavailable", "Ce déclencheur n’est pas disponible sur cet appareil.")
        }
        shortcut.nodes.forEach { node ->
            when (val action = node.action) {
                is ShortcutAction.OpenApplication -> checkPackage(action.packageName, node.id)
                is ShortcutAction.OpenRoute -> checkPackage(action.navigationPackage, node.id)
                is ShortcutAction.OpenWebsite -> if (action.url is InputValue.Fixed && !isSafeHttps(action.url.value)) {
                    error("unsafe_url", "Seules les adresses HTTPS valides sont autorisées.", node.id)
                }
                is ShortcutAction.SetVolume -> checkPercent(action.percent, node.id)
                is ShortcutAction.SetBrightness -> checkPercent(action.percent, node.id)
                is ShortcutAction.RunShortcut -> if (!shortcutExists(action.shortcutId)) {
                    error("missing_shortcut", "Le sous-raccourci sélectionné n’existe plus.", node.id)
                }
                else -> Unit
            }
        }
    }

    private fun MutableList<ValidationIssue>.checkPackage(value: InputValue<String>, nodeId: NodeId) {
        if (value is InputValue.Fixed && !capabilities.isPackageInstalled(value.value)) {
            error("package_missing", "L’application sélectionnée n’est pas installée.", nodeId)
        }
    }

    private fun MutableList<ValidationIssue>.checkPercent(value: InputValue<Int>, nodeId: NodeId) {
        if (value is InputValue.Fixed && value.value !in 0..100) {
            error("invalid_percent", "La valeur doit être comprise entre 0 et 100 %.", nodeId)
        }
    }

    private fun MutableList<ValidationIssue>.error(code: String, message: String, nodeId: NodeId? = null) {
        add(ValidationIssue(Severity.ERROR, code, message, nodeId))
    }

    private fun isSafeHttps(raw: String): Boolean = runCatching {
        val uri = java.net.URI(raw)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
