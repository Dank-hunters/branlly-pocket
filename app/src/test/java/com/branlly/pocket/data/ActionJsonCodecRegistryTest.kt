package com.branlly.pocket.data

import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionJsonCodecRegistryTest {
    @Test
    fun `open application codec preserves exact technical identity`() {
        val action = ShortcutAction.OpenApplication(
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
