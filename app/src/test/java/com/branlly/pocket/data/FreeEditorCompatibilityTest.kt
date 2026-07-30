package com.branlly.pocket.data

import com.branlly.pocket.domain.model.EditorMode
import com.branlly.pocket.ui.editor.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeEditorCompatibilityTest {
    @Test fun `historical editor modes normalize to free editor`() {
        assertEquals(EditorMode.ADVANCED, normalizeStoredEditorMode("SIMPLE"))
        assertEquals(EditorMode.ADVANCED, normalizeStoredEditorMode("ADVANCED"))
        assertEquals(EditorMode.ADVANCED, normalizeStoredEditorMode("BLUEPRINT"))
    }

    @Test fun `only home and free editor routes remain active`() {
        assertEquals(listOf(Screen.HOME, Screen.EDITOR), Screen.entries)
    }
}
