package com.branlly.pocket.platform.android

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process

private const val MAX_APPLICATION_COUNT = 500
private const val MAX_LABEL_LENGTH = 80

data class InstalledApplication(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
)

/** Lists launchable installed applications; execution is delegated to action handlers. */
class InstalledApplicationCatalog(
    private val context: Context,
) {
    fun load(): List<InstalledApplication> {
        val packageManager = context.packageManager
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .map { info ->
                val label = info.label?.toString()?.trim()?.take(MAX_LABEL_LENGTH)?.takeIf(String::isNotEmpty)
                    ?: info.applicationInfo.packageName
                InstalledApplication(label, info.applicationInfo.packageName, info.componentName.className, info.getIcon(0))
            }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = queryActivities(packageManager, intent).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim()?.take(MAX_LABEL_LENGTH)?.takeIf(String::isNotEmpty)
                ?: activity.packageName
            InstalledApplication(label, activity.packageName, activity.name, info.loadIcon(packageManager))
        }
        val installed = installedApplications(packageManager).mapNotNull { info ->
            val launch = packageManager.getLaunchIntentForPackage(info.packageName)?.component
                ?: queryActivities(
                    packageManager,
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(info.packageName),
                    matchAll = true,
                ).firstOrNull()?.activityInfo?.let { android.content.ComponentName(it.packageName, it.name) }
                ?: return@mapNotNull null
            val label = packageManager.getApplicationLabel(info).toString().trim().take(MAX_LABEL_LENGTH).ifBlank { info.packageName }
            InstalledApplication(label, info.packageName, launch.className, packageManager.getApplicationIcon(info))
        }
        return (launcherApps + resolved + installed)
            .distinctBy { it.packageName to it.activityName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApplication::label).thenBy(InstalledApplication::packageName))
            .take(MAX_APPLICATION_COUNT)
    }

    private fun queryActivities(
        packageManager: PackageManager,
        intent: Intent,
        matchAll: Boolean = false,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(if (matchAll) PackageManager.MATCH_ALL.toLong() else 0L),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, if (matchAll) PackageManager.MATCH_ALL else 0)
    }

    private fun installedApplications(packageManager: PackageManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
}
