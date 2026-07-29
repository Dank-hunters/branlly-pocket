package com.branlly.pocket.platform.android

import com.branlly.pocket.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestNotificationContractTest {
    @Test
    fun `test actions are dismiss only and have no business side effect`() {
        TestNotificationAction.entries.forEach { action ->
            assertFalse(TestNotificationContract.hasBusinessSideEffect(action))
        }
    }

    @Test
    fun `test notification expires automatically`() {
        assertTrue(TestNotificationContract.TIMEOUT_MILLIS in 30_000L..120_000L)
    }

    @Test
    fun `important notifications use a dedicated small icon instead of launcher artwork`() {
        assertNotEquals(R.drawable.ic_launcher, BranllyNotifications.SMALL_ICON)
    }
}
