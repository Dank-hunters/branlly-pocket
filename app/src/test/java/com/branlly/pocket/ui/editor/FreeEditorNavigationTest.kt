package com.branlly.pocket.ui.editor

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeEditorNavigationTest {
    @Test fun `creation opens a new free draft and preserves saved routines`() {
        val saved =
            ShortcutDefinition(
                name = "Existante",
                trigger = Trigger.ManualButton,
                nodes = listOf(ActionNode(action = ShortcutAction.EnableBluetooth())),
            )
        val result = EditorUiState(savedShortcuts = listOf(saved)).openFreeEditor(Trigger.ManualButton)

        assertEquals(Screen.EDITOR, result.screen)
        assertEquals(listOf(saved), result.savedShortcuts)
        assertNotNull(result.draft)
        assertTrue(result.draft!!.nodes.isEmpty())
    }
}
