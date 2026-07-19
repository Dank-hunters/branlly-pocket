package com.branlly.pocket.domain.catalog

import com.branlly.pocket.domain.execution.ActionRegistry
import com.branlly.pocket.domain.execution.RegisteredAction
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.Trigger

data class ActionDescriptor(
    val kind: ActionKind,
    val category: ActionCategory,
    val title: String,
    val description: String,
    val createDefault: () -> ShortcutAction,
)

/** Presentation is derived from the executable registry; there is no second support list. */
fun ActionRegistry.visibleDescriptors(trigger: Trigger): List<ActionDescriptor> {
    val priorities = LocalRecommendations.forTrigger(trigger)
    return visibleActions()
        .map { registration -> registration.toDescriptor() }
        .sortedWith(
            compareBy<ActionDescriptor> { priorities.indexOf(it.kind).orLast() }
                .thenBy { it.category.ordinal },
        )
}

fun ActionRegistry.descriptor(kind: ActionKind): ActionDescriptor? =
    registration(kind)?.takeIf { it.visibleInEditor }?.toDescriptor()

private fun RegisteredAction<out ShortcutAction>.toDescriptor(): ActionDescriptor =
    ActionDescriptor(kind, category, title, description, createDefault)

private fun Int.orLast(): Int = if (this < 0) Int.MAX_VALUE else this
