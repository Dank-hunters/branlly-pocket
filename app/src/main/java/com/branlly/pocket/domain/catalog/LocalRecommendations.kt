package com.branlly.pocket.domain.catalog

import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.Trigger
import java.time.LocalTime

/** Matrice déterministe embarquée. Trois suggestions au maximum. */
object LocalRecommendations {
    fun forTrigger(trigger: Trigger): List<ActionKind> = when (trigger) {
        is Trigger.Bluetooth -> listOf(ActionKind.OPEN_APPLICATION, ActionKind.OPEN_ROUTE, ActionKind.SET_VOLUME)
        is Trigger.BatteryLevel -> listOf(ActionKind.SET_BRIGHTNESS, ActionKind.OPEN_SETTINGS, ActionKind.NOTIFICATION)
        is Trigger.Nfc -> listOf(ActionKind.OPEN_APPLICATION, ActionKind.RUN_SHORTCUT, ActionKind.CHECKLIST)
        is Trigger.Time -> if (trigger.time >= LocalTime.of(18, 0)) {
            listOf(ActionKind.SET_BRIGHTNESS, ActionKind.SET_SOUND_MODE, ActionKind.NOTIFICATION)
        } else {
            listOf(ActionKind.OPEN_APPLICATION, ActionKind.SET_VOLUME, ActionKind.CHECKLIST)
        }
        else -> listOf(ActionKind.OPEN_APPLICATION, ActionKind.WAIT, ActionKind.CONFIRMATION)
    }

    fun after(action: ShortcutAction): List<ActionKind> = when (action.kind) {
        ActionKind.OPEN_APPLICATION -> listOf(ActionKind.SET_VOLUME, ActionKind.WAIT, ActionKind.OPEN_ROUTE)
        ActionKind.PREPARE_SMS -> listOf(ActionKind.CONFIRMATION, ActionKind.COPY_TEXT)
        ActionKind.SET_VOLUME -> listOf(ActionKind.OPEN_APPLICATION, ActionKind.WAIT)
        ActionKind.OPEN_ROUTE -> listOf(ActionKind.SET_VOLUME, ActionKind.SET_SOUND_MODE)
        else -> listOf(ActionKind.WAIT, ActionKind.CONFIRMATION, ActionKind.NOTIFICATION)
    }.take(3)
}
