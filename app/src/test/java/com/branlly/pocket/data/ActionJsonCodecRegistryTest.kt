package com.branlly.pocket.data

import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionJsonCodecRegistryTest {
    @Test
    fun `enable bluetooth V2 round trip preserves timeout`() {
        val action = ShortcutAction.EnableBluetooth(timeoutMillis = 60_000)
        assertEquals(action, ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action)))
    }

    @Test
    fun `PLAY_MEDIA codec preserves user and advanced options`() {
        val action =
            ShortcutAction.PlayMedia(
                targetAppLabel = "Player",
                targetPackage = "example.player",
                activityName = "example.player.MainActivity",
                searchQuery = "Fichu Django",
                mediaUri = "https://example.test/media",
                artist = "Django",
                preferredContentType = com.branlly.pocket.domain.model.PreferredMediaContentType.SONG,
                selectionPolicy = com.branlly.pocket.domain.model.MediaSelectionPolicy.EXACT_MATCH,
                timeoutMs = 60_000,
                allowManualFallback = false,
                errorStrategy = com.branlly.pocket.domain.model.MediaErrorStrategy.STOP_ON_FIRST_FAILURE,
            )
        assertEquals(action, ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action)))
    }

    @Test
    fun `PLAY_MEDIA codec preserves search as the only active source`() {
        val action = ShortcutAction.PlayMedia("Player", "example.player", searchQuery = "query", mediaUri = null)
        assertEquals(action, ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action)))
    }

    @Test
    fun `PLAY_MEDIA codec preserves URI as the only active source`() {
        val action = ShortcutAction.PlayMedia("Player", "example.player", searchQuery = "", mediaUri = "https://example.test/media")
        assertEquals(action, ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action)))
    }

    @Test
    fun `open application codec preserves exact technical identity`() {
        val action =
            ShortcutAction.OpenApplication(
                packageName = InputValue.Fixed("example.player"),
                searchQuery = InputValue.Fixed("django fable"),
                mediaUri = InputValue.Fixed("https://example.test/title"),
                applicationLabel = "Player",
                activityName = "example.player.MainActivity",
            )

        val decoded = ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action))

        assertEquals(action, decoded)
    }

    @Test
    fun `unsupported legacy model action remains decodable for validation`() {
        val action = ShortcutAction.Notification("Title", "Message")
        val decoded = ActionJsonCodecRegistry.DEFAULT.decode(ActionJsonCodecRegistry.DEFAULT.encode(action))
        assertTrue(decoded is ShortcutAction.Notification)
    }
}
