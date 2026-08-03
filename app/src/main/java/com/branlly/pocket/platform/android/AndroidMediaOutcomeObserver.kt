package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.media.MediaBaselineMetadataState
import com.branlly.pocket.domain.media.MediaBaselinePlaybackState
import com.branlly.pocket.domain.media.MediaBaselineSession
import com.branlly.pocket.domain.media.MediaContentFingerprint
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaObservedSession
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.media.ObservableMediaController
import com.branlly.pocket.domain.media.confirmDirectPlayback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** One event-driven observer for one PLAY_MEDIA session; it owns every registered callback. */
class AndroidMediaOutcomeObserver(
    context: Context,
    private val targetPackage: String,
    restoredBaseline: MediaSessionBaseline? = null,
) : MediaOutcomeObserver {
    private val appContext = context.applicationContext
    private val component = ComponentName(appContext, BranllyMediaListener::class.java)
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var cleanup: (() -> Unit)? = null

    @Volatile private var operationDispatched = false

    @Volatile private var commandedSessionId: String? = null

    @Volatile private var commandedController: ObservableMediaController? = null

    /** The first explicit-controller snapshot is a post-acquisition baseline, never evidence. */
    @Volatile private var commandedControllerObservationArmed = false

    @Volatile private var inspectSessions: (() -> Unit)? = null

    @Volatile private var attachCommandedController: (() -> Unit)? = null

    @Volatile private var preDispatchSessions: List<MediaBaselineSession>? = null

    @Volatile private var preDispatchCaptureFailed = false
    private val unconfirmedSessions = mutableSetOf<String>()

    override val baseline: MediaSessionBaseline = restoredBaseline ?: captureBaseline()

    override fun capturePreDispatchState() {
        operationDispatched = false
        commandedSessionId = null
        commandedController = null
        commandedControllerObservationArmed = false
        val captured =
            runCatching {
                manager
                    .getActiveSessions(component)
                    .filter { it.packageName == targetPackage }
                    .map(::baselineSession)
            }
        preDispatchSessions = captured.getOrNull()
        preDispatchCaptureFailed = captured.isFailure
        if (preDispatchCaptureFailed) {
            Log.w(TAG, "Pre-dispatch baseline unavailable; direct confirmation disabled", captured.exceptionOrNull())
        } else {
            Log.i(TAG, "Pre-dispatch baseline sessions=${preDispatchSessions?.size ?: 0}")
        }
    }

    override fun prepareCommandedController(controller: ObservableMediaController?) {
        commandedController = controller
        commandedControllerObservationArmed = false
        controller?.snapshot()?.takeIf { it.packageName == targetPackage }?.let { snapshot ->
            val baselineSession =
                MediaBaselineSession(
                    sessionId = snapshot.sessionId,
                    playbackState = snapshot.playbackState,
                    content = snapshot.content,
                )
            preDispatchSessions =
                (preDispatchSessions.orEmpty().filterNot { it.sessionId == snapshot.sessionId } + baselineSession)
        }
        handler.post { attachCommandedController?.invoke() }
    }

    override fun onOperationDispatched(
        commandedSessionId: String?,
        commandedController: ObservableMediaController?,
    ) {
        this.commandedSessionId = commandedSessionId
        this.commandedController = commandedController
        commandedControllerObservationArmed = commandedController == null
        operationDispatched = true
        synchronized(unconfirmedSessions) { unconfirmedSessions.clear() }
        Log.i(TAG, "Operation dispatched session=${commandedSessionId ?: "unscoped"}")
        handler.post {
            attachCommandedController?.invoke()
            inspectSessions?.invoke()
        }
    }

    override suspend fun awaitOutcome(timeoutMillis: Long): MediaObservedOutcome {
        if (closed.get()) return MediaObservedOutcome.Unavailable("L’observateur média est fermé.")
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext)) {
            return MediaObservedOutcome.Unavailable("Autorisez l’accès aux notifications pour confirmer la lecture.")
        }
        if (!BranllyMediaListener.isConnected()) {
            return MediaObservedOutcome.Unavailable(
                "Le service NotificationListener est indisponible.",
            )
        }
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val callbacks = ConcurrentHashMap<MediaController, MediaController.Callback>()
                var commandedSubscription: AutoCloseable? = null
                lateinit var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener
                val finished = AtomicBoolean(false)

                fun dispose() {
                    if (!finished.compareAndSet(false, true)) return
                    callbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
                    callbacks.clear()
                    runCatching { commandedSubscription?.close() }
                    commandedSubscription = null
                    runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
                    cleanup = null
                    inspectSessions = null
                    attachCommandedController = null
                }

                fun complete(outcome: MediaObservedOutcome) {
                    if (!continuation.isActive) return
                    dispose()
                    continuation.resume(outcome)
                }

                fun observed(
                    snapshot: MediaObservedSession,
                    currentSessionIds: Set<String>,
                ): MediaObservedOutcome.PlaybackStarted? {
                    val id = snapshot.sessionId
                    if (preDispatchCaptureFailed) return null
                    // A controller may have been acquired outside getActiveSessions(). Its
                    // first snapshot can describe pre-existing playback, so never use it as
                    // confirmation until a later callback/snapshot has advanced the epoch.
                    if (snapshot.sessionId == commandedSessionId && !commandedControllerObservationArmed) return null
                    val proof =
                        baseline.copy(sessions = preDispatchSessions ?: baseline.sessions).confirmDirectPlayback(
                            observed = snapshot,
                            targetPackage = targetPackage,
                            commandedSessionId = commandedSessionId,
                            commandDispatched = operationDispatched,
                            commandedSessionStillPresent = commandedSessionId in currentSessionIds,
                        )
                    if (proof == null) {
                        if (operationDispatched && snapshot.playbackState == MediaBaselinePlaybackState.PLAYING &&
                            synchronized(unconfirmedSessions) { unconfirmedSessions.add(id) }
                        ) {
                            Log.i(TAG, "Target session remains PLAYING without reliable content change session=$id")
                        }
                        return null
                    }
                    Log.i(TAG, "Direct media confirmed proof=$proof session=$id")
                    return MediaObservedOutcome.PlaybackStarted(
                        sessionId = id,
                        contentConfirmed = proof.contains("content_changed"),
                        preexisting = id in baseline.playingSessionIds,
                        proof = proof,
                    )
                }

                fun observe(sessions: List<MediaController>) {
                    if (finished.get()) return
                    val targets = sessions.filter { it.packageName == targetPackage }
                    val currentSessionIds = targets.mapTo(linkedSetOf()) { it.sessionToken.hashCode().toString() }
                    val snapshots =
                        targets
                            .map {
                                MediaObservedSession(
                                    sessionId = it.sessionToken.hashCode().toString(),
                                    packageName = it.packageName,
                                    playbackState = it.playbackState.toBaselineState(),
                                    content = it.contentFingerprint(),
                                )
                            }.toMutableList()
                    commandedController?.snapshot()?.takeIf { it.packageName == targetPackage }?.let { snapshot ->
                        currentSessionIds += snapshot.sessionId
                        if (snapshots.none { it.sessionId == snapshot.sessionId }) snapshots += snapshot
                    }
                    snapshots
                        .firstNotNullOfOrNull { observed(it, currentSessionIds) }
                        ?.let(::complete)
                        ?.also { return }
                    // The initial explicit snapshot only establishes a local baseline. Any
                    // later callback is evaluated as post-dispatch evidence.
                    commandedControllerObservationArmed = true
                    callbacks.filterKeys { it !in targets }.forEach { (controller, callback) ->
                        runCatching { controller.unregisterCallback(callback) }
                        callbacks.remove(controller)
                    }
                    targets.filterNot(callbacks::containsKey).forEach { controller ->
                        val callback =
                            object : MediaController.Callback() {
                                override fun onPlaybackStateChanged(state: PlaybackState?) {
                                    runCatching { manager.getActiveSessions(component) }.onSuccess(::observe)
                                }

                                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                                    runCatching { manager.getActiveSessions(component) }.onSuccess(::observe)
                                }

                                override fun onSessionDestroyed() {
                                    runCatching { manager.getActiveSessions(component) }
                                        .onSuccess(::observe)
                                        .onFailure {
                                            complete(
                                                MediaObservedOutcome.Unavailable(it.message ?: "Session média indisponible."),
                                            )
                                        }
                                }
                            }
                        callbacks[controller] = callback
                        runCatching { controller.registerCallback(callback, handler) }
                            .onFailure { complete(MediaObservedOutcome.Unavailable(it.message ?: "Callback média indisponible.")) }
                        if (finished.get()) {
                            callbacks.remove(controller, callback)
                            runCatching { controller.unregisterCallback(callback) }
                        }
                    }
                }

                sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { observe(it.orEmpty()) }
                inspectSessions = {
                    runCatching { manager.getActiveSessions(component) }
                        .onSuccess(::observe)
                        .onFailure { complete(MediaObservedOutcome.Unavailable(it.message ?: "Session média indisponible.")) }
                }
                attachCommandedController = attachment@{
                    if (commandedSubscription != null) return@attachment
                    val controller = commandedController ?: return@attachment
                    commandedSubscription =
                        runCatching {
                            controller.subscribe { handler.post { inspectSessions?.invoke() } }
                        }.onFailure {
                            Log.w(TAG, "Commanded controller callback unavailable", it)
                        }.getOrNull()
                }
                cleanup = ::dispose
                continuation.invokeOnCancellation { dispose() }
                try {
                    manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                    observe(manager.getActiveSessions(component))
                } catch (error: SecurityException) {
                    complete(MediaObservedOutcome.Unavailable("Autorisation MediaSession refusée."))
                } catch (error: RuntimeException) {
                    complete(MediaObservedOutcome.Unavailable(error.message ?: "Service média indisponible."))
                }
            }
        } ?: MediaObservedOutcome.TimedOut
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) cleanup?.invoke()
    }

    private fun captureBaseline(): MediaSessionBaseline {
        val sessions =
            runCatching {
                manager
                    .getActiveSessions(
                        component,
                    ).filter { it.packageName == targetPackage }
            }.getOrDefault(emptyList())
        val representative = sessions.firstOrNull()
        val metadata = representative?.metadata
        val state = representative?.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val uri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
        val metadataCount = listOf(title, artist, album, uri).count { !it.isNullOrBlank() }
        val baselineSessions = sessions.map(::baselineSession)
        Log.i(
            TAG,
            "Baseline targetSessions=${baselineSessions.size} playing=${baselineSessions.count {
                it.playbackState == MediaBaselinePlaybackState.PLAYING
            }} " +
                "contentFingerprints=${baselineSessions.count { it.content.isComparable() }}",
        )
        return MediaSessionBaseline(
            playingSessionIds =
                sessions
                    .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    .mapTo(linkedSetOf()) { it.sessionToken.hashCode().toString() },
            knownSessionIds = sessions.mapTo(linkedSetOf()) { it.sessionToken.hashCode().toString() },
            sessionPresent = representative != null,
            packageName = representative?.packageName,
            playbackState =
                when (state?.state) {
                    null -> MediaBaselinePlaybackState.NONE
                    PlaybackState.STATE_STOPPED -> MediaBaselinePlaybackState.STOPPED
                    PlaybackState.STATE_PAUSED -> MediaBaselinePlaybackState.PAUSED
                    PlaybackState.STATE_PLAYING -> MediaBaselinePlaybackState.PLAYING
                    else -> MediaBaselinePlaybackState.UNKNOWN
                },
            title = title,
            artist = artist,
            album = album,
            mediaUri = uri,
            sessionId = representative?.sessionToken?.hashCode()?.toString(),
            positionMillis = state?.position?.takeIf { it >= 0 },
            capturedAtMillis = System.currentTimeMillis(),
            metadataState =
                when (metadataCount) {
                    0 -> MediaBaselineMetadataState.ABSENT
                    4 -> MediaBaselineMetadataState.COMPLETE
                    else -> MediaBaselineMetadataState.PARTIAL
                },
            sessions = baselineSessions,
        )
    }

    private fun baselineSession(controller: MediaController): MediaBaselineSession =
        MediaBaselineSession(
            sessionId = controller.sessionToken.hashCode().toString(),
            playbackState = controller.playbackState.toBaselineState(),
            content = controller.contentFingerprint(),
        )

    private fun PlaybackState?.toBaselineState(): MediaBaselinePlaybackState =
        when (this?.state) {
            null -> MediaBaselinePlaybackState.NONE
            PlaybackState.STATE_STOPPED -> MediaBaselinePlaybackState.STOPPED
            PlaybackState.STATE_PAUSED -> MediaBaselinePlaybackState.PAUSED
            PlaybackState.STATE_PLAYING -> MediaBaselinePlaybackState.PLAYING
            else -> MediaBaselinePlaybackState.UNKNOWN
        }

    private fun MediaController.contentFingerprint(): MediaContentFingerprint {
        val metadata = metadata
        val queueId =
            playbackState?.activeQueueItemId?.takeIf {
                it !=
                    android.media.session.MediaSession.QueueItem.UNKNOWN_ID
                        .toLong()
            }
        return MediaContentFingerprint(
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            activeQueueItemId = queueId,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMillis = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
        )
    }

    private companion object {
        const val TAG = "BranllyPlayMedia"
    }
}
