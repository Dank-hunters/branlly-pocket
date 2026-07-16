package com.branlly.pocket.domain

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.widgetExecutableAction
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetExecutableActionTest {
    @Test
    fun `widget accepts a configured application`() {
        val shortcut = shortcut(ShortcutAction.OpenApplication(InputValue.Fixed("com.example.app")))

        assertTrue(shortcut.widgetExecutableAction() is ShortcutAction.OpenApplication)
    }

    @Test
    fun `widget rejects an action that asks for a runtime value`() {
        val shortcut = shortcut(ShortcutAction.OpenApplication(InputValue.AskAtRuntime))

        assertNull(shortcut.widgetExecutableAction())
    }

    @Test
    fun `widget rejects a route without destination`() {
        val shortcut =
            shortcut(
                ShortcutAction.OpenRoute(
                    navigationPackage = InputValue.Fixed("com.google.android.apps.maps"),
                    destination = InputValue.Fixed(" "),
                ),
            )

        assertNull(shortcut.widgetExecutableAction())
    }

    @Test
    fun `widget does not skip an unsupported first action`() {
        val shortcut =
            ShortcutDefinition(
                name = "Séquence",
                trigger = Trigger.ManualButton,
                nodes =
                    listOf(
                        ActionNode(action = ShortcutAction.Wait(100)),
                        ActionNode(action = ShortcutAction.OpenApplication(InputValue.Fixed("com.example.app"))),
                    ),
            )

        assertNull(shortcut.widgetExecutableAction())
    }

    private fun shortcut(action: ShortcutAction): ShortcutDefinition =
        ShortcutDefinition(
            name = "Test",
            trigger = Trigger.ManualButton,
            nodes = listOf(ActionNode(action = action)),
        )
}
