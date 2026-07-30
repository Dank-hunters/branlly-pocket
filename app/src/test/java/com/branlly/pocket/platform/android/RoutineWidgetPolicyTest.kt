package com.branlly.pocket.platform.android

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineWidgetPolicyTest {
    @Test
    fun `selection preserves order and applies widget limits`() {
        val routines = listOf("one", "two", "three", "four", "five")
        val four = WidgetSelectionPolicy(4)
        val three = WidgetSelectionPolicy(3)

        assertEquals(routines.take(4), routines.fold(emptyList<String>()) { selected, id -> four.toggle(selected, id) })
        assertEquals(routines.take(3), routines.fold(emptyList<String>()) { selected, id -> three.toggle(selected, id) })
    }

    @Test
    fun `widget instances use independent persistence keys`() {
        assertNotEquals(WidgetPreferenceKeys.routineIds(15), WidgetPreferenceKeys.routineIds(16))
        assertNotEquals(WidgetPreferenceKeys.type(15), WidgetPreferenceKeys.type(16))
    }

    @Test
    fun `unknown or deleted routine is removed from resolved selection`() {
        val available = listOf(routine("known"))

        assertEquals(listOf("known"), WidgetRoutineResolver.resolve(listOf("known", "deleted"), available, 4).map { it.id.value })
    }

    @Test
    fun `routine without actions is refused`() {
        val empty = ShortcutDefinition(id = ShortcutId("empty"), name = "Empty", trigger = Trigger.ManualButton, nodes = emptyList())

        assertTrue(WidgetRoutineResolver.resolve(listOf("empty"), listOf(empty), 4).isEmpty())
    }

    @Test
    fun `run actions are distinct per widget and routine`() {
        val first = RoutineWidgetIntents.run(11, "alpha")
        val second = RoutineWidgetIntents.run(12, "alpha")
        val third = RoutineWidgetIntents.run(11, "beta")

        assertNotEquals(first.requestCode, second.requestCode)
        assertNotEquals(first.requestCode, third.requestCode)
        assertEquals(RoutineWidgetIntents.ACTION_RUN_ROUTINE, first.action)
    }

    @Test
    fun `create action targets root create route`() {
        val action = RoutineWidgetIntents.openCreate(31)

        assertEquals(RoutineWidgetIntents.ACTION_OPEN_CREATE, action.action)
        assertEquals(null, action.routineId)
        assertFalse(action.action == RoutineWidgetIntents.ACTION_RUN_ROUTINE)
    }

    private fun routine(id: String): ShortcutDefinition =
        ShortcutDefinition(
            id = ShortcutId(id),
            name = id,
            trigger = Trigger.ManualButton,
            nodes = listOf(ActionNode(action = ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(30)))),
        )
}
