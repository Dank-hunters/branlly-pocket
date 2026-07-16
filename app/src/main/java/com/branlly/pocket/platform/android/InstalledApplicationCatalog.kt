package com.branlly.pocket.platform.android

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build

private const val MAX_APPLICATION_COUNT = 500
private const val MAX_LABEL_LENGTH = 80

data class InstalledApplication(
    val label: String,
    val packageName: String,
)

/** Ne voit que les activités de lancement déclarées dans <queries>, jamais tous les paquets. */
class InstalledApplicationCatalog(
    private val context: Context,
) {
    fun load(): List<InstalledApplication> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryLaunchers(intent)
            .asSequence()
            .filter { it.activityInfo?.exported == true }
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label =
                    info
                        .loadLabel(packageManager)
                        ?.toString()
                        ?.trim()
                        ?.take(MAX_LABEL_LENGTH)
                        ?.takeIf(String::isNotEmpty)
                        ?: packageName
                InstalledApplication(label, packageName)
            }.distinctBy(InstalledApplication::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApplication::label))
            .take(MAX_APPLICATION_COUNT)
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.queryLaunchers(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            queryIntentActivities(intent, 0)
        }
}

sealed interface ApplicationLaunchResult {
    data object Launched : ApplicationLaunchResult

    data object RuntimeValueRequired : ApplicationLaunchResult

    data object InvalidPackage : ApplicationLaunchResult

    data object MissingApplication : ApplicationLaunchResult

    data object RejectedBySystem : ApplicationLaunchResult
}

class ApplicationLauncher(
    private val context: Context,
) {
    fun launch(
        packageName: String?,
        searchQuery: String? = null,
        mediaUri: String? = null,
    ): ApplicationLaunchResult {
        if (packageName == null) return ApplicationLaunchResult.RuntimeValueRequired
        if (!PACKAGE_NAME.matches(packageName)) return ApplicationLaunchResult.InvalidPackage
        val directUri = mediaUri?.trim()?.take(MAX_MEDIA_URI_LENGTH)?.takeIf(::isSafeMediaUri)
        val directIntent =
            directUri?.let {
                Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val query = searchQuery?.trim()?.take(MAX_SEARCH_QUERY_LENGTH).orEmpty()
        val searchIntent =
            query.takeIf(String::isNotBlank)?.let {
                Intent(Intent.ACTION_SEARCH)
                    .setPackage(packageName)
                    .putExtra(SearchManager.QUERY, it)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val youtubeMusicSearchIntent =
            query
                .takeIf(String::isNotBlank)
                ?.takeIf { packageName.endsWith("youtube.music") }
                ?.let {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(it)}"))
                        .setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        val intent =
            directIntent?.takeIf { it.resolveActivity(context.packageManager) != null }
                ?: youtubeMusicSearchIntent?.takeIf { it.resolveActivity(context.packageManager) != null }
                ?: searchIntent?.takeIf { it.resolveActivity(context.packageManager) != null }
                ?: context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return ApplicationLaunchResult.MissingApplication
        return try {
            context.startActivity(intent)
            ApplicationLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            ApplicationLaunchResult.MissingApplication
        } catch (_: SecurityException) {
            ApplicationLaunchResult.RejectedBySystem
        }
    }

    companion object {
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private const val MAX_SEARCH_QUERY_LENGTH = 120
        private const val MAX_MEDIA_URI_LENGTH = 2_000

        private fun isSafeMediaUri(raw: String): Boolean =
            runCatching {
                val uri = Uri.parse(raw)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
            }.getOrDefault(false)
    }
}
