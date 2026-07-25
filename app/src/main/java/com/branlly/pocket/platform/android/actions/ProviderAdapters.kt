package com.branlly.pocket.platform.android.actions

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.branlly.pocket.domain.model.TransportMode

data class AppTarget(
    val packageName: String,
    val activityName: String? = null,
)

data class MediaOpenRequest(
    val target: AppTarget,
    val searchQuery: String? = null,
    val mediaUri: String? = null,
)

interface MediaAppAdapter {
    fun supports(target: AppTarget): Boolean

    fun buildOpenIntent(request: MediaOpenRequest): Intent?
}

enum class ProviderVerificationStatus { TESTED, EXPERIMENTAL, UNVERIFIED }

interface MediaProviderAdapter {
    val id: String
    val verificationStatus: ProviderVerificationStatus
    val capabilities: Set<com.branlly.pocket.domain.media.MediaProviderCapability>

    fun supports(target: AppTarget): Boolean
    fun buildDirectContentIntent(request: MediaOpenRequest): Intent?
    fun buildSearchIntent(request: MediaOpenRequest): Intent?
}

class YouTubeMusicAdapter(
    private val packages: Set<String> =
        setOf(
            "com.google.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music",
        ),
) : MediaAppAdapter {
    override fun supports(target: AppTarget): Boolean = target.packageName in packages

    override fun buildOpenIntent(request: MediaOpenRequest): Intent? =
        request.searchQuery?.takeIf { request.mediaUri.isNullOrBlank() && it.isNotBlank() }?.let { query ->
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}"))
                .setPackage(request.target.packageName)
        }
}

class GenericMediaAppAdapter : MediaAppAdapter {
    override fun supports(target: AppTarget): Boolean = true

    override fun buildOpenIntent(request: MediaOpenRequest): Intent? {
        request.mediaUri?.takeIf(::isSafeHttps)?.let { uri ->
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(request.target.packageName)
        }
        request.searchQuery?.takeIf(String::isNotBlank)?.let { query ->
            return Intent(Intent.ACTION_SEARCH).setPackage(request.target.packageName).putExtra(SearchManager.QUERY, query)
        }
        request.target.activityName?.let { activity ->
            return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(request.target.packageName, activity)
        }
        return null
    }

    private fun isSafeHttps(raw: String): Boolean = runCatching {
        val uri = Uri.parse(raw)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

class GenericMediaProviderAdapter : MediaProviderAdapter {
    override val id = "generic"
    override val verificationStatus = ProviderVerificationStatus.TESTED
    override val capabilities = setOf(
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_APP,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_SEARCH,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_DIRECT_CONTENT,
    )
    override fun supports(target: AppTarget) = true
    override fun buildDirectContentIntent(request: MediaOpenRequest): Intent? =
        request.mediaUri?.takeIf(::safeMediaUri)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(request.target.packageName) }
    override fun buildSearchIntent(request: MediaOpenRequest): Intent? =
        request.searchQuery?.takeIf(String::isNotBlank)?.let {
            Intent(Intent.ACTION_SEARCH).setPackage(request.target.packageName).putExtra(SearchManager.QUERY, it)
        }
}

class YouTubeMusicProviderAdapter(
    private val packages: Set<String> = setOf("com.google.android.apps.youtube.music", "app.revanced.android.apps.youtube.music"),
) : MediaProviderAdapter {
    override val id = "youtube_music"
    override val verificationStatus = ProviderVerificationStatus.UNVERIFIED
    override val capabilities = setOf(
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_APP,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_SEARCH,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_DIRECT_CONTENT,
    )
    override fun supports(target: AppTarget) = target.packageName in packages
    override fun buildDirectContentIntent(request: MediaOpenRequest): Intent? =
        request.mediaUri?.takeIf(::safeMediaUri)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(request.target.packageName) }
    override fun buildSearchIntent(request: MediaOpenRequest): Intent? = request.searchQuery?.takeIf(String::isNotBlank)?.let { query ->
        Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")).setPackage(request.target.packageName)
    }
}

