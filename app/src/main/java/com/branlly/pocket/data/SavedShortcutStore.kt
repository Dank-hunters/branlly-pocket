package com.branlly.pocket.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ChargerEvent
import com.branlly.pocket.domain.model.Condition
import com.branlly.pocket.domain.model.ConnectionEvent
import com.branlly.pocket.domain.model.EditorMode
import com.branlly.pocket.domain.model.ErrorStrategy
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.LogicalOperator
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.NumericComparison
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAccentColor
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutCategory
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.TransportMode
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.TriggerValueKey
import com.branlly.pocket.domain.model.VolumeStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

private val Context.shortcutDataStore by preferencesDataStore(name = "saved_shortcuts")

/** Persistance atomique, privée à l'application et exclue des sauvegardes Android. */
class SavedShortcutStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.shortcutDataStore

    val shortcuts: Flow<List<ShortcutDefinition>> =
        dataStore.data.map { preferences ->
            decode(preferences[SHORTCUTS] ?: preferences[LEGACY_SHORTCUTS].orEmpty())
        }

    suspend fun save(shortcut: ShortcutDefinition) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SHORTCUTS] ?: preferences[LEGACY_SHORTCUTS].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) {
                current[index] = shortcut
            } else if (current.size < MAX_SHORTCUTS) {
                current += shortcut
            }
            preferences[SHORTCUTS] = encode(current)
        }
    }

    suspend fun delete(id: ShortcutId) {
        dataStore.edit { preferences ->
            preferences[SHORTCUTS] =
                encode(
                    decode(preferences[SHORTCUTS] ?: preferences[LEGACY_SHORTCUTS].orEmpty()).filterNot { it.id == id },
                )
        }
    }

    private fun encode(shortcuts: List<ShortcutDefinition>): String =
        JSONArray()
            .apply {
                shortcuts.forEach { put(encodeDefinition(it)) }
            }.toString()

    private fun decode(raw: String): List<ShortcutDefinition> {
        if (raw.isBlank() || raw.length > MAX_STORAGE_LENGTH) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(minOf(array.length(), MAX_SHORTCUTS)) { index ->
                    decodeDefinition(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeDefinition(definition: ShortcutDefinition): JSONObject =
        JSONObject()
            .put("version", CURRENT_STORAGE_VERSION)
            .put("id", definition.id.value)
            .put("name", definition.name)
            .put("iconKey", definition.iconKey)
            .put("widgetLabel", definition.widgetLabel)
            .put("accentColor", definition.accentColor.name)
            .put("category", definition.category.name)
            .put("trigger", encodeTrigger(definition.trigger))
            .put("nodes", JSONArray().apply { definition.nodes.forEach { put(encodeNode(it)) } })
            .put("mode", definition.mode.name)
            .put("enabled", definition.enabled)
            .put("schemaVersion", definition.schemaVersion)
            .put("createdAt", definition.createdAt.toString())
            .put("updatedAt", definition.updatedAt.toString())

    private fun decodeDefinition(value: JSONObject?): ShortcutDefinition? =
        runCatching {
            val item = value ?: return null
            if (item.optInt("version", LEGACY_STORAGE_VERSION) > CURRENT_STORAGE_VERSION) return null
            if (!item.has("version")) return decodeLegacy(item)
            val nodes = item.optJSONArray("nodes") ?: return null
            ShortcutDefinition(
                id = ShortcutId(item.requiredString("id", MAX_ID_LENGTH)),
                name = item.requiredString("name", ShortcutDefinition.MAX_NAME_LENGTH),
                iconKey = item.optString("iconKey", "bolt").take(MAX_ICON_KEY_LENGTH),
                widgetLabel = item.optStringOrNull("widgetLabel")?.take(ShortcutDefinition.MAX_WIDGET_LABEL_LENGTH),
                accentColor = enumValue(item.optString("accentColor"), ShortcutAccentColor.BLUE),
                category = enumValue(item.optString("category"), ShortcutCategory.OTHER),
                trigger = decodeTrigger(item.optJSONObject("trigger")) ?: return null,
                nodes =
                    buildList {
                        repeat(minOf(nodes.length(), ShortcutDefinition.MAX_ACTION_COUNT)) { index ->
                            decodeNode(nodes.optJSONObject(index))?.let(::add)
                        }
                    },
                mode = enumValue(item.optString("mode"), EditorMode.SIMPLE),
                enabled = item.optBoolean("enabled", false),
                schemaVersion = item.optInt("schemaVersion", ShortcutDefinition.CURRENT_SCHEMA_VERSION),
                createdAt = instant(item.optString("createdAt")),
                updatedAt = instant(item.optString("updatedAt")),
            )
        }.getOrNull()

    /** Migration du schéma v1 : un itinéraire ou une application devenait un raccourci à une action. */
    private fun decodeLegacy(item: JSONObject): ShortcutDefinition? =
        runCatching {
            val id = ShortcutId(item.requiredString("id", MAX_ID_LENGTH))
            val name = item.requiredString("name", ShortcutDefinition.MAX_NAME_LENGTH)
            val action =
                when (item.optString("type", TYPE_ROUTE)) {
                    TYPE_ROUTE -> {
                        ShortcutAction.OpenRoute(
                            InputValue.Fixed(item.requiredString("navigationPackage", MAX_PACKAGE_LENGTH)),
                            InputValue.Fixed(item.requiredString("destination", MAX_TEXT_LENGTH)),
                            enumValue(item.optString("transportMode"), TransportMode.DRIVING),
                        )
                    }

                    TYPE_APPLICATION -> {
                        ShortcutAction.OpenApplication(
                            InputValue.Fixed(item.requiredString("packageName", MAX_PACKAGE_LENGTH)),
                        )
                    }

                    else -> {
                        return null
                    }
                }
            ShortcutDefinition(
                id = id,
                name = name,
                category = if (action is ShortcutAction.OpenRoute) ShortcutCategory.TRAVEL else ShortcutCategory.OTHER,
                trigger = Trigger.ManualButton,
                nodes = listOf(ActionNode(action = action)),
            )
        }.getOrNull()

    private fun encodeNode(node: ActionNode): JSONObject =
        JSONObject()
            .put("id", node.id.value)
            .put("action", encodeAction(node.action))
            .put("enabled", node.enabled)
            .put("conditions", JSONArray().apply { node.conditions.forEach { put(encodeCondition(it)) } })
            .put("errorStrategy", encodeErrorStrategy(node.errorStrategy))
            .put("timeoutMillis", node.timeoutMillis)

    private fun decodeNode(item: JSONObject?): ActionNode? =
        runCatching {
            val value = item ?: return null
            val conditions = value.optJSONArray("conditions")
            ActionNode(
                id = NodeId(value.requiredString("id", MAX_ID_LENGTH)),
                action = decodeAction(value.optJSONObject("action")) ?: return null,
                enabled = value.optBoolean("enabled", true),
                conditions =
                    buildList {
                        if (conditions != null) {
                            repeat(minOf(conditions.length(), MAX_CONDITIONS)) { index ->
                                decodeCondition(conditions.optJSONObject(index), 0)?.let(::add)
                            }
                        }
                    },
                errorStrategy = decodeErrorStrategy(value.optJSONObject("errorStrategy")),
                timeoutMillis = value.optLong("timeoutMillis").takeIf { it in 100L..300_000L },
            )
        }.getOrNull()

    private fun encodeTrigger(trigger: Trigger): JSONObject =
        JSONObject()
            .put(
                "type",
                when (trigger) {
                    Trigger.ManualButton -> "manual"
                    is Trigger.Time -> "time"
                    is Trigger.Bluetooth -> "bluetooth"
                    is Trigger.Wifi -> "wifi"
                    is Trigger.Charger -> "charger"
                    is Trigger.BatteryLevel -> "battery"
                    is Trigger.Nfc -> "nfc"
                    Trigger.Widget -> "widget"
                    Trigger.QuickTile -> "quickTile"
                },
            ).apply {
                when (trigger) {
                    is Trigger.Time -> {
                        put("time", trigger.time.toString())
                        put("days", JSONArray(trigger.days.map(DayOfWeek::name)))
                    }

                    is Trigger.Bluetooth -> {
                        put("address", trigger.deviceAddress)
                        put("name", trigger.deviceName)
                        put("event", trigger.event.name)
                        put("delay", trigger.delayMillis)
                        put("confirmation", trigger.requiresConfirmation)
                    }

                    is Trigger.Wifi -> {
                        put("ssid", trigger.ssid)
                        put("event", trigger.event.name)
                    }

                    is Trigger.Charger -> {
                        put("event", trigger.event.name)
                    }

                    is Trigger.BatteryLevel -> {
                        put("threshold", trigger.thresholdPercent)
                        put("comparison", trigger.comparison.name)
                    }

                    is Trigger.Nfc -> {
                        put("tag", trigger.localTagId)
                    }

                    else -> {
                        Unit
                    }
                }
            }

    private fun decodeTrigger(item: JSONObject?): Trigger? =
        runCatching {
            val value = item ?: return null
            when (value.optString("type")) {
                "manual" -> {
                    Trigger.ManualButton
                }

                "time" -> {
                    Trigger.Time(LocalTime.parse(value.requiredString("time", 16)), decodeDays(value.optJSONArray("days")))
                }

                "bluetooth" -> {
                    Trigger.Bluetooth(
                        value.optStringOrNull("address"),
                        value.optStringOrNull("name"),
                        enumValue(value.optString("event"), ConnectionEvent.CONNECTED),
                        value.optLong("delay", 0),
                        value.optBoolean("confirmation", false),
                    )
                }

                "wifi" -> {
                    Trigger.Wifi(
                        value.requiredString("ssid", MAX_TEXT_LENGTH),
                        enumValue(value.optString("event"), ConnectionEvent.CONNECTED),
                    )
                }

                "charger" -> {
                    Trigger.Charger(enumValue(value.optString("event"), ChargerEvent.PLUGGED))
                }

                "battery" -> {
                    Trigger.BatteryLevel(
                        value.optInt("threshold"),
                        enumValue(value.optString("comparison"), NumericComparison.LESS_THAN),
                    )
                }

                "nfc" -> {
                    Trigger.Nfc(value.optStringOrNull("tag"))
                }

                "widget" -> {
                    Trigger.Widget
                }

                "quickTile" -> {
                    Trigger.QuickTile
                }

                else -> {
                    null
                }
            }
        }.getOrNull()

    private fun encodeAction(action: ShortcutAction): JSONObject =
        JSONObject().put("type", action.kind.name).apply {
            when (action) {
                is ShortcutAction.OpenApplication -> {
                    put("package", encodeInput(action.packageName))
                    action.searchQuery?.let { put("searchQuery", encodeInput(it)) }
                }

                is ShortcutAction.OpenWebsite -> {
                    put("url", encodeInput(action.url))
                }

                is ShortcutAction.OpenRoute -> {
                    put("package", encodeInput(action.navigationPackage))
                    put("destination", encodeInput(action.destination))
                    put("transport", action.transportMode.name)
                }

                is ShortcutAction.OpenSettings -> {
                    put("panel", action.panel.name)
                }

                is ShortcutAction.SetVolume -> {
                    put("stream", action.stream.name)
                    put("percent", encodeInput(action.percent))
                    put("restore", action.restoreAfterExecution)
                }

                is ShortcutAction.SetBrightness -> {
                    put("percent", encodeInput(action.percent))
                }

                is ShortcutAction.SetSoundMode -> {
                    put("mode", action.mode.name)
                }

                is ShortcutAction.PrepareSms -> {
                    put("contact", encodeInput(action.contact))
                    put("message", encodeInput(action.message))
                    put("confirmation", action.confirmationRequired)
                }

                is ShortcutAction.CallContact -> {
                    put("contact", encodeInput(action.contact))
                }

                is ShortcutAction.CopyText -> {
                    put("text", encodeInput(action.text))
                }

                is ShortcutAction.Wait -> {
                    put("duration", action.durationMillis)
                    put("cancellable", action.cancellable)
                }

                is ShortcutAction.Checklist -> {
                    put("items", JSONArray(action.items))
                }

                is ShortcutAction.Notification -> {
                    put("title", action.title)
                    put("message", action.message)
                }

                is ShortcutAction.Confirmation -> {
                    put("message", action.message)
                }

                is ShortcutAction.RunShortcut -> {
                    put("shortcutId", action.shortcutId.value)
                }

                ShortcutAction.StopExecution -> {
                    Unit
                }
            }
        }

    private fun decodeAction(item: JSONObject?): ShortcutAction? =
        runCatching {
            val value = item ?: return null
            when (enumOrNull<com.branlly.pocket.domain.model.ActionKind>(value.optString("type"))) {
                com.branlly.pocket.domain.model.ActionKind.OPEN_APPLICATION -> {
                    ShortcutAction.OpenApplication(
                        packageName = decodeStringInput(value.optJSONObject("package")) ?: return null,
                        searchQuery = value.optJSONObject("searchQuery")?.let(::decodeStringInput),
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.OPEN_WEBSITE -> {
                    ShortcutAction.OpenWebsite(decodeStringInput(value.optJSONObject("url")) ?: return null)
                }

                com.branlly.pocket.domain.model.ActionKind.OPEN_ROUTE -> {
                    ShortcutAction.OpenRoute(
                        decodeStringInput(value.optJSONObject("package")) ?: return null,
                        decodeStringInput(value.optJSONObject("destination")) ?: return null,
                        enumValue(value.optString("transport"), TransportMode.DRIVING),
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.OPEN_SETTINGS -> {
                    ShortcutAction.OpenSettings(enumValue(value.optString("panel"), SettingsPanel.WIFI))
                }

                com.branlly.pocket.domain.model.ActionKind.SET_VOLUME -> {
                    ShortcutAction.SetVolume(
                        enumValue(value.optString("stream"), VolumeStream.MEDIA),
                        decodeIntInput(value.optJSONObject("percent")) ?: return null,
                        value.optBoolean("restore", false),
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.SET_BRIGHTNESS -> {
                    ShortcutAction.SetBrightness(decodeIntInput(value.optJSONObject("percent")) ?: return null)
                }

                com.branlly.pocket.domain.model.ActionKind.SET_SOUND_MODE -> {
                    ShortcutAction.SetSoundMode(enumValue(value.optString("mode"), SoundMode.NORMAL))
                }

                com.branlly.pocket.domain.model.ActionKind.PREPARE_SMS -> {
                    ShortcutAction.PrepareSms(
                        decodeStringInput(value.optJSONObject("contact")) ?: return null,
                        decodeStringInput(value.optJSONObject("message")) ?: return null,
                        value.optBoolean("confirmation", true),
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.CALL_CONTACT -> {
                    ShortcutAction.CallContact(decodeStringInput(value.optJSONObject("contact")) ?: return null)
                }

                com.branlly.pocket.domain.model.ActionKind.COPY_TEXT -> {
                    ShortcutAction.CopyText(decodeStringInput(value.optJSONObject("text")) ?: return null)
                }

                com.branlly.pocket.domain.model.ActionKind.WAIT -> {
                    ShortcutAction.Wait(value.optLong("duration"), value.optBoolean("cancellable", true))
                }

                com.branlly.pocket.domain.model.ActionKind.CHECKLIST -> {
                    ShortcutAction.Checklist(
                        value.optJSONArray("items")?.let { array ->
                            List(minOf(array.length(), 50)) { index -> array.optString(index).take(MAX_TEXT_LENGTH) }
                        }
                            ?: return null,
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.NOTIFICATION -> {
                    ShortcutAction.Notification(
                        value.requiredString("title", MAX_TEXT_LENGTH),
                        value.requiredString("message", MAX_TEXT_LENGTH),
                    )
                }

                com.branlly.pocket.domain.model.ActionKind.CONFIRMATION -> {
                    ShortcutAction.Confirmation(value.requiredString("message", MAX_TEXT_LENGTH))
                }

                com.branlly.pocket.domain.model.ActionKind.RUN_SHORTCUT -> {
                    ShortcutAction.RunShortcut(ShortcutId(value.requiredString("shortcutId", MAX_ID_LENGTH)))
                }

                com.branlly.pocket.domain.model.ActionKind.STOP_EXECUTION -> {
                    ShortcutAction.StopExecution
                }

                null -> {
                    null
                }
            }
        }.getOrNull()

    private fun encodeInput(input: InputValue<*>): JSONObject =
        JSONObject().apply {
            when (input) {
                is InputValue.Fixed<*> -> {
                    put("type", "fixed")
                    put("value", input.value)
                }

                InputValue.AskAtRuntime -> {
                    put("type", "ask")
                }

                is InputValue.FromTrigger -> {
                    put("type", "trigger")
                    put("key", input.key.name)
                }
            }
        }

    private fun decodeStringInput(item: JSONObject?): InputValue<String>? = decodeInput(item) { it as? String }

    private fun decodeIntInput(item: JSONObject?): InputValue<Int>? = decodeInput(item) { (it as? Number)?.toInt() }

    private fun <T> decodeInput(
        item: JSONObject?,
        fixed: (Any?) -> T?,
    ): InputValue<T>? {
        val value = item ?: return null
        return when (value.optString("type")) {
            "fixed" -> fixed(value.opt("value"))?.let { InputValue.Fixed(it) }
            "ask" -> InputValue.AskAtRuntime
            "trigger" -> InputValue.FromTrigger(enumValue(value.optString("key"), TriggerValueKey.DEVICE_NAME))
            else -> null
        }
    }

    private fun encodeErrorStrategy(strategy: ErrorStrategy): JSONObject =
        JSONObject().apply {
            when (strategy) {
                ErrorStrategy.Stop -> {
                    put("type", "stop")
                }

                ErrorStrategy.Continue -> {
                    put("type", "continue")
                }

                is ErrorStrategy.Retry -> {
                    put("type", "retry")
                    put("attempts", strategy.attempts)
                    put("delay", strategy.delayMillis)
                }
            }
        }

    private fun decodeErrorStrategy(item: JSONObject?): ErrorStrategy =
        when (item?.optString("type")) {
            "continue" -> ErrorStrategy.Continue
            "retry" -> runCatching { ErrorStrategy.Retry(item.optInt("attempts"), item.optLong("delay")) }.getOrDefault(ErrorStrategy.Stop)
            else -> ErrorStrategy.Stop
        }

    private fun encodeCondition(condition: Condition): JSONObject =
        JSONObject().apply {
            when (condition) {
                is Condition.Battery -> {
                    put("type", "battery")
                    put("comparison", condition.comparison.name)
                    put("percent", condition.percent)
                }

                is Condition.WifiConnected -> {
                    put("type", "wifi")
                    put("ssid", condition.ssid)
                }

                is Condition.BluetoothConnected -> {
                    put("type", "bluetooth")
                    put("address", condition.deviceAddress)
                }

                is Condition.DayMatches -> {
                    put("type", "days")
                    put("days", JSONArray(condition.days.map(DayOfWeek::name)))
                }

                is Condition.Group -> {
                    put("type", "group")
                    put("operator", condition.operator.name)
                    put("members", JSONArray().apply { condition.members.forEach { put(encodeCondition(it)) } })
                }
            }
        }

    private fun decodeCondition(
        item: JSONObject?,
        depth: Int,
    ): Condition? =
        runCatching {
            if (depth > MAX_CONDITION_DEPTH) return null
            val value = item ?: return null
            when (value.optString("type")) {
                "battery" -> {
                    Condition.Battery(enumValue(value.optString("comparison"), NumericComparison.EQUAL), value.optInt("percent"))
                }

                "wifi" -> {
                    Condition.WifiConnected(value.requiredString("ssid", MAX_TEXT_LENGTH))
                }

                "bluetooth" -> {
                    Condition.BluetoothConnected(value.requiredString("address", 17))
                }

                "days" -> {
                    Condition.DayMatches(decodeDays(value.optJSONArray("days")))
                }

                "group" -> {
                    val members = value.optJSONArray("members") ?: return null
                    Condition.Group(
                        enumValue(value.optString("operator"), LogicalOperator.AND),
                        buildList {
                            repeat(minOf(members.length(), MAX_CONDITIONS)) { index ->
                                decodeCondition(
                                    members.optJSONObject(index),
                                    depth + 1,
                                )?.let(::add)
                            }
                        },
                    )
                }

                else -> {
                    null
                }
            }
        }.getOrNull()

    private fun decodeDays(array: JSONArray?): Set<DayOfWeek> =
        buildSet {
            if (array != null) {
                repeat(minOf(array.length(), DayOfWeek.entries.size)) { index ->
                    runCatching { DayOfWeek.valueOf(array.getString(index)) }.getOrNull()?.let(::add)
                }
            }
        }.ifEmpty { DayOfWeek.entries.toSet() }

    private fun JSONObject.requiredString(
        key: String,
        maxLength: Int,
    ): String =
        getString(key).trim().take(maxLength).also {
            require(it.isNotBlank())
        }

    private fun JSONObject.optStringOrNull(key: String): String? = optString(key).trim().take(MAX_TEXT_LENGTH).ifBlank { null }

    private fun instant(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.now())

    private inline fun <reified T : Enum<T>> enumValue(
        raw: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? = enumValues<T>().firstOrNull { it.name == raw }

    companion object {
        private val SHORTCUTS = stringPreferencesKey("shortcut_definitions_v2")
        private val LEGACY_SHORTCUTS = stringPreferencesKey("route_shortcuts_v1")
        private const val CURRENT_STORAGE_VERSION = 5
        private const val LEGACY_STORAGE_VERSION = 1
        private const val TYPE_ROUTE = "route"
        private const val TYPE_APPLICATION = "application"
        private const val MAX_SHORTCUTS = 50
        private const val MAX_STORAGE_LENGTH = 100_000
        private const val MAX_ID_LENGTH = 80
        private const val MAX_ICON_KEY_LENGTH = 64
        private const val MAX_PACKAGE_LENGTH = 255
        private const val MAX_TEXT_LENGTH = 500
        private const val MAX_CONDITIONS = 20
        private const val MAX_CONDITION_DEPTH = 4
    }
}
