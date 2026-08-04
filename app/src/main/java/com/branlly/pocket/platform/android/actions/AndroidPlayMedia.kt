package com.branlly.pocket.platform.android.actions

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession
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

    /** Reacquires an already-commanded active controller for observation only. */
    fun reacquire(
        targetPackage: String,
        sessionId: String,
    ): ObservableMediaController? = null
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

enum class MediaControllerAcquisitionSource { ACTIVE_SESSIONS, MEDIA_NOTIFICATION }

class PreparedMediaSessionCommand internal constructor(
    val command: String,
    val sessionId: String?,
    val observableController: ObservableMediaController?,
    val acquisitionSource: MediaControllerAcquisitionSource = MediaControllerAcquisitionSource.ACTIVE_SESSIONS,
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

internal data class NotificationMediaSessionSelectionCandidate(
    val index: Int,
    val packageName: String,
    val actions: Long,
    val playing: Boolean,
    val postedAtMillis: Long,
    val stableIdentity: String,
)

interface DirectMediaTransport {
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

internal fun selectNotificationMediaSession(
    candidates: List<NotificationMediaSessionSelectionCandidate>,
    targetPackage: String,
    hasUri: Boolean,
    searchQuery: String,
): MediaSessionSelection? =
    candidates
        .asSequence()
        .filter { it.packageName == targetPackage }
        .mapNotNull { candidate ->
            selectDirectCommand(candidate.actions, hasUri, searchQuery)?.let { command ->
                candidate to
                    MediaSessionSelection(candidate.index, command)
            }
        }.sortedWith(
            compareByDescending<Pair<NotificationMediaSessionSelectionCandidate, MediaSessionSelection>> { it.first.playing }
                .thenBy { it.second.command.ordinal }
                .thenByDescending { it.first.postedAtMillis }
                .thenBy { it.first.stableIdentity }
                .thenBy { it.first.index },
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

private data class AcquiredMediaController(
    val controller: MediaController,
    val source: MediaControllerAcquisitionSource,
    val notificationPostedAtMillis: Long = 0L,
    val stableIdentity: String = "",
)

/** Narrow Android-adapter port: no notification or token reaches the coordinator/domain. */
interface NotificationMediaSessionSource {
    fun lookup(targetPackage: String): NotificationMediaSessionLookup
}

data class NotificationMediaSessionLookup(
    val candidates: List<NotificationMediaSessionCandidate>,
    val rejection: DirectMediaFailureReason,
)

data class NotificationMediaSessionCandidate(
    val observableController: ObservableMediaController,
    val actions: Long,
    val playing: Boolean,
    val postedAtMillis: Long,
    val stableIdentity: String,
    val transport: DirectMediaTransport,
)

private class AndroidNotificationMediaSessionSource(
    context: Context,
) : NotificationMediaSessionSource {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override fun lookup(targetPackage: String): NotificationMediaSessionLookup {
        val notifications =
            BranllyMediaListener.activeNotificationsSnapshot()
                ?: return NotificationMediaSessionLookup(emptyList(), DirectMediaFailureReason.NOTIFICATION_LISTENER_DISCONNECTED)
        val target = notifications.filter { it.packageName == targetPackage }
        if (target.isEmpty()) return NotificationMediaSessionLookup(emptyList(), DirectMediaFailureReason.NO_TARGET_MEDIA_NOTIFICATION)
        var rejection = DirectMediaFailureReason.MEDIA_NOTIFICATION_WITHOUT_SESSION_TOKEN
        val candidates =
            target
                .mapNotNull { notification ->
                    val token =
                        notification.notification.extras
                            ?.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                            ?: return@mapNotNull null
                    val controller =
                        runCatching { MediaController(appContext, token) }.getOrElse {
                            rejection = DirectMediaFailureReason.INVALID_NOTIFICATION_SESSION_TOKEN
                            return@mapNotNull null
                        }
                    if (controller.packageName != targetPackage) {
                        rejection = DirectMediaFailureReason.NOTIFICATION_SESSION_PACKAGE_MISMATCH
                        return@mapNotNull null
                    }
                    val controls = controller.transportControls
                    NotificationMediaSessionCandidate(
                        observableController = AndroidObservableMediaController(controller),
                        actions = controller.playbackState?.actions ?: 0L,
                        playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                        postedAtMillis = notification.postTime,
                        stableIdentity = notification.key,
                        transport =
                            object : DirectMediaTransport {
                                override fun playFromUri(uri: String) = controls.playFromUri(Uri.parse(uri), Bundle.EMPTY)

                                override fun playFromSearch(query: String) = controls.playFromSearch(query, Bundle.EMPTY)

                                override fun prepareFromSearch(query: String) = controls.prepareFromSearch(query, Bundle.EMPTY)

                                override fun play() = controls.play()
                            },
                    )
                }.sortedWith(
                    compareByDescending<NotificationMediaSessionCandidate> { it.playing }
                        .thenByDescending { it.postedAtMillis }
                        .thenBy { it.stableIdentity },
                )
        return NotificationMediaSessionLookup(candidates, rejection)
    }
}

class AndroidMediaSessionCommandGateway(
    context: Context,
    private val notificationSessions: NotificationMediaSessionSource = AndroidNotificationMediaSessionSource(context),
) : MediaSessionCommandGateway {
    private val appContext = context.applicationContext

    override fun request(action: ShortcutAction.PlayMedia): MediaSessionCommandResult =
        when (val prepared = prepare(action)) {
            is MediaSessionPreparation.Ready -> prepared.command.dispatch()
            is MediaSessionPreparation.NotSupported -> MediaSessionCommandResult.NotSupported(prepared.reason, prepared.directFailureReason)
            is MediaSessionPreparation.Failed -> MediaSessionCommandResult.Failed(prepared.reason, prepared.directFailureReason)
        }

    override fun reacquire(
        targetPackage: String,
        sessionId: String,
    ): ObservableMediaController? =
        activeControllers()
            .firstOrNull {
                it.packageName == targetPackage && AndroidObservableMediaController(it).sessionId == sessionId
            }?.let(::AndroidObservableMediaController)
            ?: notificationSessions
                .lookup(targetPackage)
                .candidates
                .firstOrNull {
                    it.observableController.packageName == targetPackage && it.observableController.sessionId == sessionId
                }?.observableController

    override fun prepare(action: ShortcutAction.PlayMedia): MediaSessionPreparation {
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext)) {
            return MediaSessionPreparation.Failed(
                "L’accès aux notifications n’est pas autorisé.",
                DirectMediaFailureReason.MEDIA_SESSION_ACCESS_UNAVAILABLE,
            )
        }
        if (!BranllyMediaListener.isConnected()) {
            return MediaSessionPreparation.Failed(
                "Le service de notifications multimédias est indisponible.",
                DirectMediaFailureReason.NOTIFICATION_LISTENER_DISCONNECTED,
            )
        }

        // Active sessions retain priority. Notification tokens are only an acquisition fallback
        // before reservation; they never create a second dispatch path.
        val active = activeControllers()
        prepareFromCandidates(action, active.map { AcquiredMediaController(it, MediaControllerAcquisitionSource.ACTIVE_SESSIONS) })
            ?.let { return it }
        val notifications = notificationSessions.lookup(action.targetPackage)
        prepareFromNotificationCandidates(action, notifications.candidates)?.let { return it }

        val reason =
            when {
                active.any { it.packageName == action.targetPackage } -> DirectMediaFailureReason.COMMAND_NOT_SUPPORTED
                notifications.candidates.isEmpty() -> notifications.rejection
                else -> DirectMediaFailureReason.NOTIFICATION_SESSION_COMMAND_NOT_SUPPORTED
            }
        return MediaSessionPreparation.NotSupported(reason.userMessage, reason)
    }

    private fun activeControllers(): List<MediaController> =
        runCatching {
            appContext
                .getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(appContext, BranllyMediaListener::class.java))
        }.getOrElse {
            Log.w(TAG, "Accès MediaSession refusé", it)
            emptyList()
        }

    private fun prepareFromCandidates(
        action: ShortcutAction.PlayMedia,
        candidates: List<AcquiredMediaController>,
    ): MediaSessionPreparation.Ready? {
        val exact = candidates.filter { it.controller.packageName == action.targetPackage }
        val selection =
            selectDirectMediaSession(
                candidates =
                    exact.mapIndexed { index, candidate ->
                        MediaSessionCandidate(
                            index = index,
                            packageName = candidate.controller.packageName,
                            actions = candidate.controller.playbackState?.actions ?: 0L,
                            playing = candidate.controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                        )
                    },
                targetPackage = action.targetPackage,
                hasUri = !action.mediaUri.isNullOrBlank(),
                searchQuery = action.searchQuery,
            ) ?: return null
        val acquired = exact[selection.index]
        val controls = acquired.controller.transportControls
        val transport =
            object : DirectMediaTransport {
                override fun playFromUri(uri: String) = controls.playFromUri(Uri.parse(uri), Bundle.EMPTY)

                override fun playFromSearch(query: String) = controls.playFromSearch(query, Bundle.EMPTY)

                override fun prepareFromSearch(query: String) = controls.prepareFromSearch(query, Bundle.EMPTY)

                override fun play() = controls.play()
            }
        val observable = AndroidObservableMediaController(acquired.controller)
        return MediaSessionPreparation.Ready(
            PreparedMediaSessionCommand(
                command = selection.command.name,
                sessionId = observable.sessionId,
                observableController = observable,
                acquisitionSource = acquired.source,
            ) {
                runCatching {
                    val sent = dispatchDirectMediaCommand(selection.command, action.mediaUri, action.searchQuery, transport)
                    Log.i(TAG, "$sent envoyé source=${acquired.source} package=${action.targetPackage}")
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

    private fun prepareFromNotificationCandidates(
        action: ShortcutAction.PlayMedia,
        candidates: List<NotificationMediaSessionCandidate>,
    ): MediaSessionPreparation.Ready? {
        val selection =
            selectNotificationMediaSession(
                candidates =
                    candidates.mapIndexed { index, candidate ->
                        NotificationMediaSessionSelectionCandidate(
                            index = index,
                            packageName = candidate.observableController.packageName,
                            actions = candidate.actions,
                            playing = candidate.playing,
                            postedAtMillis = candidate.postedAtMillis,
                            stableIdentity = candidate.stableIdentity,
                        )
                    },
                targetPackage = action.targetPackage,
                hasUri = !action.mediaUri.isNullOrBlank(),
                searchQuery = action.searchQuery,
            ) ?: return null
        val candidate = candidates[selection.index]
        return MediaSessionPreparation.Ready(
            PreparedMediaSessionCommand(
                command = selection.command.name,
                sessionId = candidate.observableController.sessionId,
                observableController = candidate.observableController,
                acquisitionSource = MediaControllerAcquisitionSource.MEDIA_NOTIFICATION,
            ) {
                runCatching {
                    val sent = dispatchDirectMediaCommand(selection.command, action.mediaUri, action.searchQuery, candidate.transport)
                    MediaSessionCommandResult.Sent(sent, candidate.observableController.sessionId, candidate.observableController)
                }.getOrElse {
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
