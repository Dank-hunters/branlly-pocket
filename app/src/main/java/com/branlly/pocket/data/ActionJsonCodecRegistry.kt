package com.branlly.pocket.data

import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.TransportMode
import com.branlly.pocket.domain.model.TriggerValueKey
import com.branlly.pocket.domain.model.VolumeStream
import org.json.JSONArray
import org.json.JSONObject

internal interface ActionJsonCodec<A : ShortcutAction> {
    val kind: ActionKind
    val actionClass: Class<A>

    fun encode(action: A): JSONObject

    fun decode(value: JSONObject): A?
}

internal class ActionJsonCodecRegistry(
    codecs: List<ActionJsonCodec<out ShortcutAction>>,
) {
    private val byKind = codecs.associateBy(ActionJsonCodec<*>::kind)

    init {
        require(byKind.size == codecs.size)
    }

    fun encode(action: ShortcutAction): JSONObject {
        val codec = byKind[action.kind] ?: error("Missing JSON codec for ${action.kind}")
        require(codec.actionClass.isInstance(action))
        return encodeTyped(codec, action).put("type", action.kind.name)
    }

    fun decode(value: JSONObject): ShortcutAction? {
        val kind = runCatching { ActionKind.valueOf(value.optString("type")) }.getOrNull() ?: return null
        val codec = byKind[kind] ?: return null
        return decodeTyped(codec, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeTyped(
        codec: ActionJsonCodec<out ShortcutAction>,
        action: ShortcutAction,
    ): JSONObject = (codec as ActionJsonCodec<ShortcutAction>).encode(action)

    @Suppress("UNCHECKED_CAST")
    private fun decodeTyped(
        codec: ActionJsonCodec<out ShortcutAction>,
        value: JSONObject,
    ): ShortcutAction? = (codec as ActionJsonCodec<ShortcutAction>).decode(value)

    companion object {
        val DEFAULT = ActionJsonCodecRegistry(defaultCodecs())
    }
}

private fun defaultCodecs(): List<ActionJsonCodec<out ShortcutAction>> =
    listOf(
        codec<ShortcutAction.OpenApplication>(ActionKind.OPEN_APPLICATION, { action ->
            JSONObject()
                .put("package", encodeInput(action.packageName))
                .putOptional("searchQuery", action.searchQuery?.let(::encodeInput))
                .putOptional("mediaUri", action.mediaUri?.let(::encodeInput))
                .putOptional("applicationLabel", action.applicationLabel)
                .putOptional("activityName", action.activityName)
        }) { value ->
            ShortcutAction.OpenApplication(
                packageName = decodeStringInput(value.optJSONObject("package")) ?: return@codec null,
                searchQuery = value.optJSONObject("searchQuery")?.let(::decodeStringInput),
                mediaUri = value.optJSONObject("mediaUri")?.let(::decodeStringInput),
                applicationLabel = value.optionalString("applicationLabel"),
                activityName = value.optionalString("activityName"),
            )
        },
        codec<ShortcutAction.WaitForMediaPlayback>(ActionKind.WAIT_FOR_MEDIA_PLAYBACK, { action ->
            JSONObject()
                .put("package", encodeInput(action.packageName))
                .put("timeout", action.timeoutMillis)
                .putOptional("applicationLabel", action.applicationLabel)
                .putOptional("expectedTitle", action.expectedTitle)
                .putOptional("expectedArtist", action.expectedArtist)
                .put("acceptAnyPlayingMedia", action.acceptAnyPlayingMedia)
        }) { value ->
            ShortcutAction.WaitForMediaPlayback(
                packageName = decodeStringInput(value.optJSONObject("package")) ?: return@codec null,
                timeoutMillis = value.optLong("timeout", 120_000L).coerceIn(1_000L, 300_000L),
                applicationLabel = value.optionalString("applicationLabel"),
                expectedTitle = value.optionalString("expectedTitle"),
                expectedArtist = value.optionalString("expectedArtist"),
                acceptAnyPlayingMedia = value.optBoolean("acceptAnyPlayingMedia", true),
            )
        },
        codec<ShortcutAction.OpenWebsite>(ActionKind.OPEN_WEBSITE, { action ->
            JSONObject().put("url", encodeInput(action.url))
        }) { value -> ShortcutAction.OpenWebsite(decodeStringInput(value.optJSONObject("url")) ?: return@codec null) },
        codec<ShortcutAction.OpenRoute>(ActionKind.OPEN_ROUTE, { action ->
            JSONObject()
                .put("package", encodeInput(action.navigationPackage))
                .put("destination", encodeInput(action.destination))
                .put("transport", action.transportMode.name)
        }) { value ->
            ShortcutAction.OpenRoute(
                navigationPackage = decodeStringInput(value.optJSONObject("package")) ?: return@codec null,
                destination = decodeStringInput(value.optJSONObject("destination")) ?: return@codec null,
                transportMode = enumValue(value.optString("transport"), TransportMode.DRIVING),
            )
        },
        codec<ShortcutAction.OpenSettings>(ActionKind.OPEN_SETTINGS, { action ->
            JSONObject().put("panel", action.panel.name)
        }) { value -> ShortcutAction.OpenSettings(enumValue(value.optString("panel"), SettingsPanel.WIFI)) },
        codec<ShortcutAction.SetVolume>(ActionKind.SET_VOLUME, { action ->
            JSONObject()
                .put("stream", action.stream.name)
                .put("percent", encodeInput(action.percent))
                .put("restore", action.restoreAfterExecution)
        }) { value ->
            ShortcutAction.SetVolume(
                stream = enumValue(value.optString("stream"), VolumeStream.MEDIA),
                percent = decodeIntInput(value.optJSONObject("percent")) ?: return@codec null,
                restoreAfterExecution = value.optBoolean("restore", false),
            )
        },
        codec<ShortcutAction.SetBrightness>(ActionKind.SET_BRIGHTNESS, { action ->
            JSONObject().put("percent", encodeInput(action.percent))
        }) { value -> ShortcutAction.SetBrightness(decodeIntInput(value.optJSONObject("percent")) ?: return@codec null) },
        codec<ShortcutAction.SetSoundMode>(ActionKind.SET_SOUND_MODE, { action ->
            JSONObject().put("mode", action.mode.name)
        }) { value -> ShortcutAction.SetSoundMode(enumValue(value.optString("mode"), SoundMode.NORMAL)) },
        codec<ShortcutAction.PrepareSms>(ActionKind.PREPARE_SMS, { action ->
            JSONObject()
                .put("contact", encodeInput(action.contact))
                .put("message", encodeInput(action.message))
                .put("confirmation", action.confirmationRequired)
        }) { value ->
            ShortcutAction.PrepareSms(
                contact = decodeStringInput(value.optJSONObject("contact")) ?: return@codec null,
                message = decodeStringInput(value.optJSONObject("message")) ?: return@codec null,
                confirmationRequired = value.optBoolean("confirmation", true),
            )
        },
        codec<ShortcutAction.CallContact>(ActionKind.CALL_CONTACT, { action ->
            JSONObject().put("contact", encodeInput(action.contact))
        }) { value -> ShortcutAction.CallContact(decodeStringInput(value.optJSONObject("contact")) ?: return@codec null) },
        codec<ShortcutAction.CopyText>(ActionKind.COPY_TEXT, { action ->
            JSONObject().put("text", encodeInput(action.text))
        }) { value -> ShortcutAction.CopyText(decodeStringInput(value.optJSONObject("text")) ?: return@codec null) },
        codec<ShortcutAction.Wait>(ActionKind.WAIT, { action ->
            JSONObject().put("duration", action.durationMillis).put("cancellable", action.cancellable)
        }) { value -> ShortcutAction.Wait(value.optLong("duration"), value.optBoolean("cancellable", true)) },
        codec<ShortcutAction.Checklist>(ActionKind.CHECKLIST, { action ->
            JSONObject().put("items", JSONArray(action.items))
        }) { value ->
            val array = value.optJSONArray("items") ?: return@codec null
            ShortcutAction.Checklist(List(minOf(array.length(), 50)) { index -> array.optString(index).take(MAX_TEXT_LENGTH) })
        },
        codec<ShortcutAction.Notification>(ActionKind.NOTIFICATION, { action ->
            JSONObject().put("title", action.title).put("message", action.message)
        }) { value ->
            ShortcutAction.Notification(
                value.optionalString("title") ?: return@codec null,
                value.optionalString("message") ?: return@codec null,
            )
        },
        codec<ShortcutAction.Confirmation>(ActionKind.CONFIRMATION, { action ->
            JSONObject().put("message", action.message)
        }) { value -> ShortcutAction.Confirmation(value.optionalString("message") ?: return@codec null) },
        codec<ShortcutAction.RunShortcut>(ActionKind.RUN_SHORTCUT, { action ->
            JSONObject().put("shortcutId", action.shortcutId.value)
        }) { value -> ShortcutAction.RunShortcut(ShortcutId(value.optionalString("shortcutId") ?: return@codec null)) },
        codec<ShortcutAction.StopExecution>(ActionKind.STOP_EXECUTION, { JSONObject() }) { ShortcutAction.StopExecution },
    )

private inline fun <reified A : ShortcutAction> codec(
    kind: ActionKind,
    crossinline encode: (A) -> JSONObject,
    crossinline decode: (JSONObject) -> A?,
): ActionJsonCodec<A> =
    object : ActionJsonCodec<A> {
        override val kind = kind
        override val actionClass: Class<A> = A::class.java
        override fun encode(action: A): JSONObject = encode(action)
        override fun decode(value: JSONObject): A? = runCatching { decode(value) }.getOrNull()
    }

private fun encodeInput(input: InputValue<*>): JSONObject = when (input) {
    is InputValue.Fixed<*> -> JSONObject().put("source", "fixed").put("value", input.value)
    InputValue.AskAtRuntime -> JSONObject().put("source", "runtime")
    is InputValue.FromTrigger -> JSONObject().put("source", "trigger").put("key", input.key.name)
}

private fun decodeStringInput(value: JSONObject?): InputValue<String>? = decodeInput(value) { it as? String }
private fun decodeIntInput(value: JSONObject?): InputValue<Int>? = decodeInput(value) { (it as? Number)?.toInt() }

private fun <T> decodeInput(value: JSONObject?, fixed: (Any?) -> T?): InputValue<T>? {
    val item = value ?: return null
    return when (item.optString("source")) {
        "fixed" -> fixed(item.opt("value"))?.let { fixedValue -> InputValue.Fixed(fixedValue) }
        "runtime" -> InputValue.AskAtRuntime
        "trigger" -> item.optionalString("key")
            ?.let { key -> runCatching { TriggerValueKey.valueOf(key) }.getOrNull() }
            ?.let { key -> InputValue.FromTrigger(key) }
        else -> null
    }
}

private fun JSONObject.putOptional(key: String, value: Any?): JSONObject = apply { if (value != null) put(key, value) }
private fun JSONObject.optionalString(key: String): String? = optString(key).trim().take(MAX_TEXT_LENGTH).ifBlank { null }
private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T = runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
private const val MAX_TEXT_LENGTH = 500
