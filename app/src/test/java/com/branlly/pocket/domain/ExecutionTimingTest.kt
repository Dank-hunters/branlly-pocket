package com.branlly.pocket.domain

import com.branlly.pocket.domain.model.ExecutionTiming
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionTimingTest {
    @Test
    fun `waits between route and application`() {
        assertEquals(
            ExecutionTiming.EXTERNAL_LAUNCH_SETTLE_MILLIS,
            ExecutionTiming.automaticDelayAfter(
                ShortcutAction.OpenRoute(InputValue.Fixed("com.waze"), InputValue.Fixed("Paris")),
                ShortcutAction.OpenApplication(InputValue.Fixed("app.revanced.android.apps.youtube.music")),
            ),
        )
    }

    @Test
    fun `waits between application and route`() {
        assertEquals(
            ExecutionTiming.EXTERNAL_LAUNCH_SETTLE_MILLIS,
            ExecutionTiming.automaticDelayAfter(
                ShortcutAction.OpenApplication(InputValue.Fixed("app.revanced.android.apps.youtube.music")),
                ShortcutAction.OpenRoute(InputValue.Fixed("com.waze"), InputValue.Fixed("Paris")),
            ),
        )
    }

    @Test
    fun `does not add a second delay after explicit wait`() {
        assertEquals(
            0L,
            ExecutionTiming.automaticDelayAfter(
                ShortcutAction.OpenRoute(InputValue.Fixed("com.waze"), InputValue.Fixed("Paris")),
                ShortcutAction.Wait(5_000),
            ),
        )
    }
}
