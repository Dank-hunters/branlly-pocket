package com.branlly.pocket.domain

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutModelTest {
    @Test
    fun `blueprints and editor actions share the same model`() {
        val shortcut =
            ShortcutDefinition(
                name = "Test",
                trigger = Trigger.ManualButton,
                nodes = listOf(ActionNode(action = ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70)))),
            )

        assertTrue(shortcut.nodes.single().action is ShortcutAction.SetVolume)
        assertEquals(ShortcutDefinition.CURRENT_SCHEMA_VERSION, shortcut.schemaVersion)
    }

    @Test
    fun `media playback wait is an explicit action`() {
        val action = ShortcutAction.WaitForMediaPlayback(InputValue.Fixed("example.media.player"))
        assertEquals(com.branlly.pocket.domain.model.ActionKind.WAIT_FOR_MEDIA_PLAYBACK, action.kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wait duration is bounded`() {
        ShortcutAction.Wait(Long.MAX_VALUE)
    }
}
