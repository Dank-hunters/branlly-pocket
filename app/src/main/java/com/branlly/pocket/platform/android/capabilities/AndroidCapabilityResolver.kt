package com.branlly.pocket.platform.android.capabilities

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.branlly.pocket.platform.android.BranllyMediaListener

interface AndroidPlatformInfo {
    val sdkInt: Int
}

object RuntimeAndroidPlatformInfo : AndroidPlatformInfo {
    override val sdkInt: Int = Build.VERSION.SDK_INT
}

enum class AndroidCapability {
    APP_NOTIFICATIONS,
    MEDIA_PLAYBACK_OBSERVATION,
    BLUETOOTH_CONTROL,
    WRITE_SYSTEM_SETTINGS,
    NOTIFICATION_POLICY_CONTROL,
    MICROPHONE_INPUT,
}

enum class CapabilityStatus {
    Granted,
    MissingRuntimePermission,
    MissingSpecialAccess,
    DisabledByUser,
    Unsupported,
    TemporarilyUnavailable,
}

sealed interface CapabilityResolution {
    data object None : CapabilityResolution

    data class RuntimePermissions(
        val permissions: List<String>,
    ) : CapabilityResolution

    data class Settings(
        val primary: Intent,
        val fallback: Intent,
    ) : CapabilityResolution

    data object Unsupported : CapabilityResolution
}

data class CapabilitySnapshot(
    val capability: AndroidCapability,
    val status: CapabilityStatus,
    val resolution: CapabilityResolution,
    val explanation: String,
    val runtimePermissions: List<String> = emptyList(),
) {
    val granted: Boolean get() = status == CapabilityStatus.Granted
}

/** Immutable SDK policy shared by setup and action runtime checks. */
object AndroidCapabilityPolicy {
    fun bluetoothRuntimePermissions(platform: AndroidPlatformInfo): List<String> =
        if (platform.sdkInt >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    fun notificationRuntimePermissions(platform: AndroidPlatformInfo): List<String> =
        if (platform.sdkInt >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
}

class AndroidCapabilityResolver(
    private val context: Context,
    private val platform: AndroidPlatformInfo = RuntimeAndroidPlatformInfo,
) {
    fun resolve(capability: AndroidCapability): CapabilitySnapshot =
        when (capability) {
            AndroidCapability.BLUETOOTH_CONTROL -> bluetooth()
            AndroidCapability.APP_NOTIFICATIONS -> notifications()
            AndroidCapability.MEDIA_PLAYBACK_OBSERVATION -> mediaObservation()
            AndroidCapability.WRITE_SYSTEM_SETTINGS -> writeSettings()
            AndroidCapability.NOTIFICATION_POLICY_CONTROL -> notificationPolicy()
            AndroidCapability.MICROPHONE_INPUT -> microphone()
        }

    private fun bluetooth(): CapabilitySnapshot {
        val adapter =
            context.getSystemService(BluetoothManager::class.java).adapter
                ?: return CapabilitySnapshot(
                    AndroidCapability.BLUETOOTH_CONTROL,
                    CapabilityStatus.Unsupported,
                    CapabilityResolution.Unsupported,
                    "Cet appareil ne possède pas d’adaptateur Bluetooth.",
                )
        val permissions = AndroidCapabilityPolicy.bluetoothRuntimePermissions(platform)
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            return CapabilitySnapshot(
                AndroidCapability.BLUETOOTH_CONTROL,
                CapabilityStatus.MissingRuntimePermission,
                CapabilityResolution.RuntimePermissions(permissions),
                "Autorisez le contrôle Bluetooth pour Branlly Pocket.",
                permissions,
            )
        }
        return CapabilitySnapshot(
            AndroidCapability.BLUETOOTH_CONTROL,
            CapabilityStatus.Granted,
            CapabilityResolution.None,
            if (runCatching {
                    adapter.isEnabled
                }.getOrDefault(false)
            ) {
                "Bluetooth est activé."
            } else {
                "Bluetooth peut être activé au moment de la routine."
            },
        )
    }

    private fun notifications(): CapabilitySnapshot {
        val permissions = AndroidCapabilityPolicy.notificationRuntimePermissions(platform)
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            return CapabilitySnapshot(
                AndroidCapability.APP_NOTIFICATIONS,
                CapabilityStatus.MissingRuntimePermission,
                CapabilityResolution.RuntimePermissions(permissions),
                "Autorisez les notifications de Branlly Pocket.",
                permissions,
            )
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return CapabilitySnapshot(
                AndroidCapability.APP_NOTIFICATIONS,
                CapabilityStatus.DisabledByUser,
                AndroidSettingsNavigator.appNotifications(context),
                "Les notifications de Branlly Pocket sont désactivées.",
            )
        }
        return CapabilitySnapshot(
            AndroidCapability.APP_NOTIFICATIONS,
            CapabilityStatus.Granted,
            CapabilityResolution.None,
            "Notifications disponibles.",
        )
    }

