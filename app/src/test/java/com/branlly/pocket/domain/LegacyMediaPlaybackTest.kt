package com.branlly.pocket.domain

import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.execution.ActionResult
import com.branlly.pocket.domain.execution.ExecutionLogger
import com.branlly.pocket.domain.media.ExactPackagePlaybackTracker
import com.branlly.pocket.domain.media.MediaPlaybackStatus
import com.branlly.pocket.domain.media.MediaSessionSnapshot
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.platform.android.MediaPlaybackWaiter
import com.branlly.pocket.platform.android.MediaWaitResult
import com.branlly.pocket.platform.android.actions.WaitForMediaPlaybackHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMediaPlaybackTest {
    @Test
    fun `legacy tracker accepts PLAYING only from the exact package`() {
        val tracker = ExactPackagePlaybackTracker("target.player")

        assertFalse(tracker.observe(listOf(MediaSessionSnapshot("1", "other.player", MediaPlaybackStatus.PLAYING))))
        assertFalse(tracker.observe(listOf(MediaSessionSnapshot("2", "target.player", MediaPlaybackStatus.PAUSED))))
        assertTrue(tracker.observe(listOf(MediaSessionSnapshot("3", "target.player", MediaPlaybackStatus.PLAYING))))
        assertTrue(tracker.observedTargetSession)
    }

    @Test
    fun `WAIT_FOR_MEDIA_PLAYBACK keeps using its legacy waiter`() =
        runBlocking {
            var observedPackage: String? = null
            var observedTimeout: Long? = null
            val waiter =
                MediaPlaybackWaiter { packageName, timeoutMs ->
                    observedPackage = packageName
                    observedTimeout = timeoutMs
                    MediaWaitResult.Playing
                }
            val action = ShortcutAction.WaitForMediaPlayback(InputValue.Fixed("target.player"), timeoutMillis = 12_000)

            val result = WaitForMediaPlaybackHandler(waiter).execute(action, context())

            assertEquals(ActionResult.Completed, result)
            assertEquals("target.player", observedPackage)
            assertEquals(12_000L, observedTimeout)
        }

    private fun context() =
        ActionExecutionContext(
            executionId = "execution",
            routineId = ShortcutId.new(),
            nodeId = NodeId.new(),
            logger = ExecutionLogger { _, _ -> },
        )
}
