package com.branlly.pocket.platform.android.actions

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMediaSessionCommandPolicyTest {
    @Test fun `search selects only a compatible session from the exact target package`() {
        val selection =
            selectDirectMediaSession(
                candidates =
                    listOf(
                        candidate(0, "other.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, playing = true),
                        candidate(1, "target.player", PlaybackState.ACTION_PLAY),
                        candidate(2, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH),
                    ),
                targetPackage = "target.player",
                hasUri = false,
                searchQuery = "query",
            )

        assertEquals(MediaSessionSelection(2, DirectMediaSessionCommand.PLAY_FROM_SEARCH), selection)
    }

    @Test fun `search command is dispatched only to the selected exact package transport`() {
        val other = RecordingTransport()
        val target = RecordingTransport()
        val transports = listOf(other, target)
        val selection =
            requireNotNull(
                selectDirectMediaSession(
                    candidates =
                        listOf(
                            candidate(0, "other.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, playing = true),
                            candidate(1, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH),
                        ),
                    targetPackage = "target.player",
                    hasUri = false,
                    searchQuery = "requested song",
                ),
            )

        dispatchDirectMediaCommand(selection.command, null, "requested song", transports[selection.index])

        assertTrue(other.calls.isEmpty())
        assertEquals(listOf("playFromSearch:requested song"), target.calls)
    }

    @Test fun `search never degrades to generic play`() {
        assertNull(selectDirectCommand(PlaybackState.ACTION_PLAY, hasUri = false, searchQuery = "query"))
        val transport = RecordingTransport()
        dispatchDirectMediaCommand(DirectMediaSessionCommand.PLAY_FROM_SEARCH, null, "query", transport)
        assertEquals(listOf("playFromSearch:query"), transport.calls)
    }

    @Test fun `prepare from search requires prepare and play capabilities`() {
        assertNull(selectDirectCommand(PlaybackState.ACTION_PREPARE_FROM_SEARCH, hasUri = false, searchQuery = "query"))
        assertEquals(
            DirectMediaSessionCommand.PREPARE_FROM_SEARCH_AND_PLAY,
            selectDirectCommand(
                PlaybackState.ACTION_PREPARE_FROM_SEARCH or PlaybackState.ACTION_PLAY,
                hasUri = false,
                searchQuery = "query",
            ),
        )
    }

    @Test fun `an already playing compatible target session is preferred`() {
        val selection =
            selectDirectMediaSession(
                candidates =
                    listOf(
                        candidate(0, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH),
                        candidate(1, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, playing = true),
                    ),
                targetPackage = "target.player",
                hasUri = false,
                searchQuery = "query",
            )

        assertEquals(1, selection?.index)
    }

    @Test fun `notification candidates reject another package and choose latest compatible stable candidate`() {
        val selection =
            selectNotificationMediaSession(
                candidates =
                    listOf(
                        notificationCandidate(0, "other.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, true, 999, "other"),
                        notificationCandidate(1, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, false, 10, "z"),
                        notificationCandidate(2, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, false, 20, "b"),
                        notificationCandidate(3, "target.player", PlaybackState.ACTION_PLAY_FROM_SEARCH, false, 20, "a"),
                    ),
                targetPackage = "target.player",
                hasUri = false,
                searchQuery = "query",
            )

        assertEquals(MediaSessionSelection(3, DirectMediaSessionCommand.PLAY_FROM_SEARCH), selection)
    }

    @Test fun `notification candidate without a compatible command is never selected`() {
        assertNull(
            selectNotificationMediaSession(
                candidates = listOf(notificationCandidate(0, "target.player", PlaybackState.ACTION_PLAY, false, 1, "n")),
                targetPackage = "target.player",
                hasUri = false,
                searchQuery = "query",
            ),
        )
    }

    private class RecordingTransport : DirectMediaTransport {
        val calls = mutableListOf<String>()

        override fun playFromUri(uri: String) {
            calls += "playFromUri:$uri"
        }

        override fun playFromSearch(query: String) {
            calls += "playFromSearch:$query"
        }

        override fun prepareFromSearch(query: String) {
            calls += "prepareFromSearch:$query"
        }

        override fun play() {
            calls += "play"
        }
    }

    private fun notificationCandidate(
        index: Int,
        packageName: String,
        actions: Long,
        playing: Boolean,
        postedAtMillis: Long,
        stableIdentity: String,
    ) = NotificationMediaSessionSelectionCandidate(index, packageName, actions, playing, postedAtMillis, stableIdentity)

    private fun candidate(
        index: Int,
        packageName: String,
        actions: Long,
        playing: Boolean = false,
    ) = MediaSessionCandidate(index, packageName, actions, playing)
}
