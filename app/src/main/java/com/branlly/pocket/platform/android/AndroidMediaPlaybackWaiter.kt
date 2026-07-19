package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Exact-package MediaSession observer resilient to session replacement and multiple sessions. */
class AndroidMediaPlaybackWaiter(
    private val context: Context,
) : MediaPlaybackWaiter {
    override suspend fun waitForPlayback(
        packageName: String,
        timeoutMs: Long,
    ): MediaWaitResult {
        if (packageName.isBlank()) return MediaWaitResult.Failed("Le package multimédia est vide.")
        val listener = ComponentName(context, BranllyMediaListener::class.java)
        if (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)) {
            return MediaWaitResult.Failed("Accès aux notifications requis pour surveiller la lecture média.")
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        var accessFailure: String? = null
        val playing = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val sessions = try {
                    manager.getActiveSessions(listener)
                } catch (error: SecurityException) {
                    accessFailure = "Android refuse l’accès aux sessions multimédias."
                    return@withTimeoutOrNull false
                } catch (error: RuntimeException) {
                    accessFailure = "Le service d’accès aux sessions multimédias est indisponible."
                    return@withTimeoutOrNull false
                }
                val targets = sessions.filter { controller -> controller.packageName == packageName }
                targets.forEach { controller ->
                    val state = controller.playbackState?.state
                    Log.i(TAG, "MEDIA_SESSION_FOUND package=${controller.packageName}")
                    Log.i(TAG, "MEDIA_PLAYBACK_STATE package=${controller.packageName} state=$state")
                }
                if (targets.any { controller -> controller.playbackState?.state == PlaybackState.STATE_PLAYING }) {
                    return@withTimeoutOrNull true
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        accessFailure?.let { return MediaWaitResult.Failed(it) }
        return if (playing == true) MediaWaitResult.Playing else MediaWaitResult.TimedOut
    }

    private companion object {
        const val TAG = "MediaPlaybackWaiter"
        const val POLL_INTERVAL_MILLIS = 250L
    }
}
