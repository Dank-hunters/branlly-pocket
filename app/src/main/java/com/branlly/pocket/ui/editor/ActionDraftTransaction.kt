package com.branlly.pocket.ui.editor

import com.branlly.pocket.domain.model.ActionNode

/** Pure insertion rule used by editor action drafts; an already committed draft is a no-op. */
internal object ActionDraftTransaction {
    fun commit(
        nodes: List<ActionNode>,
        draft: ActionNode,
        index: Int,
    ): List<ActionNode> {
        val existing = nodes.indexOfFirst { it.id == draft.id }
        return when {
            existing >= 0 && nodes[existing] == draft -> nodes
            existing >= 0 -> nodes.map { if (it.id == draft.id) draft else it }
            else -> nodes.toMutableList().apply { add(index.coerceIn(0, size), draft) }
        }
    }
}
