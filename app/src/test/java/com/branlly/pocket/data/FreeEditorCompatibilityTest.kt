package com.branlly.pocket.data

import com.branlly.pocket.ui.editor.Screen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FreeEditorCompatibilityTest {
    @Test fun `historical mode metadata is removed without changing nodes`() {
        val nodes =
            JSONArray()
                .put(JSONObject().put("id", "first").put("delayBeforeMillis", 400))
                .put(JSONObject().put("id", "second").put("enabled", false))
        val stored = JSONObject().put("mode", "BLUEPRINT").put("nodes", nodes)

        val migrated = stored.migrateLegacyEditorMode()

        assertFalse(migrated.has("mode"))
        assertEquals(nodes.toString(), migrated.getJSONArray("nodes").toString())
    }

    @Test fun `only home and free editor routes remain active`() {
        assertEquals(listOf(Screen.HOME, Screen.EDITOR), Screen.entries)
    }
}
