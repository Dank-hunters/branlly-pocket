package com.branlly.pocket.ui.editor

import com.branlly.pocket.domain.model.MediaLaunchMode
import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayMediaEditorStateTest {
    private val action = ShortcutAction.PlayMedia("Player", "example.player", searchQuery = "old", mediaUri = "https://old")

    @Test
    fun `new PLAY_MEDIA action defaults to automatic launch`() {
        assertEquals(MediaLaunchMode.AUTOMATIC, ShortcutAction.PlayMedia("", "", searchQuery = "").launchMode)
    }

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
    fun `manual package produces the same media model as normal selection`() {
        val manuallySelected = action.forManualPackage("example.player").forSearch("precise query")
        val normallySelected = action.copy(targetAppLabel = "example.player", targetPackage = "example.player").forSearch("precise query")

        assertEquals(normallySelected, manuallySelected)
        assertNull(manuallySelected.mediaUri)
    }

    @Test
    fun `blank URI is invalid active content`() {
        val updated = action.forUri("")
        assertEquals("", updated.searchQuery)
        assertNull(updated.mediaUri)
    }
}
