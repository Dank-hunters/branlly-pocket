package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Bounded observer for a user-controlled external media session. */
class MediaPlaybackMonitor(
    private val context: Context,
) {
    suspend fun requestPlayAndAwait(
        packageName: String,
        timeoutMillis: Long,
    ): MediaPlaybackAwaitResult {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, BranllyMediaListener::class.java)
        val completed =
            withTimeoutOrNull(timeoutMillis) {
                while (true) {
                    val controller =
                        runCatching {
                            manager.getActiveSessions(listener).firstOrNull { it.packageName == packageName }
                        }.getOrNull()
                    if (controller == null) {
                        delay(500)
                        continue
                    }
                    val state = controller.playbackState
                    if (state?.state == PlaybackState.STATE_PLAYING) return@withTimeoutOrNull true
                    if (state != null && state.actions and PlaybackState.ACTION_PLAY != 0L) {
                        runCatching { controller.transportControls.play() }
                    }
                    if (awaitPlaying(controller, 1_500L)) return@withTimeoutOrNull true
                }
            }
        return when {
            completed == true -> MediaPlaybackAwaitResult.Playing
            hasNotificationAccess(manager, listener) -> MediaPlaybackAwaitResult.TimedOut
            else -> MediaPlaybackAwaitResult.NotificationAccessMissing
        }
    }

    private suspend fun awaitPlaying(
        controller: MediaController,
        timeoutMillis: Long,
    ): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val callback =
                    object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            if (state?.state == PlaybackState.STATE_PLAYING && continuation.isActive) continuation.resume(true)
                        }
                    }
                controller.registerCallback(callback)
                continuation.invokeOnCancellation { controller.unregisterCallback(callback) }
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING && continuation.isActive) continuation.resume(true)
            }
        } ?: false

    private fun hasNotificationAccess(
        manager: MediaSessionManager,
        listener: ComponentName,
    ): Boolean = runCatching { manager.getActiveSessions(listener) }.isSuccess
}

sealed interface MediaPlaybackAwaitResult {
    data object Playing : MediaPlaybackAwaitResult

    data object TimedOut : MediaPlaybackAwaitResult

    data object NotificationAccessMissing : MediaPlaybackAwaitResult
}
