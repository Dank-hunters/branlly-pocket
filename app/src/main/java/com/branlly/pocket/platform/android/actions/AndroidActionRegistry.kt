package com.branlly.pocket.platform.android.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.branlly.pocket.domain.execution.ActionEditorKey
import com.branlly.pocket.domain.execution.ActionRegistry
import com.branlly.pocket.domain.execution.ActionValidationContext
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.execution.RegisteredAction
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.platform.android.AndroidMediaPlaybackWaiter

class AndroidActionValidationContext(
    private val context: Context,
) : ActionValidationContext {
    override fun isPackageInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)).enabled
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0).enabled
        }
    }.getOrDefault(false)

    override fun isPackageLaunchable(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null ||
            queryLauncher(packageName)

    private fun queryLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())).isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
        }
    }
}

class AndroidExecutionLogger(
    private val appPackage: String,
) : ExecutionLogger {
    override fun log(event: String, fields: Map<String, Any?>) {
        val values = fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        Log.i(TAG, "APP_PACKAGE=$appPackage event=$event $values")
    }

    private companion object {
        const val TAG = "BranllyExecution"
    }
}

object AndroidActionRegistry {
    fun create(context: Context): ActionRegistry {
        val appContext = context.applicationContext
        val externalLauncher = AndroidExternalActivityLauncher(appContext)
        return ActionRegistry(
            listOf(
                RegisteredAction(
                    kind = ActionKind.OPEN_APPLICATION,
                    actionClass = ShortcutAction.OpenApplication::class.java,
                    title = "Ouvrir une application",
                    description = "Application installée",
                    category = ActionCategory.OPEN,
                    editorKey = ActionEditorKey.APPLICATION,
                    createDefault = { ShortcutAction.OpenApplication(InputValue.AskAtRuntime) },
                    summary = { action ->
                        val packageName = (action.packageName as? InputValue.Fixed<String>)?.value ?: "cible non définie"
                        "Ouvrir ${action.applicationLabel ?: packageName} · $packageName"
                    },
                    handler = OpenApplicationHandler(
                        context = appContext,
                        launcher = externalLauncher,
                        adapters = BuiltInProviderCatalog.mediaAdapters,
                        fallback = GenericMediaAppAdapter(),
                    ),
                ),
                RegisteredAction(
                    kind = ActionKind.OPEN_ROUTE,
                    actionClass = ShortcutAction.OpenRoute::class.java,
                    title = "Lancer un itinéraire",
                    description = "Destination fixe ou demandée",
                    category = ActionCategory.OPEN,
                    editorKey = ActionEditorKey.ROUTE,
                    createDefault = { ShortcutAction.OpenRoute(InputValue.AskAtRuntime, InputValue.AskAtRuntime) },
                    handler = OpenRouteHandler(
                        externalLauncher,
                        BuiltInProviderCatalog.navigationAdapters,
                    ),
                ),
                RegisteredAction(
                    kind = ActionKind.OPEN_SETTINGS,
                    actionClass = ShortcutAction.OpenSettings::class.java,
                    title = "Ouvrir des réglages",
                    description = "Bluetooth, Wi-Fi, batterie…",
                    category = ActionCategory.OPEN,
                    editorKey = ActionEditorKey.SETTINGS,
                    createDefault = { ShortcutAction.OpenSettings(SettingsPanel.BLUETOOTH) },
                    handler = OpenSettingsHandler(appContext, externalLauncher),
                ),
                RegisteredAction(
                    kind = ActionKind.SET_VOLUME,
                    actionClass = ShortcutAction.SetVolume::class.java,
                    title = "Régler le volume",
                    description = "Multimédia, sonnerie ou alarme",
                    category = ActionCategory.DEVICE,
                    editorKey = ActionEditorKey.VOLUME,
                    createDefault = { ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70)) },
                    handler = SetVolumeHandler(appContext),
                ),
                RegisteredAction(
                    kind = ActionKind.SET_BRIGHTNESS,
                    actionClass = ShortcutAction.SetBrightness::class.java,
                    title = "Régler la luminosité",
                    description = "Niveau de 0 à 100 %",
                    category = ActionCategory.DEVICE,
                    editorKey = ActionEditorKey.BRIGHTNESS,
                    createDefault = { ShortcutAction.SetBrightness(InputValue.Fixed(50)) },
                    handler = SetBrightnessHandler(appContext),
                ),
                RegisteredAction(
                    kind = ActionKind.SET_SOUND_MODE,
                    actionClass = ShortcutAction.SetSoundMode::class.java,
                    title = "Changer le mode sonore",
                    description = "Normal, vibreur ou silencieux",
                    category = ActionCategory.DEVICE,
                    editorKey = ActionEditorKey.SOUND_MODE,
                    createDefault = { ShortcutAction.SetSoundMode(SoundMode.VIBRATE) },
                    handler = SetSoundModeHandler(appContext),
                ),
                RegisteredAction(
                    kind = ActionKind.WAIT_FOR_MEDIA_PLAYBACK,
                    actionClass = ShortcutAction.WaitForMediaPlayback::class.java,
                    title = "Attendre la lecture média",
                    description = "Continue après démarrage confirmé",
                    category = ActionCategory.ORGANIZE,
                    editorKey = ActionEditorKey.MEDIA_WAIT,
                    createDefault = { ShortcutAction.WaitForMediaPlayback(InputValue.AskAtRuntime) },
                    summary = { action ->
                        val packageName = (action.packageName as? InputValue.Fixed<String>)?.value ?: "cible non définie"
                        "Attendre lecture · ${action.applicationLabel ?: packageName} · $packageName · ${action.timeoutMillis / 1_000} s"
                    },
                    handler = WaitForMediaPlaybackHandler(AndroidMediaPlaybackWaiter(appContext)),
                ),
                RegisteredAction(
                    kind = ActionKind.WAIT,
                    actionClass = ShortcutAction.Wait::class.java,
                    title = "Attendre",
                    description = "Délai annulable",
                    category = ActionCategory.ORGANIZE,
                    editorKey = ActionEditorKey.WAIT,
                    createDefault = { ShortcutAction.Wait(2_000) },
                    summary = { action -> "Attendre ${action.durationMillis / 1_000} secondes" },
                    handler = WaitHandler(),
                ),
            ),
        )
    }
}
