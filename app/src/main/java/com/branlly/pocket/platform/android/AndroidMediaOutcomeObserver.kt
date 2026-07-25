package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.media.MediaObservedOutcome
import com.branlly.pocket.domain.media.MediaOutcomeObserver
import com.branlly.pocket.domain.media.MediaSessionBaseline
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** One event-driven observer for one PLAY_MEDIA session; it owns every registered callback. */
class AndroidMediaOutcomeObserver(
    context: Context,
    private val targetPackage: String,
) : MediaOutcomeObserver {
    private val appContext = context.applicationContext
    private val component = ComponentName(appContext, BranllyMediaListener::class.java)
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var cleanup: (() -> Unit)? = null

    override val baseline: MediaSessionBaseline = captureBaseline()

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
                val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
                lateinit var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener
                val finished = AtomicBoolean(false)

                fun dispose() {
                    if (!finished.compareAndSet(false, true)) return
                    callbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
                    callbacks.clear()
                    runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
                    cleanup = null
                }

                fun complete(outcome: MediaObservedOutcome) {
                    if (!continuation.isActive) return
                    dispose()
                    continuation.resume(outcome)
                }

                fun observed(controller: MediaController): MediaObservedOutcome.PlaybackStarted? {
                    if (controller.packageName != targetPackage ||
                        controller.playbackState?.state != PlaybackState.STATE_PLAYING
                    ) {
                        return null
                    }
                    val id = controller.sessionToken.hashCode().toString()
                    if (id in baseline.playingSessionIds) return null
                    return MediaObservedOutcome.PlaybackStarted(id, contentConfirmed = false, preexisting = false)
                }

                fun observe(sessions: List<MediaController>) {
                    if (finished.get()) return
                    sessions
                        .filter { it.packageName == targetPackage }
                        .firstNotNullOfOrNull(::observed)
                        ?.let(::complete)
                        ?.also { return }
                    val targets = sessions.filter { it.packageName == targetPackage }
                    callbacks.filterKeys { it !in targets }.forEach { (controller, callback) ->
                        runCatching { controller.unregisterCallback(callback) }
                        callbacks.remove(controller)
                    }
                    targets.filterNot(callbacks::containsKey).forEach { controller ->
                        val callback =
                            object : MediaController.Callback() {
                                override fun onPlaybackStateChanged(state: PlaybackState?) {
                                    observed(controller)?.let(::complete)
                                }

                                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                                    observed(controller)?.let(::complete)
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
                    }
                }

                sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { observe(it.orEmpty()) }
                cleanup = ::dispose
                continuation.invokeOnCancellation(::dispose)
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
        return MediaSessionBaseline(
            playingSessionIds =
                sessions
                    .filter {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING
                    }.mapTo(mutableSetOf()) { it.sessionToken.hashCode().toString() },
            knownSessionIds = sessions.mapTo(mutableSetOf()) { it.sessionToken.hashCode().toString() },
        )
    }
}
