package com.branlly.pocket.platform.android.actions

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionHandler
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ActionValidationError
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.platform.android.MediaPlaybackWaiter
import com.branlly.pocket.platform.android.MediaWaitResult
import kotlinx.coroutines.delay

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

class OpenApplicationHandler(
    private val context: Context,
    private val launcher: ExternalActivityGateway,
    private val adapters: List<MediaAppAdapter>,
    private val fallback: MediaAppAdapter,
) : ActionHandler<ShortcutAction.OpenApplication> {
    override val kind = ActionKind.OPEN_APPLICATION

    override fun validate(
        action: ShortcutAction.OpenApplication,
        context: ActionValidationContext,
    ): List<ActionValidationError> {
        val packageName = (action.packageName as? InputValue.Fixed<String>)?.value
            ?: return listOf(ActionValidationError("runtime_value_unresolved", "Choisissez une application."))
        return buildList {
            if (!PACKAGE_NAME.matches(packageName)) add(ActionValidationError("invalid_package", "Le package Android est invalide."))
            else if (!context.isPackageInstalled(packageName)) add(ActionValidationError("missing_package", "L’application n’est pas installée."))
            else if (action.mediaUri == null && action.searchQuery == null && !context.isPackageLaunchable(packageName)) {
                add(ActionValidationError("package_not_launchable", "L’application ne possède aucune activité lançable visible."))
            }
            val uri = (action.mediaUri as? InputValue.Fixed<String>)?.value
            if (uri != null && !isSafeHttps(uri)) add(ActionValidationError("invalid_media_uri", "Le lien média doit être une URL HTTPS valide."))
        }
    }

    override suspend fun execute(
        action: ShortcutAction.OpenApplication,
        context: ActionExecutionContext,
    ): ActionResult {
        val packageName = (action.packageName as InputValue.Fixed<String>).value
        val request = MediaOpenRequest(
            target = AppTarget(packageName, action.activityName),
            searchQuery = (action.searchQuery as? InputValue.Fixed<String>)?.value,
            mediaUri = (action.mediaUri as? InputValue.Fixed<String>)?.value,
        )
        val intent = adapters.firstNotNullOfOrNull { adapter ->
            adapter.takeIf { it.supports(request.target) }?.buildOpenIntent(request)
        } ?: fallback.buildOpenIntent(request)
            ?: this.context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult.Failed("L’application ne possède aucune activité lançable.")
        return launcher.launch(intent, action.applicationLabel ?: packageName, context)
    }

    private fun isSafeHttps(raw: String): Boolean = runCatching {
        val uri = Uri.parse(raw)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

class WaitForMediaPlaybackHandler(
    private val waiter: MediaPlaybackWaiter,
) : ActionHandler<ShortcutAction.WaitForMediaPlayback> {
    override val kind = ActionKind.WAIT_FOR_MEDIA_PLAYBACK

    override fun validate(
        action: ShortcutAction.WaitForMediaPlayback,
        context: ActionValidationContext,
    ): List<ActionValidationError> {
        val packageName = (action.packageName as? InputValue.Fixed<String>)?.value
            ?: return listOf(ActionValidationError("runtime_value_unresolved", "Choisissez l’application multimédia à surveiller."))
        return buildList {
            if (!PACKAGE_NAME.matches(packageName)) add(ActionValidationError("invalid_package", "Le package multimédia est invalide."))
            else if (!context.isPackageInstalled(packageName)) add(ActionValidationError("missing_package", "L’application multimédia n’est pas installée."))
            if (action.expectedTitle != null || action.expectedArtist != null || !action.acceptAnyPlayingMedia) {
                add(ActionValidationError("unsupported_media_metadata", "Le filtrage par titre ou artiste n’est pas encore pris en charge."))
            }
        }
    }

    override suspend fun execute(
        action: ShortcutAction.WaitForMediaPlayback,
        context: ActionExecutionContext,
    ): ActionResult {
        val packageName = (action.packageName as InputValue.Fixed<String>).value
        return when (val result = waiter.waitForPlayback(packageName, action.timeoutMillis)) {
            MediaWaitResult.Playing -> ActionResult.Completed
            MediaWaitResult.TimedOut -> ActionResult.TimedOut("La lecture multimédia a expiré.")
            is MediaWaitResult.Failed -> ActionResult.Failed(result.reason, recoverable = true)
        }
    }
}

class OpenRouteHandler(
    private val launcher: ExternalActivityGateway,
    private val adapters: List<NavigationProviderAdapter>,
) : ActionHandler<ShortcutAction.OpenRoute> {
    override val kind = ActionKind.OPEN_ROUTE

    override fun validate(
        action: ShortcutAction.OpenRoute,
        context: ActionValidationContext,
    ): List<ActionValidationError> {
        val packageName = (action.navigationPackage as? InputValue.Fixed<String>)?.value
            ?: return listOf(ActionValidationError("runtime_value_unresolved", "Choisissez une application de navigation."))
        val destination = (action.destination as? InputValue.Fixed<String>)?.value
            ?: return listOf(ActionValidationError("runtime_value_unresolved", "Indiquez une destination."))
        return buildList {
            if (!PACKAGE_NAME.matches(packageName)) add(ActionValidationError("invalid_package", "Le package de navigation est invalide."))
            else if (!context.isPackageInstalled(packageName)) add(ActionValidationError("missing_package", "L’application de navigation n’est pas installée."))
            if (destination.isBlank()) add(ActionValidationError("missing_destination", "Indiquez une destination."))
            if (adapters.none { it.supports(NavigationTarget(packageName)) }) {
                add(ActionValidationError("unsupported_navigation_provider", "Aucun adaptateur de navigation ne prend en charge cette cible."))
            }
        }
    }

    override suspend fun execute(
        action: ShortcutAction.OpenRoute,
        context: ActionExecutionContext,
    ): ActionResult {
        val packageName = (action.navigationPackage as InputValue.Fixed<String>).value
        val destination = (action.destination as InputValue.Fixed<String>).value.trim()
        val target = NavigationTarget(packageName)
        val adapter = adapters.firstOrNull { it.supports(target) }
            ?: return ActionResult.Failed("Aucun adaptateur de navigation n’est disponible.")
        val intent = adapter.buildRouteIntent(RouteRequest(target, destination, action.transportMode))
            ?: return ActionResult.Failed("Impossible de construire l’itinéraire.")
        return launcher.launch(intent, action.applicationName(), context)
    }

    private fun ShortcutAction.OpenRoute.applicationName(): String =
        (navigationPackage as? InputValue.Fixed<String>)?.value ?: "l’application de navigation"
}

class OpenSettingsHandler(
    private val context: Context,
    private val launcher: ExternalActivityGateway,
) : ActionHandler<ShortcutAction.OpenSettings> {
    override val kind = ActionKind.OPEN_SETTINGS

    override fun validate(
        action: ShortcutAction.OpenSettings,
        context: ActionValidationContext,
    ): List<ActionValidationError> = emptyList()

    override suspend fun execute(
        action: ShortcutAction.OpenSettings,
        context: ActionExecutionContext,
    ): ActionResult {
        val settingsAction = when (action.panel) {
            SettingsPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsPanel.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsPanel.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingsPanel.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsPanel.SOUND -> Settings.ACTION_SOUND_SETTINGS
        }
        val launchResult = launcher.launch(Intent(settingsAction), "les réglages ${action.panel.name.lowercase()}", context)
        if (launchResult !is ActionResult.Completed || action.panel != SettingsPanel.BLUETOOTH) return launchResult
        val manager = this.context.getSystemService(BluetoothManager::class.java)
        repeat(240) {
            if (runCatching { manager.adapter?.isEnabled == true }.getOrDefault(false)) return ActionResult.Completed
            delay(500)
        }
        return ActionResult.TimedOut("Bluetooth n’a pas été activé dans le délai prévu.")
    }
}

class SetVolumeHandler(
    private val context: Context,
) : ActionHandler<ShortcutAction.SetVolume> {
    override val kind = ActionKind.SET_VOLUME

    override fun validate(action: ShortcutAction.SetVolume, context: ActionValidationContext): List<ActionValidationError> = buildList {
        val percent = (action.percent as? InputValue.Fixed<Int>)?.value
        if (percent == null) add(ActionValidationError("runtime_value_unresolved", "Choisissez un volume."))
        else if (percent !in 0..100) add(ActionValidationError("invalid_percent", "Le volume doit être compris entre 0 et 100 %."))
        if (action.restoreAfterExecution) add(ActionValidationError("unsupported_restore", "La restauration automatique du volume n’est pas prise en charge."))
    }

    override suspend fun execute(action: ShortcutAction.SetVolume, context: ActionExecutionContext): ActionResult = runCatching {
        val percent = (action.percent as InputValue.Fixed<Int>).value
        val stream = when (action.stream) {
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        }
        val manager = this.context.getSystemService(AudioManager::class.java)
        manager.setStreamVolume(stream, manager.getStreamMaxVolume(stream) * percent / 100, 0)
        ActionResult.Completed
    }.getOrElse { ActionResult.Failed("Android a refusé le réglage du volume.") }
}

class SetBrightnessHandler(
    private val context: Context,
) : ActionHandler<ShortcutAction.SetBrightness> {
    override val kind = ActionKind.SET_BRIGHTNESS

    override fun validate(action: ShortcutAction.SetBrightness, context: ActionValidationContext): List<ActionValidationError> {
        val percent = (action.percent as? InputValue.Fixed<Int>)?.value
        return when {
            percent == null -> listOf(ActionValidationError("runtime_value_unresolved", "Choisissez une luminosité."))
            percent !in 0..100 -> listOf(ActionValidationError("invalid_percent", "La luminosité doit être comprise entre 0 et 100 %."))
            else -> emptyList()
        }
    }

    override suspend fun execute(action: ShortcutAction.SetBrightness, context: ActionExecutionContext): ActionResult {
        if (!Settings.System.canWrite(this.context)) {
            return ActionResult.PermissionRequired(
                "Autorisez la modification des réglages système.",
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
            )
        }
        val percent = (action.percent as InputValue.Fixed<Int>).value
        return runCatching {
            Settings.System.putInt(this.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(this.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 1 + percent * 254 / 100)
            ActionResult.Completed
        }.getOrElse { ActionResult.Failed("Android a refusé le réglage de luminosité.") }
    }
}

class SetSoundModeHandler(
    private val context: Context,
) : ActionHandler<ShortcutAction.SetSoundMode> {
    override val kind = ActionKind.SET_SOUND_MODE

    override fun validate(action: ShortcutAction.SetSoundMode, context: ActionValidationContext): List<ActionValidationError> = emptyList()

    override suspend fun execute(action: ShortcutAction.SetSoundMode, context: ActionExecutionContext): ActionResult = runCatching {
        val audioManager = this.context.getSystemService(AudioManager::class.java)
        if (action.mode == SoundMode.DO_NOT_DISTURB) {
            val manager = this.context.getSystemService(NotificationManager::class.java)
            if (!manager.isNotificationPolicyAccessGranted) {
                return ActionResult.PermissionRequired("Autorisez l’accès au mode Ne pas déranger.", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            audioManager.ringerMode = when (action.mode) {
                SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                SoundMode.DO_NOT_DISTURB -> error("Handled above")
            }
        }
        ActionResult.Completed
    }.getOrElse { ActionResult.Failed("Android a refusé le changement du mode sonore.") }
}

class WaitHandler : ActionHandler<ShortcutAction.Wait> {
    override val kind = ActionKind.WAIT

    override fun validate(action: ShortcutAction.Wait, context: ActionValidationContext): List<ActionValidationError> =
        if (action.cancellable) emptyList()
        else listOf(ActionValidationError("unsupported_non_cancellable_wait", "Les attentes non annulables ne sont pas prises en charge."))

    override suspend fun execute(action: ShortcutAction.Wait, context: ActionExecutionContext): ActionResult {
        delay(action.durationMillis)
        return ActionResult.Completed
    }
}
