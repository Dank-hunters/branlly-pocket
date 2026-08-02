package com.branlly.pocket.domain

import com.branlly.pocket.domain.media.MediaBaselineMetadataState
import com.branlly.pocket.domain.media.MediaBaselinePlaybackState
import com.branlly.pocket.domain.media.MediaBaselineSession
import com.branlly.pocket.domain.media.MediaContentFingerprint
import com.branlly.pocket.domain.media.MediaExecutionCheckpoint
import com.branlly.pocket.domain.media.MediaExecutionCheckpointCodec
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionState
import com.branlly.pocket.domain.media.MediaOperation
import com.branlly.pocket.domain.media.MediaOperationStatus
import com.branlly.pocket.domain.media.MediaOperationType
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaExecutionCheckpointCodecTest {
    @Test
    fun `round trip preserves identity deadlines and empty baseline`() {
        val checkpoint = checkpoint()
        val restored = roundTrip(checkpoint)
        assertEquals(checkpoint.executionId, restored?.executionId)
        assertEquals(checkpoint.routineId, restored?.routineId)
        assertEquals(checkpoint.nodeId, restored?.nodeId)
        assertEquals(checkpoint.startedAtMillis, restored?.startedAtMillis)
        assertEquals(checkpoint.automaticDeadlineMillis, restored?.automaticDeadlineMillis)
        assertEquals(checkpoint.globalDeadlineMillis, restored?.globalDeadlineMillis)
        assertEquals(checkpoint.baseline, restored?.baseline)
    }

    @Test
    fun `round trip preserves paused and playing baseline metadata`() {
        val paused =
            baseline(
                MediaBaselinePlaybackState.PAUSED,
                artist = null,
                album = null,
                uri = null,
                position = null,
                metadata = MediaBaselineMetadataState.PARTIAL,
            )
        val playing =
            baseline(
                MediaBaselinePlaybackState.PLAYING,
                artist = "Artist",
                album = "Album",
                uri = "https://media.example/item",
                position = 1_234,
                metadata = MediaBaselineMetadataState.COMPLETE,
            )
        assertEquals(paused, roundTrip(checkpoint(baseline = paused))?.baseline)
        assertEquals(playing, roundTrip(checkpoint(baseline = playing))?.baseline)
    }

    @Test
    fun `round trip preserves ordered plan operation state and flags`() {
        val operations =
            listOf(
                MediaOperation(
                    "direct",
                    MediaOperationType.DIRECT_URI,
                    true,
                    MediaOperationStatus.COMPLETED,
                    effectApplied = true,
                    executionCount = 1,
                ),
                MediaOperation(
                    "provider",
                    MediaOperationType.PROVIDER_SEARCH,
                    true,
                    MediaOperationStatus.AWAITING_OUTCOME,
                    effectApplied = true,
                    executionCount = 1,
                ),
                MediaOperation(
                    "manual",
                    MediaOperationType.MANUAL_ASSISTANCE,
                    false,
                    MediaOperationStatus.SKIPPED,
                    available = false,
                    reason = "not needed",
                ),
            )
        val checkpoint =
            checkpoint(
                state = MediaExecutionState.AWAIT_OUTCOME,
                stateVersion = 7,
                operationId = "provider",
                continuationCreated = true,
                continuationConsumed = true,
                continuationKey = "execution:node:provider:7",
                manualGuidanceShown = true,
                plan = MediaExecutionPlan(operations),
            )
        val restored = roundTrip(checkpoint)
        assertEquals(operations, restored?.plan?.operations)
        assertEquals("provider", restored?.operationId)
        assertEquals(7, restored?.stateVersion)
        assertEquals(true, restored?.continuationCreated)
        assertEquals(true, restored?.continuationConsumed)
        assertEquals(checkpoint.continuationKey, restored?.continuationKey)
        assertEquals(true, restored?.manualGuidanceShown)
    }

    @Test
    fun `older checkpoint without direct failure notice flag remains decodable`() {
        val encoded = JSONObject(MediaExecutionCheckpointCodec.encode(checkpoint())).apply { remove("directFailureNoticeShown") }

        assertEquals(false, MediaExecutionCheckpointCodec.decode(encoded.toString())?.directFailureNoticeShown)
    }

    @Test
    fun `round trip preserves per session baseline and commanded session identity`() {
        val baseline =
            baseline(MediaBaselinePlaybackState.PLAYING).copy(
                sessions =
                    listOf(
                        MediaBaselineSession(
                            "session",
                            MediaBaselinePlaybackState.PLAYING,
                            MediaContentFingerprint(mediaId = "old", activeQueueItemId = 1, title = "Old", durationMillis = 120_000),
                        ),
                    ),
            )
        val operation =
            MediaOperation(
                "media_session",
                MediaOperationType.MEDIA_SESSION,
                true,
                MediaOperationStatus.AWAITING_OUTCOME,
                effectApplied = true,
                executionCount = 1,
                commandedSessionId = "session",
            )
        val restored =
            roundTrip(
                checkpoint(
                    baseline = baseline,
                    state = MediaExecutionState.AWAIT_OUTCOME,
                    operationId = operation.id,
                    plan = MediaExecutionPlan(listOf(operation)),
                ),
            )

        assertEquals(baseline.sessions, restored?.baseline?.sessions)
        assertEquals(
            "session",
            restored
                ?.plan
                ?.operations
                ?.single()
                ?.commandedSessionId,
        )
    }

    @Test
    fun `older checkpoint without session baseline fields remains decodable`() {
        val encoded = JSONObject(MediaExecutionCheckpointCodec.encode(checkpoint(baseline = baseline(MediaBaselinePlaybackState.PLAYING))))
        encoded.getJSONObject("baseline").remove("sessions")
        encoded.getJSONArray("plan").let { operations ->
            repeat(operations.length()) { operations.getJSONObject(it).remove("commandedSessionId") }
        }

        val restored = MediaExecutionCheckpointCodec.decode(encoded.toString())

        assertEquals(listOf("session"), restored?.baseline?.sessions?.map { it.sessionId })
        assertEquals(
            null,
            restored
                ?.plan
                ?.operations
                ?.singleOrNull()
                ?.commandedSessionId,
        )
    }

    @Test
    fun `rejects blank identities incoherent deadlines and baseline`() {
        val encoded = MediaExecutionCheckpointCodec.encode(checkpoint())
        listOf(
            encoded.replace("\"execution\"", "\"\""),
            encoded.replace("\"routine\"", "\"\""),
            encoded.replace("\"node\"", "\"\""),
            encoded.replace("\"globalDeadlineMillis\":30", "\"globalDeadlineMillis\":9"),
            encoded.replace("\"automaticDeadlineMillis\":20", "\"automaticDeadlineMillis\":31"),
            MediaExecutionCheckpointCodec.encode(
                checkpoint(baseline = baseline(MediaBaselinePlaybackState.PLAYING).copy(packageName = "")),
            ),
            MediaExecutionCheckpointCodec.encode(checkpoint(baseline = MediaSessionBaseline(emptySet(), emptySet(), title = "unexpected"))),
        ).forEach { assertNull(MediaExecutionCheckpointCodec.decode(it)) }
    }

    @Test
    fun `rejects incoherent operation plans`() {
        val running = MediaOperation("one", MediaOperationType.PROVIDER_SEARCH, true, MediaOperationStatus.RUNNING, executionCount = 1)
        val invalid =
            listOf(
                checkpoint(plan = MediaExecutionPlan(listOf(running, running.copy()))),
                checkpoint(operationId = "missing", plan = MediaExecutionPlan(listOf(running))),
                checkpoint(operationId = "one", plan = MediaExecutionPlan(listOf(running, running.copy(id = "two")))),
                checkpoint(
                    plan =
                        MediaExecutionPlan(
                            listOf(running.copy(status = MediaOperationStatus.EFFECT_APPLIED, effectApplied = true, executionCount = 0)),
                        ),
                ),
                checkpoint(continuationCreated = false, continuationConsumed = true),
                checkpoint(state = MediaExecutionState.AWAIT_USER_LAUNCH, operationId = null),
            )
        invalid.forEach { assertNull(roundTrip(it)) }
    }

    private fun roundTrip(checkpoint: MediaExecutionCheckpoint) =
        MediaExecutionCheckpointCodec.decode(MediaExecutionCheckpointCodec.encode(checkpoint))

    private fun baseline(
        state: MediaBaselinePlaybackState,
        artist: String? = "Artist",
        album: String? = "Album",
        uri: String? = "https://media.example/item",
        position: Long? = 10,
        metadata: MediaBaselineMetadataState = MediaBaselineMetadataState.COMPLETE,
    ) = MediaSessionBaseline(
        playingSessionIds = if (state == MediaBaselinePlaybackState.PLAYING) setOf("session") else emptySet(),
        knownSessionIds = setOf("session"),
        sessionPresent = true,
        packageName = "player.example",
        playbackState = state,
        title = "Title",
        artist = artist,
        album = album,
        mediaUri = uri,
        sessionId = "session",
        positionMillis = position,
        capturedAtMillis = 100,
        metadataState = metadata,
    )

    private fun checkpoint(
        baseline: MediaSessionBaseline = MediaSessionBaseline(emptySet(), emptySet()),
        state: MediaExecutionState = MediaExecutionState.PRECHECK,
        stateVersion: Int = 0,
        operationId: String? = null,
        continuationCreated: Boolean = false,
        continuationConsumed: Boolean = false,
        continuationKey: String? = null,
        manualGuidanceShown: Boolean = false,
        plan: MediaExecutionPlan = MediaExecutionPlan(emptyList()),
    ) = MediaExecutionCheckpoint(
        executionId = "execution",
        routineId = ShortcutId("routine"),
        nodeId = NodeId("node"),
        startedAtMillis = 10,
        automaticDeadlineMillis = 20,
        globalDeadlineMillis = 30,
        state = state,
        stateVersion = stateVersion,
        operationId = operationId,
        continuationCreated = continuationCreated,
        continuationConsumed = continuationConsumed,
        continuationKey = continuationKey,
        manualGuidanceShown = manualGuidanceShown,
        baseline = baseline,
        plan = plan,
    )
}
