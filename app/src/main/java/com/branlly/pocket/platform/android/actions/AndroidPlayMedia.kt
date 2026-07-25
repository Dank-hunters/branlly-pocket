package com.branlly.pocket.platform.android.actions

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.media.MediaPlaybackConfirmation
import com.branlly.pocket.domain.media.MediaPlaybackStrategy
import com.branlly.pocket.domain.media.MediaProviderCapability
import com.branlly.pocket.domain.media.MediaStrategyContext
import com.branlly.pocket.domain.media.MediaStrategyResult
import com.branlly.pocket.domain.model.MediaSelectionPolicy
import com.branlly.pocket.domain.model.PreferredMediaContentType
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.workflow.CapabilityResolver
import com.branlly.pocket.platform.android.BranllyMediaListener
import com.branlly.pocket.platform.android.MediaPlaybackWaiter
import com.branlly.pocket.platform.android.MediaWaitResult

class AndroidMediaCapabilityResolver(
    context: Context,
    private val adapters: List<MediaProviderAdapter>,
) : CapabilityResolver<ShortcutAction.PlayMedia, MediaCapabilitySnapshot> {
    private val appContext = context.applicationContext

    override fun resolve(action: ShortcutAction.PlayMedia): MediaCapabilitySnapshot {
        val packageManager = appContext.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(action.targetPackage, PackageManager.ApplicationInfoFlags.of(0)).enabled
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(action.targetPackage, 0).enabled
            }
        }.getOrDefault(false)
        val launchable = packageManager.getLaunchIntentForPackage(action.targetPackage) != null
        val exactActivity = action.activityName?.let { name ->
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

    private fun activeSessions(packageName: String): List<MediaController> = runCatching {
        appContext.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
            .filter { it.packageName == packageName }
    }.getOrDefault(emptyList())
}

sealed interface MediaSessionCommandResult {
    data class Sent(val command: String) : MediaSessionCommandResult
    data class NotSupported(val reason: String) : MediaSessionCommandResult
    data class Failed(val reason: String) : MediaSessionCommandResult
}

fun interface MediaSessionCommandGateway {
    fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult
}

class AndroidMediaSessionCommandGateway(context: Context) : MediaSessionCommandGateway {
    private val appContext = context.applicationContext

    override fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult {
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext) || !BranllyMediaListener.isConnected()) {
            return MediaSessionCommandResult.Failed("Le service MediaSession n’est pas disponible.")
        }
        val controllers = runCatching {
            appContext.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
                .filter { it.packageName == action.targetPackage }
        }.getOrElse { return MediaSessionCommandResult.Failed(it.message ?: "Accès MediaSession refusé.") }
        val controller = controllers.maxByOrNull { it.playbackState?.actions ?: 0L }
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
                else -> MediaSessionCommandResult.NotSupported("La session n’annonce aucune commande compatible.")
            }
        }.getOrElse { MediaSessionCommandResult.Failed(it.message ?: "Commande MediaSession refusée.") }
    }

    private infix fun Long.supports(action: Long): Boolean = this and action != 0L
}

abstract class WaitingMediaStrategy(
    protected val waiter: MediaPlaybackWaiter,
) {
    protected suspend fun await(packageName: String, timeoutMillis: Long): MediaStrategyResult = when (val result = waiter.waitForPlayback(packageName, timeoutMillis)) {
        MediaWaitResult.Playing -> MediaStrategyResult.StartedPlayback(MediaPlaybackConfirmation.PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED)
        MediaWaitResult.TimedOut -> MediaStrategyResult.RecoverableFailure("COMMAND_ACCEPTANCE_UNCONFIRMED")
        MediaWaitResult.SessionAbsent -> MediaStrategyResult.RecoverableFailure("Aucune session cible n’a démarré.")
        MediaWaitResult.PermissionMissing -> MediaStrategyResult.TerminalFailure("Autorisez l’accès aux notifications pour confirmer la lecture.")
        MediaWaitResult.ListenerUnavailable -> MediaStrategyResult.TerminalFailure("Le service NotificationListener est indisponible.")
        is MediaWaitResult.ServiceUnavailable -> MediaStrategyResult.TerminalFailure(result.reason)
        is MediaWaitResult.Failed -> MediaStrategyResult.RecoverableFailure(result.reason)
    }
}

class DirectUriMediaStrategy(
    private val launcher: ExternalActivityGateway,
    private val adapter: MediaProviderAdapter,
    waiter: MediaPlaybackWaiter,
) : WaitingMediaStrategy(waiter), MediaPlaybackStrategy {
    override val id = "direct_uri"
    override val priority = 10
    override val timeoutMillis = 10_000L
    override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) =
        !action.mediaUri.isNullOrBlank() && MediaProviderCapability.CAN_OPEN_DIRECT_CONTENT in capabilities.providerCapabilities

    override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult {
        val request = context.action.request()
        val intent = adapter.buildDirectContentIntent(request) ?: return MediaStrategyResult.NotSupported("URI non prise en charge.")
        return when (val launched = launcher.launch(intent, context.action.targetAppLabel, context.executionContext)) {
            ActionResult.Completed -> await(context.action.targetPackage, context.remainingTimeoutMillis)
            is ActionResult.UserActionRequired -> MediaStrategyResult.UserActionRequired(launched.reason)
            is ActionResult.PermissionRequired -> MediaStrategyResult.TerminalFailure(launched.reason)
            is ActionResult.Cancelled -> MediaStrategyResult.Cancelled(launched.reason)
            is ActionResult.TimedOut -> MediaStrategyResult.TimedOut(launched.reason)
            is ActionResult.Failed -> MediaStrategyResult.RecoverableFailure(launched.reason)
        }
    }
}

