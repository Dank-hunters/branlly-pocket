package com.branlly.pocket.domain

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.validation.DeviceCapabilities
import com.branlly.pocket.domain.validation.ShortcutValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutValidatorTest {
    private val validator = ShortcutValidator(
        capabilities = object : DeviceCapabilities {
            override fun isPackageInstalled(packageName: String) = packageName == "installed.app"
            override fun isTriggerAvailable(trigger: Trigger) = true
        },
        shortcutExists = { false },
    )

    @Test
    fun `rejects cleartext and credential URLs`() {
        listOf("http://example.org", "https://user:pass@example.org").forEach { url ->
            val result = validator.validate(shortcutWith(ShortcutAction.OpenWebsite(InputValue.Fixed(url))))
            assertTrue(result.any { it.code == "unsafe_url" })
        }
    }

    @Test
    fun `accepts a normal HTTPS URL`() {
        val result = validator.validate(shortcutWith(ShortcutAction.OpenWebsite(InputValue.Fixed("https://example.org/path"))))
        assertEquals(emptyList<com.branlly.pocket.domain.validation.ValidationIssue>(), result)
    }

    private fun shortcutWith(action: ShortcutAction) = ShortcutDefinition(
        name = "Test",
        trigger = Trigger.ManualButton,
        nodes = listOf(ActionNode(action = action)),
    )
}
