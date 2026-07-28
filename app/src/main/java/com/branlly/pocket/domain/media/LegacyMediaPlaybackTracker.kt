package com.branlly.pocket.domain.media

/** Compatibility state used only by the advanced WAIT_FOR_MEDIA_PLAYBACK action. */
enum class MediaPlaybackStatus { PLAYING, PAUSED, OTHER }

data class MediaSessionSnapshot(
    val sessionId: String,
    val packageName: String,
    val status: MediaPlaybackStatus,
)

class ExactPackagePlaybackTracker(
    private val targetPackage: String,
) {
    var observedTargetSession: Boolean = false
        private set

    fun observe(sessions: List<MediaSessionSnapshot>): Boolean {
        val targets = sessions.filter { it.packageName == targetPackage }
        if (targets.isNotEmpty()) observedTargetSession = true
        return targets.any { it.status == MediaPlaybackStatus.PLAYING }
    }
}
