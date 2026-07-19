package com.branlly.pocket.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.branlly.pocket.domain.execution.ActionEditorKey
import com.branlly.pocket.domain.model.ShortcutAction

fun interface ActionEditorProvider {
    @Composable
    fun Render(
        action: ShortcutAction,
        onChange: (ShortcutAction) -> Unit,
    )
}

object ActionEditorRegistry {
    private val providers: Map<ActionEditorKey, ActionEditorProvider> =
        mapOf(
            ActionEditorKey.APPLICATION to ActionEditorProvider { action, onChange ->
                ApplicationForm(action as ShortcutAction.OpenApplication, onChange)
            },
            ActionEditorKey.MEDIA_WAIT to ActionEditorProvider { action, onChange ->
                MediaWaitForm(action as ShortcutAction.WaitForMediaPlayback, onChange)
            },
            ActionEditorKey.ROUTE to ActionEditorProvider { action, onChange -> RouteForm(action as ShortcutAction.OpenRoute, onChange) },
            ActionEditorKey.SETTINGS to ActionEditorProvider { action, onChange ->
                SettingsForm(action as ShortcutAction.OpenSettings, onChange)
            },
            ActionEditorKey.VOLUME to ActionEditorProvider { action, onChange -> VolumeForm(action as ShortcutAction.SetVolume, onChange) },
            ActionEditorKey.BRIGHTNESS to ActionEditorProvider { action, onChange ->
                BrightnessForm(action as ShortcutAction.SetBrightness, onChange)
            },
            ActionEditorKey.SOUND_MODE to ActionEditorProvider { action, onChange ->
                SoundModeForm(action as ShortcutAction.SetSoundMode, onChange)
            },
            ActionEditorKey.WAIT to ActionEditorProvider { action, onChange -> WaitForm(action as ShortcutAction.Wait, onChange) },
        )

    @Composable
    fun Render(
        key: ActionEditorKey,
        action: ShortcutAction,
        onChange: (ShortcutAction) -> Unit,
    ) {
        val provider = providers[key]
        if (provider == null) Text("Aucun configurateur n’est enregistré pour cette action.") else provider.Render(action, onChange)
    }

    fun hasProvider(key: ActionEditorKey): Boolean = key in providers
}
