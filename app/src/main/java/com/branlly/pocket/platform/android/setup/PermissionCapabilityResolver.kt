package com.branlly.pocket.platform.android.setup

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.branlly.pocket.platform.android.BranllyMediaListener

enum class SetupCapability {
    NOTIFICATIONS,
    MEDIA_CONTROL,
    NEARBY_DEVICES,
}

enum class CapabilityRequestTarget {
    NONE,
    RUNTIME_PERMISSION,
    SETTINGS,
}

data class SetupCapabilityStatus(
    val capability: SetupCapability,
    val granted: Boolean,
)

data class SetupSnapshot(
    val statuses: List<SetupCapabilityStatus>,
    val mediaListenerOperational: Boolean,
) {
    val missing: List<SetupCapabilityStatus>
        get() = statuses.filterNot(SetupCapabilityStatus::granted)

    val allRequiredGranted: Boolean
        get() = missing.isEmpty()

    fun status(capability: SetupCapability): SetupCapabilityStatus? = statuses.firstOrNull { it.capability == capability }
}

/** Pure SDK policy kept separate from Android status resolution so version rules remain testable. */
object SetupCapabilityPolicy {
    fun requiredCapabilities(sdkInt: Int): List<SetupCapability> =
        buildList {
            add(SetupCapability.NOTIFICATIONS)
            add(SetupCapability.MEDIA_CONTROL)
            if (sdkInt >= Build.VERSION_CODES.S) add(SetupCapability.NEARBY_DEVICES)
        }

    fun runtimePermissions(sdkInt: Int): List<String> =
        buildList {
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (sdkInt >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }

    fun notificationsGranted(
        sdkInt: Int,
        runtimePermissionGranted: Boolean,
        notificationsEnabled: Boolean,
    ): Boolean = (sdkInt < Build.VERSION_CODES.TIRAMISU || runtimePermissionGranted) && notificationsEnabled

    fun nearbyDevicesGranted(
        sdkInt: Int,
        connectPermissionGranted: Boolean,
    ): Boolean = sdkInt < Build.VERSION_CODES.S || connectPermissionGranted

    fun runtimePermission(
        capability: SetupCapability,
        sdkInt: Int,
    ): String? =
        when (capability) {
            SetupCapability.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS.takeIf { sdkInt >= Build.VERSION_CODES.TIRAMISU }
            SetupCapability.MEDIA_CONTROL -> null
            SetupCapability.NEARBY_DEVICES -> Manifest.permission.BLUETOOTH_CONNECT.takeIf { sdkInt >= Build.VERSION_CODES.S }
        }

    fun requestTarget(
        capability: SetupCapability,
        sdkInt: Int,
        granted: Boolean,
        runtimeRequestAlreadyMade: Boolean,
    ): CapabilityRequestTarget {
        if (granted || capability !in requiredCapabilities(sdkInt)) return CapabilityRequestTarget.NONE
        val runtimePermission = runtimePermission(capability, sdkInt)
        return if (runtimePermission != null && !runtimeRequestAlreadyMade) {
            CapabilityRequestTarget.RUNTIME_PERMISSION
        } else {
            CapabilityRequestTarget.SETTINGS
        }
    }
}

object InitialSetupDecision {
    fun shouldShowAssistant(
        setupCompleted: Boolean,
        limitedModeForCurrentLaunch: Boolean,
        openedFromSettings: Boolean = false,
    ): Boolean = openedFromSettings || (!setupCompleted && !limitedModeForCurrentLaunch)

    fun revokedCapabilities(
        setupCompleted: Boolean,
        snapshot: SetupSnapshot,
    ): List<SetupCapability> = if (setupCompleted) snapshot.missing.map(SetupCapabilityStatus::capability) else emptyList()

    fun nextMissingCapability(snapshot: SetupSnapshot): SetupCapability? = snapshot.missing.firstOrNull()?.capability
}

class PermissionCapabilityResolver(
    private val context: Context,
) {
    fun resolve(): SetupSnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        val statuses =
            SetupCapabilityPolicy.requiredCapabilities(sdkInt).map { capability ->
                SetupCapabilityStatus(capability, isGranted(capability, sdkInt))
            }
        return SetupSnapshot(
            statuses = statuses,
            mediaListenerOperational = BranllyMediaListener.isConnected(),
        )
    }

    private fun isGranted(
        capability: SetupCapability,
        sdkInt: Int,
    ): Boolean =
        when (capability) {
            SetupCapability.NOTIFICATIONS -> {
                val runtimeGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                SetupCapabilityPolicy.notificationsGranted(
                    sdkInt = sdkInt,
                    runtimePermissionGranted = runtimeGranted,
                    notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                )
            }

            SetupCapability.MEDIA_CONTROL -> {
                context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            }

            SetupCapability.NEARBY_DEVICES -> {
                SetupCapabilityPolicy.nearbyDevicesGranted(
                    sdkInt = sdkInt,
                    connectPermissionGranted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                            PackageManager.PERMISSION_GRANTED,
                )
            }
        }
}

object PermissionSettingsIntents {
    fun appNotifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:${context.packageName}"))

    fun notificationListener(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = ComponentName(context, BranllyMediaListener::class.java)
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        }
        return notificationListenerList()
    }

    fun notificationListenerList(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun open(
        context: Context,
        primary: Intent,
        fallback: Intent = appDetails(context),
    ) {
        runCatching { context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
