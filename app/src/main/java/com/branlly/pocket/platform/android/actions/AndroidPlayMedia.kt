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
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.media.MediaBaselinePlaybackState
import com.branlly.pocket.domain.media.MediaCapabilitySnapshot
import com.branlly.pocket.domain.media.MediaContentFingerprint
import com.branlly.pocket.domain.media.MediaObservedSession
import com.branlly.pocket.domain.media.ObservableMediaController
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
        val sessionId: String? = null,
        val observableController: ObservableMediaController? = null,
    ) : MediaSessionCommandResult

    data class NotSupported(
        val reason: String,
        val directFailureReason: DirectMediaFailureReason? = null,
    ) : MediaSessionCommandResult

    data class Failed(
        val reason: String,
        val directFailureReason: DirectMediaFailureReason? = null,
    ) : MediaSessionCommandResult
}

fun interface MediaSessionCommandGateway {
    /** Legacy one-shot entry point kept for existing fakes and callers. */
    fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult

    /**
     * Selects an exact controller without touching the target app. Android gateways override
     * this so the coordinator can snapshot and subscribe before reserving the external effect.
     */
    fun prepare(action: ShortcutAction.PlayMedia): MediaSessionPreparation =
        when (val result = request(action)) {
            is MediaSessionCommandResult.Sent -> {
                // Compatibility for existing in-memory gateways. The Android implementation
                // overrides this path and prepares without dispatching.
                MediaSessionPreparation.Ready(
                    PreparedMediaSessionCommand(result.command, result.sessionId, result.observableController) { result },
                )
            }

            is MediaSessionCommandResult.NotSupported -> {
                MediaSessionPreparation.NotSupported(result.reason, result.directFailureReason)
            }

            is MediaSessionCommandResult.Failed -> {
                MediaSessionPreparation.Failed(result.reason, result.directFailureReason)
            }
        }
}

sealed interface MediaSessionPreparation {
    data class Ready(
        val command: PreparedMediaSessionCommand,
    ) : MediaSessionPreparation

    data class NotSupported(
        val reason: String,
        val directFailureReason: DirectMediaFailureReason? = null,
    ) : MediaSessionPreparation

    data class Failed(
        val reason: String,
        val directFailureReason: DirectMediaFailureReason? = null,
    ) : MediaSessionPreparation
}

class PreparedMediaSessionCommand internal constructor(
    val command: String,
    val sessionId: String?,
    val observableController: ObservableMediaController?,
    private val dispatchBlock: () -> MediaSessionCommandResult,
) {
    fun dispatch(): MediaSessionCommandResult = dispatchBlock()
}

internal enum class DirectMediaSessionCommand {
    PLAY_FROM_URI,
    PLAY_FROM_SEARCH,
    PREPARE_FROM_SEARCH_AND_PLAY,
}

internal data class MediaSessionCandidate(
    val index: Int,
    val packageName: String,
    val actions: Long,
    val playing: Boolean,
)

internal data class MediaSessionSelection(
    val index: Int,
    val command: DirectMediaSessionCommand,
)

internal interface DirectMediaTransport {
    fun playFromUri(uri: String)

    fun playFromSearch(query: String)

    fun prepareFromSearch(query: String)

    fun play()
}

internal fun dispatchDirectMediaCommand(
    command: DirectMediaSessionCommand,
    mediaUri: String?,
    searchQuery: String,
    transport: DirectMediaTransport,
): String =
    when (command) {
        DirectMediaSessionCommand.PLAY_FROM_URI -> {
            transport.playFromUri(requireNotNull(mediaUri))
            "playFromUri"
        }

        DirectMediaSessionCommand.PLAY_FROM_SEARCH -> {
            transport.playFromSearch(searchQuery)
            "playFromSearch"
        }

        DirectMediaSessionCommand.PREPARE_FROM_SEARCH_AND_PLAY -> {
            transport.prepareFromSearch(searchQuery)
            transport.play()
            "prepareFromSearch+play"
        }
    }

