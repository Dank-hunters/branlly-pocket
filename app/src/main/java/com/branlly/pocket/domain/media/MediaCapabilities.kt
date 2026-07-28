package com.branlly.pocket.domain.media

enum class MediaProviderCapability {
    CAN_OPEN_APP,
    CAN_OPEN_SEARCH,
    CAN_OPEN_DIRECT_CONTENT,
    CAN_REQUEST_PLAYBACK,
    CAN_AUTOMATE_RESULT_SELECTION,
    CAN_VERIFY_CONTENT,
}

data class MediaCapabilitySnapshot(
    val packageInstalled: Boolean,
    val packageLaunchable: Boolean,
    val exactActivityAvailable: Boolean,
    val directUriProvided: Boolean,
    val providerAdapterId: String?,
    val providerCapabilities: Set<MediaProviderCapability>,
    val notificationListenerAuthorized: Boolean,
    val notificationListenerAvailable: Boolean,
    val exactPackageSessionCount: Int,
    val transportActions: Long,
    val manualFallbackAllowed: Boolean,
    val advancedAutomationAllowed: Boolean,
    val advancedAutomationAvailable: Boolean,
)
