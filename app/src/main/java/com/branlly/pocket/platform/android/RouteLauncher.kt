package com.branlly.pocket.platform.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.TransportMode

object NavigationApps {
    const val GOOGLE_MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
    const val MAX_DESTINATION_LENGTH = 500

    val supportedPackages: Set<String> = setOf(GOOGLE_MAPS, WAZE)
}

sealed interface RouteLaunchResult {
    data object Launched : RouteLaunchResult
    data object MissingApplication : RouteLaunchResult
    data object MissingDestination : RouteLaunchResult
    data object RuntimeValueRequired : RouteLaunchResult
    data object UnsupportedApplication : RouteLaunchResult
    data object RejectedBySystem : RouteLaunchResult
}

/** Adaptateur Android isolé : le domaine ne dépend d'aucune API Android. */
class RouteLauncher(private val context: Context) {
    fun launch(action: ShortcutAction.OpenRoute): RouteLaunchResult {
        val packageName = (action.navigationPackage as? InputValue.Fixed<String>)?.value
            ?: return RouteLaunchResult.RuntimeValueRequired
        val rawDestination = (action.destination as? InputValue.Fixed<String>)?.value
            ?: return RouteLaunchResult.RuntimeValueRequired
        val destination = rawDestination.trim()
        if (destination.isEmpty() || destination.length > NavigationApps.MAX_DESTINATION_LENGTH) {
            return RouteLaunchResult.MissingDestination
        }
        if (packageName !in NavigationApps.supportedPackages) {
            return RouteLaunchResult.UnsupportedApplication
        }
        if (!isInstalled(packageName)) return RouteLaunchResult.MissingApplication

        val uri = when (packageName) {
            NavigationApps.GOOGLE_MAPS -> googleMapsUri(destination, action.transportMode)
            NavigationApps.WAZE -> wazeUri(destination)
            else -> return RouteLaunchResult.UnsupportedApplication
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            RouteLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            RouteLaunchResult.MissingApplication
        } catch (_: SecurityException) {
            RouteLaunchResult.RejectedBySystem
        }
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.applicationInfo(packageName).enabled
    }.getOrDefault(false)

    private fun googleMapsUri(destination: String, mode: TransportMode): Uri = Uri.Builder()
        .scheme("https")
        .authority("www.google.com")
        .appendPath("maps")
        .appendPath("dir")
        .appendPath("")
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("travelmode", mode.googleMapsValue())
        .build()

    @Suppress("DEPRECATION")
    private fun PackageManager.applicationInfo(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            getApplicationInfo(packageName, 0)
        }

    private fun wazeUri(destination: String): Uri = Uri.Builder()
        .scheme("https")
        .authority("waze.com")
        .appendPath("ul")
        .appendQueryParameter("q", destination)
        .appendQueryParameter("navigate", "yes")
        .build()
}

private fun TransportMode.googleMapsValue(): String = when (this) {
    TransportMode.DRIVING -> "driving"
    TransportMode.WALKING -> "walking"
    TransportMode.BICYCLING -> "bicycling"
    TransportMode.TRANSIT -> "transit"
}