internal fun selectDirectMediaSession(
    candidates: List<MediaSessionCandidate>,
    targetPackage: String,
    hasUri: Boolean,
    searchQuery: String,
): MediaSessionSelection? =
    candidates
        .asSequence()
        .filter { it.packageName == targetPackage }
        .mapNotNull { candidate ->
            selectDirectCommand(candidate.actions, hasUri, searchQuery)?.let { command ->
                candidate to MediaSessionSelection(candidate.index, command)
            }
        }.sortedWith(
            compareByDescending<Pair<MediaSessionCandidate, MediaSessionSelection>> { it.first.playing }
                .thenBy { it.second.command.ordinal },
        ).firstOrNull()
        ?.second

internal fun selectDirectCommand(
    actions: Long,
    hasUri: Boolean,
    searchQuery: String,
): DirectMediaSessionCommand? =
    when {
        hasUri && actions supports PlaybackState.ACTION_PLAY_FROM_URI -> DirectMediaSessionCommand.PLAY_FROM_URI

        searchQuery.isNotBlank() && actions supports PlaybackState.ACTION_PLAY_FROM_SEARCH -> DirectMediaSessionCommand.PLAY_FROM_SEARCH

        searchQuery.isNotBlank() &&
            actions supports PlaybackState.ACTION_PREPARE_FROM_SEARCH &&
            actions supports PlaybackState.ACTION_PLAY -> DirectMediaSessionCommand.PREPARE_FROM_SEARCH_AND_PLAY

        else -> null
    }

private infix fun Long.supports(action: Long): Boolean = this and action != 0L

class AndroidMediaSessionCommandGateway(
    context: Context,
) : MediaSessionCommandGateway {
    private val appContext = context.applicationContext

    override fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult =
        when (val prepared = prepare(action)) {
            is MediaSessionPreparation.Ready -> prepared.command.dispatch()
            is MediaSessionPreparation.NotSupported -> MediaSessionCommandResult.NotSupported(prepared.reason, prepared.directFailureReason)
            is MediaSessionPreparation.Failed -> MediaSessionCommandResult.Failed(prepared.reason, prepared.directFailureReason)
        }

    override fun prepare(action: ShortcutAction.PlayMedia): MediaSessionPreparation {
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext) ||
            !BranllyMediaListener.isConnected()
        ) {
            Log.w(TAG, "MediaSession indisponible: listener non autorisé ou déconnecté")
            return MediaSessionPreparation.Failed(
                "Le service MediaSession n’est pas disponible.",
                DirectMediaFailureReason.MEDIA_SESSION_ACCESS_UNAVAILABLE,
            )
        }
        val controllers =
            runCatching {
                appContext
                    .getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
            }.getOrElse {
                Log.w(TAG, "Accès MediaSession refusé", it)
                return MediaSessionPreparation.Failed(
                    it.message ?: "Accès MediaSession refusé.",
                    DirectMediaFailureReason.MEDIA_SESSION_ACCESS_UNAVAILABLE,
                )
            }
        val exactControllers = controllers.filter { it.packageName == action.targetPackage }
        if (exactControllers.isEmpty()) {
            Log.i(TAG, "Aucune session du package cible: ${action.targetPackage}")
            return MediaSessionPreparation.NotSupported("Aucune session du package cible.", DirectMediaFailureReason.NO_TARGET_SESSION)
        }
        exactControllers.forEach { controller ->
            val actions = controller.playbackState?.actions ?: 0L
            Log.i(
                TAG,
                "Session trouvée package=${controller.packageName} " +
                    "playFromSearch=${actions supports PlaybackState.ACTION_PLAY_FROM_SEARCH} " +
                    "prepareFromSearch=${actions supports PlaybackState.ACTION_PREPARE_FROM_SEARCH} " +
                    "play=${actions supports PlaybackState.ACTION_PLAY}",
            )
        }
        val selection =
            selectDirectMediaSession(
                candidates =
                    controllers.mapIndexed { index, controller ->
                        MediaSessionCandidate(
                            index = index,
                            packageName = controller.packageName,
                            actions = controller.playbackState?.actions ?: 0L,
                            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                        )
                    },
                targetPackage = action.targetPackage,
                hasUri = !action.mediaUri.isNullOrBlank(),
                searchQuery = action.searchQuery,
            ) ?: run {
                Log.i(TAG, "Session cible sans commande de recherche compatible")
                return MediaSessionPreparation.NotSupported(
                    "La session n’annonce aucune commande compatible.",
                    DirectMediaFailureReason.COMMAND_NOT_SUPPORTED,
                )
            }
        val controls = controllers[selection.index].transportControls
        val transport =
            object : DirectMediaTransport {
                override fun playFromUri(uri: String) = controls.playFromUri(Uri.parse(uri), Bundle.EMPTY)

                override fun playFromSearch(query: String) = controls.playFromSearch(query, Bundle.EMPTY)

                override fun prepareFromSearch(query: String) = controls.prepareFromSearch(query, Bundle.EMPTY)

                override fun play() = controls.play()
            }
        val controller = controllers[selection.index]
        val observable = AndroidObservableMediaController(controller)
        return MediaSessionPreparation.Ready(
            PreparedMediaSessionCommand(
                command = selection.command.name,
                sessionId = observable.sessionId,
                observableController = observable,
            ) {
                runCatching {
                    val sent = dispatchDirectMediaCommand(selection.command, action.mediaUri, action.searchQuery, transport)
                    Log.i(TAG, "$sent envoyé package=${action.targetPackage}")
                    MediaSessionCommandResult.Sent(sent, observable.sessionId, observable)
                }.getOrElse {
                    Log.w(TAG, "Commande MediaSession refusée", it)
                    MediaSessionCommandResult.Failed(
                        it.message ?: "Commande MediaSession refusée.",
                        DirectMediaFailureReason.COMMAND_EXCEPTION,
                    )
                }
            },
        )
    }

    private companion object {
        const val TAG = "BranllyPlayMedia"
    }
}

