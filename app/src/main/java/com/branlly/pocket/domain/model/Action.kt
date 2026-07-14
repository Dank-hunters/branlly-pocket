package com.branlly.pocket.domain.model

sealed interface InputValue<out T> {
    data class Fixed<T>(val value: T) : InputValue<T>
    data object AskAtRuntime : InputValue<Nothing>
    data class FromTrigger(val key: TriggerValueKey) : InputValue<Nothing>
}

enum class TriggerValueKey { DEVICE_NAME, WIFI_SSID, BATTERY_LEVEL, NFC_TAG }

sealed interface ShortcutAction {
    val kind: ActionKind

    data class OpenApplication(val packageName: InputValue<String>) : ShortcutAction {
        override val kind = ActionKind.OPEN_APPLICATION
    }
    data class OpenWebsite(val url: InputValue<String>) : ShortcutAction {
        override val kind = ActionKind.OPEN_WEBSITE
    }
    data class OpenRoute(
        val navigationPackage: InputValue<String>,
        val destination: InputValue<String>,
        val transportMode: TransportMode = TransportMode.DRIVING,
    ) : ShortcutAction {
        override val kind = ActionKind.OPEN_ROUTE
    }
    data class OpenSettings(val panel: SettingsPanel) : ShortcutAction {
        override val kind = ActionKind.OPEN_SETTINGS
    }
    data class SetVolume(
        val stream: VolumeStream,
        val percent: InputValue<Int>,
        val restoreAfterExecution: Boolean = false,
    ) : ShortcutAction {
        override val kind = ActionKind.SET_VOLUME
    }
    data class SetBrightness(val percent: InputValue<Int>) : ShortcutAction {
        override val kind = ActionKind.SET_BRIGHTNESS
    }
    data class SetSoundMode(val mode: SoundMode) : ShortcutAction {
        override val kind = ActionKind.SET_SOUND_MODE
    }
    data class PrepareSms(
        val contact: InputValue<String>,
        val message: InputValue<String>,
        val confirmationRequired: Boolean = true,
    ) : ShortcutAction {
        override val kind = ActionKind.PREPARE_SMS
    }
    data class CallContact(val contact: InputValue<String>) : ShortcutAction {
        override val kind = ActionKind.CALL_CONTACT
    }
    data class CopyText(val text: InputValue<String>) : ShortcutAction {
        override val kind = ActionKind.COPY_TEXT
    }
    data class Wait(val durationMillis: Long, val cancellable: Boolean = true) : ShortcutAction {
        init { require(durationMillis in 100L..86_400_000L) }
        override val kind = ActionKind.WAIT
    }
    data class Checklist(val items: List<String>) : ShortcutAction {
        init { require(items.size in 1..50) }
        override val kind = ActionKind.CHECKLIST
    }
    data class Notification(val title: String, val message: String) : ShortcutAction {
        override val kind = ActionKind.NOTIFICATION
    }
    data class Confirmation(val message: String) : ShortcutAction {
        override val kind = ActionKind.CONFIRMATION
    }
    data class RunShortcut(val shortcutId: ShortcutId) : ShortcutAction {
        override val kind = ActionKind.RUN_SHORTCUT
    }
    data object StopExecution : ShortcutAction {
        override val kind = ActionKind.STOP_EXECUTION
    }
}

enum class ActionKind {
    OPEN_APPLICATION, OPEN_WEBSITE, OPEN_ROUTE, OPEN_SETTINGS,
    SET_VOLUME, SET_BRIGHTNESS, SET_SOUND_MODE,
    PREPARE_SMS, CALL_CONTACT, COPY_TEXT,
    WAIT, CHECKLIST, NOTIFICATION, CONFIRMATION,
    RUN_SHORTCUT, STOP_EXECUTION,
}

enum class ActionCategory { OPEN, DEVICE, COMMUNICATE, ORGANIZE, CONTROL }
enum class TransportMode { DRIVING, WALKING, BICYCLING, TRANSIT }
enum class VolumeStream { MEDIA, RING, ALARM }
enum class SettingsPanel { BLUETOOTH, WIFI, BATTERY, DISPLAY, SOUND }
enum class SoundMode { NORMAL, VIBRATE, SILENT, DO_NOT_DISTURB }
