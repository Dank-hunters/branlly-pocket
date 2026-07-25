package com.branlly.pocket.domain

import com.branlly.pocket.domain.media.MediaExecutionCheckpoint
import com.branlly.pocket.domain.media.MediaExecutionCheckpointCodec
import com.branlly.pocket.domain.media.MediaExecutionPlan
import com.branlly.pocket.domain.media.MediaExecutionState
import com.branlly.pocket.domain.media.MediaSessionBaseline
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaExecutionCheckpointCodecTest {
    @Test fun `round trip preserves identity and original deadlines`() {
        val checkpoint = checkpoint()
        val restored = MediaExecutionCheckpointCodec.decode(MediaExecutionCheckpointCodec.encode(checkpoint))
        assertEquals(checkpoint.executionId, restored?.executionId)
        assertEquals(checkpoint.routineId, restored?.routineId)
        assertEquals(checkpoint.nodeId, restored?.nodeId)
        assertEquals(checkpoint.startedAtMillis, restored?.startedAtMillis)
        assertEquals(checkpoint.automaticDeadlineMillis, restored?.automaticDeadlineMillis)
        assertEquals(checkpoint.globalDeadlineMillis, restored?.globalDeadlineMillis)
    }

    @Test fun `rejects blank identities and incoherent deadlines`() {
        val encoded = MediaExecutionCheckpointCodec.encode(checkpoint())
        listOf(
            encoded.replace("\"execution\"", "\"\""),
            encoded.replace("\"routine\"", "\"\""),
            encoded.replace("\"node\"", "\"\""),
            encoded.replace("\"globalDeadlineMillis\":30", "\"globalDeadlineMillis\":9"),
            encoded.replace("\"automaticDeadlineMillis\":20", "\"automaticDeadlineMillis\":31"),
        ).forEach { assertNull(MediaExecutionCheckpointCodec.decode(it)) }
    }

    private fun checkpoint() =
        MediaExecutionCheckpoint(
            executionId = "execution",
            routineId = ShortcutId("routine"),
            nodeId = NodeId("node"),
            startedAtMillis = 10,
            automaticDeadlineMillis = 20,
            globalDeadlineMillis = 30,
            state = MediaExecutionState.PRECHECK,
            stateVersion = 0,
            operationId = null,
            continuationConsumed = false,
            manualGuidanceShown = false,
            baseline = MediaSessionBaseline(emptySet(), emptySet()),
            plan = MediaExecutionPlan(emptyList()),
        )
}
