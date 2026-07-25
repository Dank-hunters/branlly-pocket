package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.media.ExactPackagePlaybackTracker
import com.branlly.pocket.domain.media.MediaPlaybackStatus
import com.branlly.pocket.domain.media.MediaSessionSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Event-driven exact-package observer resilient to replacement and multiple concurrent sessions. */
class AndroidMediaPlaybackWaiter(
    context: Context,
) : MediaPlaybackWaiter {
    private val appContext = context.applicationContext

    override suspend fun waitForPlayback(packageName: String, timeoutMs: Long): MediaWaitResult {
        if (packageName.isBlank()) return MediaWaitResult.Failed("Le package multimédia est vide.")
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext)) {
            return MediaWaitResult.PermissionMissing
        }
        if (!BranllyMediaListener.isConnected()) return MediaWaitResult.ListenerUnavailable
        val component = ComponentName(appContext, BranllyMediaListener::class.java)
        val manager = appContext.getSystemService(MediaSessionManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        val tracker = ExactPackagePlaybackTracker(packageName)
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<MediaWaitResult> { continuation ->
                val finished = AtomicBoolean(false)
                val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
                lateinit var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener

                fun cleanup() {
                    if (!finished.compareAndSet(false, true)) return
                    callbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
                    callbacks.clear()
                    runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
                }

                fun complete(value: MediaWaitResult) {
                    if (!continuation.isActive) return
                    cleanup()
                    continuation.resume(value)
                }

                fun observe(sessions: List<MediaController>) {
                    if (finished.get()) return
                    val targets = sessions.filter { it.packageName == packageName }
                    val snapshots = sessions.map { controller ->
                        MediaSessionSnapshot(
                            sessionId = controller.sessionToken.hashCode().toString(),
                            packageName = controller.packageName,
                            status = when (controller.playbackState?.state) {
                                PlaybackState.STATE_PLAYING -> MediaPlaybackStatus.PLAYING
                                PlaybackState.STATE_PAUSED -> MediaPlaybackStatus.PAUSED
                                else -> MediaPlaybackStatus.OTHER
                            },
                        )
                    }
                    if (tracker.observe(snapshots)) {
                        complete(MediaWaitResult.Playing)
                        return
                    }
                    callbacks.filterKeys { it !in targets }.forEach { (controller, callback) ->
                        runCatching { controller.unregisterCallback(callback) }
                        callbacks.remove(controller)
                    }
                    targets.filterNot(callbacks::containsKey).forEach { controller ->
                        val callback = object : MediaController.Callback() {
                            override fun onPlaybackStateChanged(state: PlaybackState?) {
                                if (state?.state == PlaybackState.STATE_PLAYING) complete(MediaWaitResult.Playing)
                            }

                            override fun onSessionDestroyed() {
                                runCatching { manager.getActiveSessions(component) }
                                    .onSuccess(::observe)
                                    .onFailure { complete(MediaWaitResult.ServiceUnavailable(it.message ?: "Session média indisponible.")) }
                            }
                        }
                        callbacks[controller] = callback
                        runCatching { controller.registerCallback(callback, handler) }
                            .onFailure { complete(MediaWaitResult.ServiceUnavailable(it.message ?: "Callback média indisponible.")) }
                    }
                }

                activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions -> observe(sessions.orEmpty()) }
                continuation.invokeOnCancellation { cleanup() }
                try {
                    manager.addOnActiveSessionsChangedListener(activeSessionsListener, component, handler)
                    observe(manager.getActiveSessions(component))
                } catch (_: SecurityException) {
                    complete(MediaWaitResult.PermissionMissing)
                } catch (error: RuntimeException) {
                    complete(MediaWaitResult.ServiceUnavailable(error.message ?: "Service média indisponible."))
                }
            }
        }
        return result ?: if (tracker.observedTargetSession) MediaWaitResult.TimedOut else MediaWaitResult.SessionAbsent
    }
}
