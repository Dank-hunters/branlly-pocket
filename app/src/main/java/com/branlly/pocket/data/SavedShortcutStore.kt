package com.branlly.pocket.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.branlly.pocket.domain.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.shortcutDataStore by preferencesDataStore(name = "saved_shortcuts")

sealed interface SavedShortcut {
    val id: String
    val name: String
}

data class SavedRouteShortcut(
    override val id: String,
    override val name: String,
    val navigationPackage: String,
    val destination: String,
    val transportMode: TransportMode,
) : SavedShortcut

data class SavedApplicationShortcut(
    override val id: String,
    override val name: String,
    val packageName: String,
) : SavedShortcut

/** Persistance atomique, privée à l'application et exclue des sauvegardes Android. */
class SavedShortcutStore(context: Context) {
    private val dataStore = context.applicationContext.shortcutDataStore

    val shortcuts: Flow<List<SavedShortcut>> = dataStore.data.map { preferences ->
        decode(preferences[SHORTCUTS].orEmpty())
    }

    suspend fun save(shortcut: SavedShortcut) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SHORTCUTS].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) current[index] = shortcut else if (current.size < MAX_SHORTCUTS) current += shortcut
            preferences[SHORTCUTS] = encode(current)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            preferences[SHORTCUTS] = encode(decode(preferences[SHORTCUTS].orEmpty()).filterNot { it.id == id })
        }
    }

    private fun encode(shortcuts: List<SavedShortcut>): String = JSONArray().apply {
        shortcuts.forEach { shortcut ->
            put(
                when (shortcut) {
                    is SavedRouteShortcut -> JSONObject()
                        .put("type", TYPE_ROUTE)
                        .put("id", shortcut.id)
                        .put("name", shortcut.name)
                        .put("navigationPackage", shortcut.navigationPackage)
                        .put("destination", shortcut.destination)
                        .put("transportMode", shortcut.transportMode.name)
                    is SavedApplicationShortcut -> JSONObject()
                        .put("type", TYPE_APPLICATION)
                        .put("id", shortcut.id)
                        .put("name", shortcut.name)
                        .put("packageName", shortcut.packageName)
                },
            )
        }
    }.toString()

    private fun decode(raw: String): List<SavedShortcut> {
        if (raw.isBlank() || raw.length > MAX_STORAGE_LENGTH) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(minOf(array.length(), MAX_SHORTCUTS)) { index ->
                    val item = array.getJSONObject(index)
                    val id = item.getString("id").take(MAX_ID_LENGTH)
                    val name = item.getString("name").take(MAX_NAME_LENGTH)
                    if (id.isBlank() || name.isBlank()) return@repeat
                    when (item.optString("type", TYPE_ROUTE)) {
                        TYPE_ROUTE -> {
                            val destination = item.getString("destination").take(MAX_DESTINATION_LENGTH)
                            if (destination.isNotBlank()) {
                                add(
                                    SavedRouteShortcut(
                                        id = id,
                                        name = name,
                                        navigationPackage = item.getString("navigationPackage").take(MAX_PACKAGE_LENGTH),
                                        destination = destination,
                                        transportMode = TransportMode.valueOf(item.getString("transportMode")),
                                    ),
                                )
                            }
                        }
                        TYPE_APPLICATION -> {
                            val packageName = item.getString("packageName").take(MAX_PACKAGE_LENGTH)
                            if (packageName.isNotBlank()) add(SavedApplicationShortcut(id, name, packageName))
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val SHORTCUTS = stringPreferencesKey("route_shortcuts_v1")
        private const val TYPE_ROUTE = "route"
        private const val TYPE_APPLICATION = "application"
        private const val MAX_SHORTCUTS = 50
        private const val MAX_STORAGE_LENGTH = 100_000
        private const val MAX_ID_LENGTH = 80
        private const val MAX_NAME_LENGTH = 80
        private const val MAX_PACKAGE_LENGTH = 255
        private const val MAX_DESTINATION_LENGTH = 500
    }
}
