package com.branlly.pocket.platform.android

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

/** Controls the currently active media session after the user grants notification access once. */
class MediaPlaybackController(
    private val context: Context,
) {
    fun play(): PlaybackStartResult {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, BranllyMediaListener::class.java)
        val controller =
            runCatching { manager.getActiveSessions(listener).firstOrNull() }.getOrNull()
                ?: return PlaybackStartResult.PermissionOrSessionMissing
        return runCatching {
            controller.transportControls.play()
            PlaybackStartResult.Started
        }.getOrElse { PlaybackStartResult.Failed }
    }
}

class BranllyMediaListener : NotificationListenerService()

sealed interface PlaybackStartResult {
    data object Started : PlaybackStartResult

    data object PermissionOrSessionMissing : PlaybackStartResult

    data object Failed : PlaybackStartResult
}
