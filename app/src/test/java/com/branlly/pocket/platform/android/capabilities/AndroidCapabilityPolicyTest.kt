package com.branlly.pocket.platform.android.capabilities

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCapabilityPolicyTest {
    @Test
    fun `API 30 Bluetooth control never requests a runtime permission`() {
        assertTrue(AndroidCapabilityPolicy.bluetoothRuntimePermissions(platform(Build.VERSION_CODES.R)).isEmpty())
    }

    @Test
    fun `API 31 Bluetooth control requires only connect`() {
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_CONNECT),
            AndroidCapabilityPolicy.bluetoothRuntimePermissions(platform(Build.VERSION_CODES.S)),
        )
    }

    @Test
    fun `API 30 notifications never request post notifications`() {
        assertTrue(AndroidCapabilityPolicy.notificationRuntimePermissions(platform(Build.VERSION_CODES.R)).isEmpty())
    }

    @Test
    fun `API 33 notifications request post notifications`() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            AndroidCapabilityPolicy.notificationRuntimePermissions(platform(Build.VERSION_CODES.TIRAMISU)),
        )
    }

    @Test
    fun `Bluetooth off is separate from the control permission policy`() {
        assertTrue(AndroidCapabilityPolicy.bluetoothRuntimePermissions(platform(Build.VERSION_CODES.R)).isEmpty())
    }

    private fun platform(sdkInt: Int): AndroidPlatformInfo =
        object : AndroidPlatformInfo {
            override val sdkInt: Int = sdkInt
        }
}
