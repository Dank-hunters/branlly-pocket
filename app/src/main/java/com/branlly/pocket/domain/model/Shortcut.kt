package com.branlly.pocket.domain.model

import java.time.Instant
import java.util.UUID

@JvmInline
value class ShortcutId(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }

    companion object {
        fun new(): ShortcutId = ShortcutId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class NodeId(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }

    companion object {
        fun new(): NodeId = NodeId(UUID.randomUUID().toString())
    }
}

data class ShortcutDefinition(
    val id: ShortcutId = ShortcutId.new(),
    val name: String,
    val iconKey: String = "bolt",
    val accentColor: ShortcutAccentColor = ShortcutAccentColor.BLUE,
    val category: ShortcutCategory = ShortcutCategory.OTHER,
    val trigger: Trigger,
    val nodes: List<ActionNode>,
    val mode: EditorMode = EditorMode.SIMPLE,
    val enabled: Boolean = false,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) {
    init {
        require(name.length <= MAX_NAME_LENGTH)
        require(nodes.size <= MAX_ACTION_COUNT)
        require(schemaVersion > 0)
        require(nodes.map(ActionNode::id).distinct().size == nodes.size) { "Node IDs must be unique" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_NAME_LENGTH = 80
        const val MAX_ACTION_COUNT = 100
    }
}

enum class ShortcutCategory { TRAVEL, WELLBEING, PRODUCTIVITY, COMMUNICATION, DEVICE, OTHER }

enum class ShortcutAccentColor { BLUE, CYAN, VIOLET, PINK, RED, ORANGE, YELLOW, GREEN, MINT, WHITE, GRAY }

enum class EditorMode { SIMPLE, ADVANCED }

data class ActionNode(
    val id: NodeId = NodeId.new(),
    val action: ShortcutAction,
    val enabled: Boolean = true,
    val conditions: List<Condition> = emptyList(),
    val errorStrategy: ErrorStrategy = ErrorStrategy.Stop,
    val timeoutMillis: Long? = null,
) {
    init {
        require(conditions.size <= 20)
        require(timeoutMillis == null || timeoutMillis in 100L..300_000L)
    }
}

sealed interface ErrorStrategy {
    data object Stop : ErrorStrategy

    data object Continue : ErrorStrategy

    data class Retry(
        val attempts: Int,
        val delayMillis: Long,
    ) : ErrorStrategy {
        init {
            require(attempts in 1..5)
            require(delayMillis in 0L..60_000L)
        }
    }
}