class MediaSessionPlaybackStrategy(
    private val gateway: MediaSessionCommandGateway,
    waiter: MediaPlaybackWaiter,
) : WaitingMediaStrategy(waiter), MediaPlaybackStrategy {
    override val id = "media_session"
    override val priority = 20
    override val timeoutMillis = 8_000L
    override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) =
        capabilities.notificationListenerAvailable && capabilities.exactPackageSessionCount > 0 &&
            action.selectionPolicy != MediaSelectionPolicy.ASK_USER

    override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult = when (val command = gateway.request(context.action)) {
        is MediaSessionCommandResult.Sent -> await(context.action.targetPackage, context.remainingTimeoutMillis)
        is MediaSessionCommandResult.NotSupported -> MediaStrategyResult.NotSupported(command.reason)
        is MediaSessionCommandResult.Failed -> MediaStrategyResult.RecoverableFailure(command.reason)
    }
}

class ProviderIntentMediaStrategy(
    private val launcher: ExternalActivityGateway,
    private val adapter: MediaProviderAdapter,
    waiter: MediaPlaybackWaiter,
) : WaitingMediaStrategy(waiter), MediaPlaybackStrategy {
    override val id = "provider_intent"
    override val priority = 30
    override val timeoutMillis = 8_000L
    override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) =
        MediaProviderCapability.CAN_OPEN_SEARCH in capabilities.providerCapabilities

    override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult {
        val intent = adapter.buildSearchIntent(context.action.request())
            ?: return MediaStrategyResult.NotSupported("Recherche fournisseur indisponible.")
        return when (val launched = launcher.launch(intent, context.action.targetAppLabel, context.executionContext)) {
            ActionResult.Completed -> await(context.action.targetPackage, context.remainingTimeoutMillis)
            is ActionResult.UserActionRequired -> MediaStrategyResult.UserActionRequired(launched.reason)
            is ActionResult.Failed -> MediaStrategyResult.RecoverableFailure(launched.reason)
            is ActionResult.PermissionRequired -> MediaStrategyResult.TerminalFailure(launched.reason)
            is ActionResult.Cancelled -> MediaStrategyResult.Cancelled(launched.reason)
            is ActionResult.TimedOut -> MediaStrategyResult.TimedOut(launched.reason)
        }
    }
}

fun interface ManualMediaGuidance {
    fun show(action: ShortcutAction.PlayMedia, executionContext: ActionExecutionContext)

    fun clear() = Unit
}

class ManualFallbackMediaStrategy(
    private val appContext: Context,
    private val launcher: ExternalActivityGateway,
    private val adapter: MediaProviderAdapter,
    private val guidance: ManualMediaGuidance,
    waiter: MediaPlaybackWaiter,
) : WaitingMediaStrategy(waiter), MediaPlaybackStrategy {
    override val id = "manual_fallback"
    override val priority = 40
    override val timeoutMillis: Long? = null
    override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) = capabilities.manualFallbackAllowed

    override suspend fun execute(context: MediaStrategyContext): MediaStrategyResult {
        val action = context.action
        val searchIntent = adapter.buildSearchIntent(action.request())?.takeIf(launcher::canResolve)
        val intent = searchIntent
            ?: action.activityName?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(action.targetPackage, it) }
            ?: appContext.packageManager.getLaunchIntentForPackage(action.targetPackage)
            ?: Intent(Intent.ACTION_SEARCH).setPackage(action.targetPackage).putExtra(SearchManager.QUERY, action.searchQuery)
        return when (val launched = launcher.launch(intent, action.targetAppLabel, context.executionContext)) {
            ActionResult.Completed -> {
                guidance.show(action, context.executionContext)
                await(action.targetPackage, context.remainingTimeoutMillis)
            }
            is ActionResult.UserActionRequired -> MediaStrategyResult.UserActionRequired(launched.reason)
            is ActionResult.Failed -> MediaStrategyResult.TerminalFailure(launched.reason)
            is ActionResult.PermissionRequired -> MediaStrategyResult.TerminalFailure(launched.reason)
            is ActionResult.Cancelled -> MediaStrategyResult.Cancelled(launched.reason)
            is ActionResult.TimedOut -> MediaStrategyResult.TimedOut(launched.reason)
        }
    }

    override suspend fun cleanup() = guidance.clear()
}

class UnavailableMediaAutomationStrategy : MediaPlaybackStrategy {
    override val id = "advanced_automation"
    override val priority = 35
    override val timeoutMillis: Long? = null
    override fun isAvailable(action: ShortcutAction.PlayMedia, capabilities: MediaCapabilitySnapshot) = capabilities.advancedAutomationAllowed
    override suspend fun execute(context: MediaStrategyContext) = MediaStrategyResult.NotSupported("Automatisation avancée indisponible dans cette phase.")
}

private fun ShortcutAction.PlayMedia.request() = MediaOpenRequest(
    target = AppTarget(targetPackage, activityName),
    searchQuery = effectiveSearchQuery(),
    mediaUri = mediaUri,
)

private fun ShortcutAction.PlayMedia.effectiveSearchQuery(): String = buildList {
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