private class AndroidObservableMediaController(
    private val controller: MediaController,
) : ObservableMediaController {
    override val sessionId: String = controller.sessionToken.hashCode().toString()
    override val packageName: String = controller.packageName

    override fun snapshot(): MediaObservedSession? =
        runCatching {
            val metadata = controller.metadata
            MediaObservedSession(
                sessionId = sessionId,
                packageName = packageName,
                playbackState =
                    when (controller.playbackState?.state) {
                        PlaybackState.STATE_PLAYING -> MediaBaselinePlaybackState.PLAYING
                        PlaybackState.STATE_PAUSED -> MediaBaselinePlaybackState.PAUSED
                        PlaybackState.STATE_STOPPED -> MediaBaselinePlaybackState.STOPPED
                        null -> MediaBaselinePlaybackState.NONE
                        else -> MediaBaselinePlaybackState.UNKNOWN
                    },
                content =
                    MediaContentFingerprint(
                        mediaId = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID),
                        activeQueueItemId = controller.playbackState?.activeQueueItemId?.takeIf { it >= 0L },
                        title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                        artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
                        album = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM),
                        durationMillis = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L },
                        mediaUri = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_URI),
                    ),
            )
        }.getOrNull()

    override fun subscribe(listener: () -> Unit): AutoCloseable {
        val callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = listener()

                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = listener()

                override fun onSessionDestroyed() = listener()
            }
        controller.registerCallback(callback)
        val closed =
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) runCatching { controller.unregisterCallback(callback) }
        }
    }
}

fun interface ManualMediaGuidance {
    fun show(
        action: ShortcutAction.PlayMedia,
        executionContext: ActionExecutionContext,
    )

    /** Short, non-persistent explanation shown when Branlly opens the player. */
    fun showInfo(
        message: String,
        executionContext: ActionExecutionContext,
    ) = Unit

    /** Displays an already-decided direct playback failure; it never alters media flow. */
    fun showDirectFailure(
        notice: DirectMediaFailureNotice,
        executionContext: ActionExecutionContext,
    ): Boolean = false

    fun clear() = Unit
}