    private fun mediaObservation(): CapabilitySnapshot {
        val enabled = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        return CapabilitySnapshot(
            AndroidCapability.MEDIA_PLAYBACK_OBSERVATION,
            if (enabled) CapabilityStatus.Granted else CapabilityStatus.MissingSpecialAccess,
            if (enabled) CapabilityResolution.None else AndroidSettingsNavigator.notificationListener(context),
            "Accès au service de notifications pour observer la lecture média.",
        )
    }

    private fun writeSettings(): CapabilitySnapshot =
        if (Settings.System.canWrite(context)) {
            CapabilitySnapshot(
                AndroidCapability.WRITE_SYSTEM_SETTINGS,
                CapabilityStatus.Granted,
                CapabilityResolution.None,
                "Modification des réglages système disponible.",
            )
        } else {
            CapabilitySnapshot(
                AndroidCapability.WRITE_SYSTEM_SETTINGS,
                CapabilityStatus.MissingSpecialAccess,
                AndroidSettingsNavigator.writeSettings(context),
                "Autorisez la modification des réglages système.",
            )
        }

    private fun notificationPolicy(): CapabilitySnapshot {
        val granted = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        return CapabilitySnapshot(
            AndroidCapability.NOTIFICATION_POLICY_CONTROL,
            if (granted) CapabilityStatus.Granted else CapabilityStatus.MissingSpecialAccess,
            if (granted) CapabilityResolution.None else AndroidSettingsNavigator.notificationPolicy(context),
            "Autorisez l’accès au mode Ne pas déranger.",
        )
    }

    private fun microphone(): CapabilitySnapshot {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return CapabilitySnapshot(
            AndroidCapability.MICROPHONE_INPUT,
            if (granted) CapabilityStatus.Granted else CapabilityStatus.MissingRuntimePermission,
            if (granted) CapabilityResolution.None else CapabilityResolution.RuntimePermissions(listOf(Manifest.permission.RECORD_AUDIO)),
            "Autorisez le microphone pour la commande vocale.",
            if (granted) emptyList() else listOf(Manifest.permission.RECORD_AUDIO),
        )
    }
}

object AndroidSettingsNavigator {
    fun appNotifications(context: Context): CapabilityResolution.Settings =
        settings(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            appDetails(context),
        )

    fun notificationListener(context: Context): CapabilityResolution.Settings {
        val primary =
            if (RuntimeAndroidPlatformInfo.sdkInt >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, BranllyMediaListener::class.java).flattenToString(),
                    )
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        return settings(primary, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun writeSettings(context: Context): CapabilityResolution.Settings =
        settings(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:${context.packageName}")),
            appDetails(context),
        )

    fun notificationPolicy(context: Context): CapabilityResolution.Settings =
        settings(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            appDetails(context),
        )

    fun open(
        context: Context,
        resolution: CapabilityResolution.Settings,
    ): Boolean {
        val primary = resolution.primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = resolution.fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(primary) }.recoverCatching { context.startActivity(fallback) }.isSuccess
    }

    private fun settings(
        primary: Intent,
        fallback: Intent,
    ): CapabilityResolution.Settings = CapabilityResolution.Settings(primary, fallback)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
}
