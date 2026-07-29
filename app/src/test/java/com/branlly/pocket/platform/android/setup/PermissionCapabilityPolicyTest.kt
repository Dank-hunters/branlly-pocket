package com.branlly.pocket.platform.android.setup

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCapabilityPolicyTest {
    @Test
    fun `API 30 requests neither notification nor nearby runtime permissions`() {
        assertTrue(SetupCapabilityPolicy.runtimePermissions(30).isEmpty())
        assertFalse(SetupCapability.NEARBY_DEVICES in SetupCapabilityPolicy.requiredCapabilities(30))
    }

    @Test
    fun `API 30 detects disabled application notifications`() {
        assertFalse(
            SetupCapabilityPolicy.notificationsGranted(
                sdkInt = 30,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
            ),
        )
        assertTrue(
            SetupCapabilityPolicy.notificationsGranted(
                sdkInt = 30,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
            ),
        )
    }

    @Test
    fun `API 31 requires only Bluetooth connect for nearby devices`() {
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), SetupCapabilityPolicy.runtimePermissions(31))
        assertFalse(SetupCapabilityPolicy.nearbyDevicesGranted(31, connectPermissionGranted = false))
        assertTrue(SetupCapabilityPolicy.nearbyDevicesGranted(31, connectPermissionGranted = true))
    }

    @Test
    fun `API 33 adds post notifications to Bluetooth connect`() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT),
            SetupCapabilityPolicy.runtimePermissions(33),
        )
    }

    @Test
    fun `already granted capability never triggers another request`() {
        assertEquals(
            CapabilityRequestTarget.NONE,
            SetupCapabilityPolicy.requestTarget(SetupCapability.NOTIFICATIONS, 33, granted = true, runtimeRequestAlreadyMade = false),
        )
    }

    @Test
    fun `runtime refusal switches to settings instead of looping`() {
        assertEquals(
            CapabilityRequestTarget.RUNTIME_PERMISSION,
            SetupCapabilityPolicy.requestTarget(SetupCapability.NOTIFICATIONS, 33, granted = false, runtimeRequestAlreadyMade = false),
        )
        assertEquals(
            CapabilityRequestTarget.SETTINGS,
            SetupCapabilityPolicy.requestTarget(SetupCapability.NOTIFICATIONS, 33, granted = false, runtimeRequestAlreadyMade = true),
        )
    }

    @Test
    fun `notification listener always opens settings and refresh can observe authorization`() {
        assertEquals(
            CapabilityRequestTarget.SETTINGS,
            SetupCapabilityPolicy.requestTarget(SetupCapability.MEDIA_CONTROL, 30, granted = false, runtimeRequestAlreadyMade = false),
        )
        val absent = snapshot(notification = true, media = false)
        val authorizedAfterResume = snapshot(notification = true, media = true)
        assertEquals(SetupCapability.MEDIA_CONTROL, InitialSetupDecision.nextMissingCapability(absent))
        assertNull(InitialSetupDecision.nextMissingCapability(authorizedAfterResume))
    }

    @Test
    fun `incomplete setup is shown and completed setup remains hidden while permissions stay active`() {
        assertTrue(InitialSetupDecision.shouldShowAssistant(setupCompleted = false, limitedModeForCurrentLaunch = false))
        assertFalse(InitialSetupDecision.shouldShowAssistant(setupCompleted = true, limitedModeForCurrentLaunch = false))
        assertTrue(InitialSetupDecision.revokedCapabilities(true, snapshot(notification = true, media = true)).isEmpty())
    }

    @Test
    fun `settings entry reopens setup even after completion`() {
        assertTrue(
            InitialSetupDecision.shouldShowAssistant(
                setupCompleted = true,
                limitedModeForCurrentLaunch = false,
                openedFromSettings = true,
            ),
        )
    }

    @Test
    fun `limited continuation hides setup only for current launch`() {
        assertFalse(InitialSetupDecision.shouldShowAssistant(setupCompleted = false, limitedModeForCurrentLaunch = true))
        assertTrue(InitialSetupDecision.shouldShowAssistant(setupCompleted = false, limitedModeForCurrentLaunch = false))
    }

    @Test
    fun `later revocation is reported without reopening the full setup automatically`() {
        val revoked = InitialSetupDecision.revokedCapabilities(true, snapshot(notification = false, media = true))
        assertEquals(listOf(SetupCapability.NOTIFICATIONS), revoked)
        assertFalse(InitialSetupDecision.shouldShowAssistant(setupCompleted = true, limitedModeForCurrentLaunch = false))
    }

    private fun snapshot(
        notification: Boolean,
        media: Boolean,
    ): SetupSnapshot =
        SetupSnapshot(
            statuses =
                listOf(
                    SetupCapabilityStatus(SetupCapability.NOTIFICATIONS, notification),
                    SetupCapabilityStatus(SetupCapability.MEDIA_CONTROL, media),
                ),
            mediaListenerOperational = media,
        )
}
