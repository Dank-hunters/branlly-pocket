package com.branlly.pocket.ui.editor

import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayMediaEditorStateTest {
    private val action = ShortcutAction.PlayMedia("Player", "example.player", searchQuery = "old", mediaUri = "https://old")

    @Test
    fun `search mode retains query and clears URI`() {
        val updated = action.forSearch("precise query")
        assertEquals("precise query", updated.searchQuery)
        assertNull(updated.mediaUri)
    }

    @Test
    fun `URI mode retains URI and clears query`() {
        val updated = action.forUri("https://example.test/media")
        assertEquals("", updated.searchQuery)
        assertEquals("https://example.test/media", updated.mediaUri)
    }

    @Test
    fun `blank URI is invalid active content`() {
        val updated = action.forUri("")
        assertEquals("", updated.searchQuery)
        assertNull(updated.mediaUri)
    }
}
