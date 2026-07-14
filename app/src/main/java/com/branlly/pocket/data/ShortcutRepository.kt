package com.branlly.pocket.data

import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface ShortcutRepository {
    fun observeAll(): Flow<List<ShortcutDefinition>>
    fun observe(id: ShortcutId): Flow<ShortcutDefinition?>
    suspend fun save(shortcut: ShortcutDefinition)
    suspend fun delete(id: ShortcutId)
}

/** Stockage temporaire de phase 1. Le contrat permet de brancher Room chiffré sans toucher au domaine. */
class InMemoryShortcutRepository : ShortcutRepository {
    private val state = MutableStateFlow<Map<ShortcutId, ShortcutDefinition>>(emptyMap())

    override fun observeAll(): Flow<List<ShortcutDefinition>> = state.map { it.values.sortedBy(ShortcutDefinition::name) }
    override fun observe(id: ShortcutId): Flow<ShortcutDefinition?> = state.map { it[id] }

    override suspend fun save(shortcut: ShortcutDefinition) {
        state.update { it + (shortcut.id to shortcut) }
    }

    override suspend fun delete(id: ShortcutId) {
        state.update { it - id }
    }
}
