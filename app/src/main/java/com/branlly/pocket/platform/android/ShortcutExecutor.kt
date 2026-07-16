package com.branlly.pocket.platform.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ExecutionTiming
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.VolumeStream
import kotlinx.coroutines.delay

/** Exécute dans l'ordre les actions Android actuellement réellement prises en charge. */
class ShortcutExecutor(
    private val context: Context,
) {
    suspend fun execute(shortcut: ShortcutDefinition): ShortcutExecutionResult {
        val actions = shortcut.nodes.filter(ActionNode::enabled).map(ActionNode::action)
        actions.forEachIndexed { index, action ->
            val result = execute(action)
            if (result !is ShortcutExecutionResult.Completed) return result
            val delayMillis = ExecutionTiming.automaticDelayAfter(action, actions.getOrNull(index + 1))
            if (delayMillis > 0) delay(delayMillis)
        }
        val finalNode =
            shortcut.finalForegroundNodeId?.let { id ->
                shortcut.nodes.firstOrNull { it.id == id && it.enabled }
            }
        val finalAction = finalNode?.action
        val lastEnabledNode = shortcut.nodes.lastOrNull(ActionNode::enabled)
        if (finalNode != null && finalNode.id != lastEnabledNode?.id &&
            (finalAction is ShortcutAction.OpenApplication || finalAction is ShortcutAction.OpenRoute)
        ) {
            val delayMillis = lastEnabledNode?.action?.let { ExecutionTiming.automaticDelayAfter(it, finalAction) } ?: 0L
            if (delayMillis > 0) delay(delayMillis)
            return execute(finalAction)
        }
        return ShortcutExecutionResult.Completed
    }

    private suspend fun execute(action: ShortcutAction): ShortcutExecutionResult =
        when (action) {
            is ShortcutAction.OpenApplication -> {
                val packageName = (action.packageName as? InputValue.Fixed<String>)?.value
                val searchQuery = (action.searchQuery as? InputValue.Fixed<String>)?.value
                val mediaUri = (action.mediaUri as? InputValue.Fixed<String>)?.value
                ApplicationLauncher(context).launch(packageName, searchQuery, mediaUri).toExecutionResult()
            }

            is ShortcutAction.OpenRoute -> {
                RouteLauncher(context).launch(action).toExecutionResult()
            }

            is ShortcutAction.OpenSettings -> {
                openSettings(action.panel)
            }

            is ShortcutAction.SetVolume -> {
                setVolume(action)
            }

            is ShortcutAction.SetBrightness -> {
                setBrightness(action)
            }

            is ShortcutAction.SetSoundMode -> {
                setSoundMode(action.mode)
            }

            is ShortcutAction.Wait -> {
                delay(action.durationMillis)
                ShortcutExecutionResult.Completed
            }

            else -> {
                ShortcutExecutionResult.UnsupportedAction(action.kind.name)
            }
        }

    private fun openSettings(panel: SettingsPanel): ShortcutExecutionResult {
        val action =
            when (panel) {
                SettingsPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
                SettingsPanel.WIFI -> Settings.ACTION_WIFI_SETTINGS
                SettingsPanel.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                SettingsPanel.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
                SettingsPanel.SOUND -> Settings.ACTION_SOUND_SETTINGS
            }
        return runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShortcutExecutionResult.Completed
        }.getOrElse { ShortcutExecutionResult.Failed("Android ne peut pas ouvrir ce réglage.") }
    }

    private fun setVolume(action: ShortcutAction.SetVolume): ShortcutExecutionResult {
        val percent =
            (action.percent as? InputValue.Fixed<Int>)?.value
                ?: return ShortcutExecutionResult.Failed("Choisissez un volume avant de lancer ce raccourci.")
        val stream =
            when (action.stream) {
                com.branlly.pocket.domain.model.VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
                com.branlly.pocket.domain.model.VolumeStream.RING -> AudioManager.STREAM_RING
                com.branlly.pocket.domain.model.VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            }
        val manager = context.getSystemService(AudioManager::class.java)
        val index = (manager.getStreamMaxVolume(stream) * percent.coerceIn(0, 100)) / 100
        manager.setStreamVolume(stream, index, 0)
        return ShortcutExecutionResult.Completed
    }

    private fun setBrightness(action: ShortcutAction.SetBrightness): ShortcutExecutionResult {
        val percent =
            (action.percent as? InputValue.Fixed<Int>)?.value
                ?: return ShortcutExecutionResult.Failed("Choisissez une luminosité avant de lancer ce raccourci.")
        if (!Settings.System.canWrite(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return ShortcutExecutionResult.Failed("Autorisez la modification des réglages système, puis relancez le raccourci.")
        }
        val brightness = 1 + ((percent.coerceIn(0, 100) * 254) / 100)
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            ShortcutExecutionResult.Completed
        }.getOrElse { ShortcutExecutionResult.Failed("Android a refusé le réglage de luminosité.") }
    }

    private fun setSoundMode(mode: SoundMode): ShortcutExecutionResult {
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (mode == SoundMode.DO_NOT_DISTURB) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ShortcutExecutionResult.Failed("Autorisez l’accès au mode Ne pas déranger, puis relancez le raccourci.")
            }
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            audioManager.ringerMode =
                when (mode) {
                    SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                    SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                    SoundMode.DO_NOT_DISTURB -> error("Handled above")
                }
        }
        return ShortcutExecutionResult.Completed
    }
}

sealed interface ShortcutExecutionResult {
    data object Completed : ShortcutExecutionResult

    data class Failed(
        val message: String,
    ) : ShortcutExecutionResult

    data class UnsupportedAction(
        val kind: String,
    ) : ShortcutExecutionResult
}

private fun ApplicationLaunchResult.toExecutionResult(): ShortcutExecutionResult =
    when (this) {
        ApplicationLaunchResult.Launched -> {
            ShortcutExecutionResult.Completed
        }

        ApplicationLaunchResult.RuntimeValueRequired -> {
            ShortcutExecutionResult.Failed(
                "Choisissez une application avant de lancer ce raccourci.",
            )
        }

        ApplicationLaunchResult.InvalidPackage -> {
            ShortcutExecutionResult.Failed("L’application choisie n’est pas valide.")
        }

        ApplicationLaunchResult.MissingApplication -> {
            ShortcutExecutionResult.Failed("L’application choisie n’est plus installée.")
        }

        ApplicationLaunchResult.RejectedBySystem -> {
            ShortcutExecutionResult.Failed("Android a refusé l’ouverture de l’application.")
        }
    }

private fun RouteLaunchResult.toExecutionResult(): ShortcutExecutionResult =
    when (this) {
        RouteLaunchResult.Launched -> {
            ShortcutExecutionResult.Completed
        }

        RouteLaunchResult.MissingApplication -> {
            ShortcutExecutionResult.Failed("L’application de navigation choisie n’est pas installée.")
        }

        RouteLaunchResult.MissingDestination -> {
            ShortcutExecutionResult.Failed("Indiquez une destination.")
        }

        RouteLaunchResult.RuntimeValueRequired -> {
            ShortcutExecutionResult.Failed("Cette action demande une valeur au lancement.")
        }

        RouteLaunchResult.UnsupportedApplication -> {
            ShortcutExecutionResult.Failed(
                "Cette application de navigation n’est pas prise en charge.",
            )
        }

        RouteLaunchResult.RejectedBySystem -> {
            ShortcutExecutionResult.Failed("Android a refusé l’ouverture de l’itinéraire.")
        }
    }
