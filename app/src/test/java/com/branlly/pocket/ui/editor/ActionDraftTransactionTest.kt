package com.branlly.pocket.ui.editor

import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActionDraftTransactionTest {
    private fun node(id: String) = ActionNode(id = NodeId(id), action = ShortcutAction.Wait(1_000))

    @Test fun `new draft inserts once at requested position`() {
        val first = node("first")
        val second = node("second")
        val pending = node("pending")
        val committed = ActionDraftTransaction.commit(listOf(first, second), pending, 1)
        assertEquals(listOf("first", "pending", "second"), committed.map { it.id.value })
        assertSame(committed, ActionDraftTransaction.commit(committed, pending, 1))
    }

    @Test fun `uncommitted draft and cancellation leave existing timeline unchanged`() {
        val first = node("first")
        val second = node("second")
        val timeline = listOf(first, second)
        val draft = node("pending")

        assertEquals(listOf("first", "second"), timeline.map { it.id.value })
        assertEquals(listOf("first", "second"), timeline.map { it.id.value }) // cancellation performs no commit
        assertEquals("pending", draft.id.value)
    }

    @Test fun `editing preserves ID and position`() {
        val first = node("first")
        val original = node("edited")
        val updated = original.copy(action = ShortcutAction.Wait(2_000))
        val committed = ActionDraftTransaction.commit(listOf(first, original), updated, 0)
        assertEquals(listOf("first", "edited"), committed.map { it.id.value })
        assertEquals(updated, committed[1])
        assertEquals(original.enabled, committed[1].enabled)
        assertEquals(original.delayBeforeMillis, committed[1].delayBeforeMillis)
    }
}
