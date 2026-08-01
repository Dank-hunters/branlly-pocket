package com.branlly.pocket.domain

import com.branlly.pocket.domain.media.MediaBaselinePlaybackState
import com.branlly.pocket.domain.media.MediaBaselineSession
import com.branlly.pocket.domain.media.MediaContentFingerprint
import com.branlly.pocket.domain.media.MediaObservedSession
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.media.confirmDirectPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaOutcomeConfirmationPolicyTest {
    @Test fun `new target session playing after dispatch confirms direct playback`() {
        assertEquals("new_session_playing", baseline().confirm(observed("new", MediaBaselinePlaybackState.PLAYING), null))
    }

    @Test fun `paused commanded session transitioning to playing confirms direct playback`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PAUSED))
        assertEquals("commanded_session_started", baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING), "commanded"))
    }

    @Test fun `active commanded session with changed media fingerprint confirms direct playback`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "old"))
        assertEquals(
            "commanded_session_content_changed",
            baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "new"), "commanded"),
        )
    }

    @Test fun `queue item change confirms active commanded session`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, queueItemId = 1))
        assertEquals(
            "commanded_session_content_changed",
            baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, queueItemId = 2), "commanded"),
        )
    }

    @Test fun `position is not a content proof`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "same"))
        assertNull(baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "same"), "commanded"))
    }

    @Test fun `active session without comparable metadata remains unconfirmed`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING))
        assertNull(baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING), "commanded"))
    }

    @Test fun `other package never confirms playback`() {
        assertNull(baseline().confirm(observed("new", MediaBaselinePlaybackState.PLAYING, packageName = "other.player"), null))
    }

    @Test fun `other preexisting target session cannot confirm a commanded session`() {
        val baseline =
            baseline(
                session("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "same"),
                session("other", MediaBaselinePlaybackState.PAUSED),
            )
        assertNull(baseline.confirm(observed("other", MediaBaselinePlaybackState.PLAYING), "commanded", commandedStillPresent = true))
    }

    @Test fun `replacement target session confirms only after commanded session disappeared`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, mediaId = "old"))
        assertEquals(
            "replacement_session_playing",
            baseline.confirm(observed("replacement", MediaBaselinePlaybackState.PLAYING), "commanded", commandedStillPresent = false),
        )
        assertNull(baseline.confirm(observed("replacement", MediaBaselinePlaybackState.PLAYING), "commanded", commandedStillPresent = true))
    }

    @Test fun `metadata before playback state confirms once playing is observed`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, title = "old"))
        assertNull(baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PAUSED, title = "new"), "commanded"))
        assertEquals(
            "commanded_session_content_changed",
            baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, title = "new"), "commanded"),
        )
    }

    @Test fun `playback state before metadata confirms only after metadata changes`() {
        val baseline = baseline(session("commanded", MediaBaselinePlaybackState.PLAYING, title = "old"))
        assertNull(baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, title = "old"), "commanded"))
        assertEquals(
            "commanded_session_content_changed",
            baseline.confirm(observed("commanded", MediaBaselinePlaybackState.PLAYING, title = "new"), "commanded"),
        )
    }

    @Test fun `no observation before a dispatched operation can confirm playback`() {
        val baseline = baseline()
        assertNull(
            baseline.confirmDirectPlayback(
                observed("new", MediaBaselinePlaybackState.PLAYING),
                targetPackage = "target.player",
                commandedSessionId = null,
                commandDispatched = false,
                commandedSessionStillPresent = false,
            ),
        )
    }

    private fun MediaSessionBaseline.confirm(
        observed: MediaObservedSession,
        commandedSessionId: String?,
        commandedStillPresent: Boolean = commandedSessionId != null,
    ) = confirmDirectPlayback(
        observed = observed,
        targetPackage = "target.player",
        commandedSessionId = commandedSessionId,
        commandDispatched = true,
        commandedSessionStillPresent = commandedStillPresent,
    )

    private fun baseline(vararg sessions: MediaBaselineSession) =
        MediaSessionBaseline(
            playingSessionIds =
                sessions.filter { it.playbackState == MediaBaselinePlaybackState.PLAYING }.mapTo(
                    linkedSetOf(),
                ) { it.sessionId },
            knownSessionIds = sessions.mapTo(linkedSetOf()) { it.sessionId },
            sessions = sessions.toList(),
        )

    private fun session(
        id: String,
        state: MediaBaselinePlaybackState,
        mediaId: String? = null,
        queueItemId: Long? = null,
        title: String? = null,
    ) = MediaBaselineSession(id, state, MediaContentFingerprint(mediaId = mediaId, activeQueueItemId = queueItemId, title = title))

    private fun observed(
        id: String,
        state: MediaBaselinePlaybackState,
        packageName: String = "target.player",
        mediaId: String? = null,
        queueItemId: Long? = null,
        title: String? = null,
    ) = MediaObservedSession(
        id,
        packageName,
        state,
        MediaContentFingerprint(mediaId = mediaId, activeQueueItemId = queueItemId, title = title),
    )
}
