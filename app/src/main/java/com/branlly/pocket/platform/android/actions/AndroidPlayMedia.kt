package com.branlly.pocket.platform.android.actions

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.model.PreferredMediaContentType
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.CapabilityResolver
import com.branlly.pocket.platform.android.BranllyMediaListener

class AndroidMediaCapabilityResolver(
    context: Context,
    private val adapters: List<MediaProviderAdapter>,
) : CapabilityResolver<ShortcutAction.PlayMedia, MediaCapabilitySnapshot> {
    private val appContext = context.applicationContext

    override fun resolve(action: ShortcutAction.PlayMedia): MediaCapabilitySnapshot {
        val packageManager = appContext.packageManager
        val installed =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(action.targetPackage, PackageManager.ApplicationInfoFlags.of(0)).enabled
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(action.targetPackage, 0).enabled
                }
            }.getOrDefault(false)
        val launchable = packageManager.getLaunchIntentForPackage(action.targetPackage) != null
        val exactActivity =
            action.activityName?.let { name ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getActivityInfo(
                            ComponentName(action.targetPackage, name),
                            PackageManager.ComponentInfoFlags.of(0),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getActivityInfo(ComponentName(action.targetPackage, name), 0)
                    }
                }.isSuccess
            } ?: false
        val adapter = adapters.firstOrNull { it.supports(AppTarget(action.targetPackage, action.activityName)) }
        val listenerAuthorized = appContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(appContext)
        val listenerAvailable = listenerAuthorized && BranllyMediaListener.isConnected()
        val sessions = if (listenerAvailable) activeSessions(action.targetPackage) else emptyList()
        return MediaCapabilitySnapshot(
            packageInstalled = installed,
            packageLaunchable = launchable,
            exactActivityAvailable = exactActivity,
            directUriProvided = !action.mediaUri.isNullOrBlank(),
            providerAdapterId = adapter?.id,
            providerCapabilities = adapter?.capabilities.orEmpty(),
            notificationListenerAuthorized = listenerAuthorized,
            notificationListenerAvailable = listenerAvailable,
            exactPackageSessionCount = sessions.size,
            transportActions = sessions.fold(0L) { value, controller -> value or (controller.playbackState?.actions ?: 0L) },
            manualFallbackAllowed = action.allowManualFallback,
            advancedAutomationAllowed = action.allowAdvancedAutomation,
            advancedAutomationAvailable = false,
        )
    }

    private fun activeSessions(packageName: String): List<MediaController> =
        runCatching {
            appContext
                .getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
                .filter { it.packageName == packageName }
        }.getOrDefault(emptyList())
}

sealed interface MediaSessionCommandResult {
    data class Sent(
        val command: String,
    ) : MediaSessionCommandResult

    data class NotSupported(
        val reason: String,
    ) : MediaSessionCommandResult

    data class Failed(
        val reason: String,
    ) : MediaSessionCommandResult
}

fun interface MediaSessionCommandGateway {
    fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult
}

class AndroidMediaSessionCommandGateway(
    context: Context,
) : MediaSessionCommandGateway {
    private val appContext = context.applicationContext

    override fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult {
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext) ||
            !BranllyMediaListener.isConnected()
        ) {
            return MediaSessionCommandResult.Failed("Le service MediaSession n’est pas disponible.")
        }
        val controllers =
            runCatching {
                appContext
                    .getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
                    .filter { it.packageName == action.targetPackage }
            }.getOrElse { return MediaSessionCommandResult.Failed(it.message ?: "Accès MediaSession refusé.") }
        val controller =
            controllers.maxByOrNull { it.playbackState?.actions ?: 0L }
                ?: return MediaSessionCommandResult.NotSupported("Aucune session du package cible.")
        val actions = controller.playbackState?.actions ?: 0L
        val controls = controller.transportControls
        return runCatching {
            val uri = action.mediaUri?.let(Uri::parse)
            when {
                uri != null && actions supports PlaybackState.ACTION_PLAY_FROM_URI -> {
                    controls.playFromUri(uri, Bundle.EMPTY)
                    MediaSessionCommandResult.Sent("playFromUri")
                }

                action.searchQuery.isNotBlank() && actions supports PlaybackState.ACTION_PLAY_FROM_SEARCH -> {
                    controls.playFromSearch(action.effectiveSearchQuery(), Bundle.EMPTY)
                    MediaSessionCommandResult.Sent("playFromSearch")
                }

                action.searchQuery.isNotBlank() && actions supports PlaybackState.ACTION_PREPARE_FROM_SEARCH -> {
                    controls.prepareFromSearch(action.effectiveSearchQuery(), Bundle.EMPTY)
                    if (actions supports PlaybackState.ACTION_PLAY) controls.play()
                    MediaSessionCommandResult.Sent("prepareFromSearch${if (actions supports PlaybackState.ACTION_PLAY) "+play" else ""}")
                }

                actions supports PlaybackState.ACTION_PLAY -> {
                    controls.play()
                    MediaSessionCommandResult.Sent("play")
                }

                else -> {
                    MediaSessionCommandResult.NotSupported("La session n’annonce aucune commande compatible.")
                }
            }
        }.getOrElse { MediaSessionCommandResult.Failed(it.message ?: "Commande MediaSession refusée.") }
    }

    private infix fun Long.supports(action: Long): Boolean = this and action != 0L
}

fun interface ManualMediaGuidance {
    fun show(
        action: ShortcutAction.PlayMedia,
        executionContext: ActionExecutionContext,
    )

    fun clear() = Unit
}

private fun ShortcutAction.PlayMedia.effectiveSearchQuery(): String =
    buildList {
        add(searchQuery.trim())
        artist?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        when (preferredContentType) {
            PreferredMediaContentType.AUTO -> Unit
            PreferredMediaContentType.SONG -> add("song")
            PreferredMediaContentType.VIDEO -> add("video")
            PreferredMediaContentType.PLAYLIST -> add("playlist")
            PreferredMediaContentType.PODCAST -> add("podcast")
        }
    }.filter(String::isNotBlank).joinToString(" ")