class YouTubeProviderAdapter(
    private val packages: Set<String> = setOf("com.google.android.youtube", "app.revanced.android.youtube"),
) : MediaProviderAdapter {
    override val id = "youtube"
    override val verificationStatus = ProviderVerificationStatus.UNVERIFIED
    override val capabilities = setOf(
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_APP,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_SEARCH,
        com.branlly.pocket.domain.media.MediaProviderCapability.CAN_OPEN_DIRECT_CONTENT,
    )
    override fun supports(target: AppTarget) = target.packageName in packages
    override fun buildDirectContentIntent(request: MediaOpenRequest): Intent? =
        request.mediaUri?.takeIf(::safeMediaUri)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(request.target.packageName) }
    override fun buildSearchIntent(request: MediaOpenRequest): Intent? = request.searchQuery?.takeIf(String::isNotBlank)?.let { query ->
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).setPackage(request.target.packageName)
    }
}

private fun safeMediaUri(raw: String): Boolean = runCatching {
    val uri = Uri.parse(raw)
    (uri.scheme.equals("https", true) || uri.scheme.equals("spotify", true)) && uri.userInfo == null
}.getOrDefault(false)

data class NavigationTarget(
    val packageName: String,
)

data class RouteRequest(
    val target: NavigationTarget,
    val destination: String,
    val transportMode: TransportMode,
)

interface NavigationProviderAdapter {
    fun supports(target: NavigationTarget): Boolean

    fun buildRouteIntent(request: RouteRequest): Intent?
}

data class NavigationProviderOption(
    val packageName: String,
    val label: String,
    val adapter: NavigationProviderAdapter,
    val supportedModes: Set<TransportMode>,
)

object BuiltInProviderCatalog {
    val mediaAdapters: List<MediaAppAdapter> = listOf(YouTubeMusicAdapter())
    val mediaProviderAdapters: List<MediaProviderAdapter> = listOf(
        YouTubeMusicProviderAdapter(),
        YouTubeProviderAdapter(),
        GenericMediaProviderAdapter(),
    )
    val navigationProviders: List<NavigationProviderOption> =
        listOf(
            NavigationProviderOption(WazeAdapter.PACKAGE, "Waze", WazeAdapter(), setOf(TransportMode.DRIVING)),
            NavigationProviderOption(GoogleMapsAdapter.PACKAGE, "Google Maps", GoogleMapsAdapter(), TransportMode.entries.toSet()),
        )

    val defaultNavigationPackage: String = GoogleMapsAdapter.PACKAGE
    val navigationAdapters: List<NavigationProviderAdapter> = navigationProviders.map(NavigationProviderOption::adapter) + GenericNavigationAdapter()
}

class WazeAdapter : NavigationProviderAdapter {
    override fun supports(target: NavigationTarget): Boolean = target.packageName == PACKAGE

    override fun buildRouteIntent(request: RouteRequest): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.Builder()
                .scheme("waze")
                .authority("ul")
                .appendQueryParameter("q", request.destination)
                .appendQueryParameter("navigate", "yes")
                .build(),
        ).setPackage(request.target.packageName)

    companion object {
        const val PACKAGE = "com.waze"
    }
}

class GenericNavigationAdapter : NavigationProviderAdapter {
    override fun supports(target: NavigationTarget): Boolean = true

    override fun buildRouteIntent(request: RouteRequest): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(request.destination)}"),
        ).setPackage(request.target.packageName)
}

class GoogleMapsAdapter : NavigationProviderAdapter {
    override fun supports(target: NavigationTarget): Boolean = target.packageName == PACKAGE

    override fun buildRouteIntent(request: RouteRequest): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .appendPath("maps")
                .appendPath("dir")
                .appendPath("")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", request.destination)
                .appendQueryParameter("travelmode", request.transportMode.googleMapsValue())
                .build(),
        ).setPackage(request.target.packageName)

    companion object {
        const val PACKAGE = "com.google.android.apps.maps"
    }
}

private fun TransportMode.googleMapsValue(): String = when (this) {
    TransportMode.DRIVING -> "driving"
    TransportMode.WALKING -> "walking"
    TransportMode.BICYCLING -> "bicycling"
    TransportMode.TRANSIT -> "transit"
}
