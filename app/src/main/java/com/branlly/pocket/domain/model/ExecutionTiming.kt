package com.branlly.pocket.domain.model

/** Temps de stabilisation local avant d'ouvrir une seconde application externe. */
object ExecutionTiming {
    const val EXTERNAL_LAUNCH_SETTLE_MILLIS = 5_000L

    fun automaticDelayAfter(
        current: ShortcutAction,
        next: ShortcutAction?,
    ): Long =
        when {
            next == null || next is ShortcutAction.Wait -> 0L
            current.isExternalLaunch() && next.isExternalLaunch() -> EXTERNAL_LAUNCH_SETTLE_MILLIS
            else -> 0L
        }
}

private fun ShortcutAction.isExternalLaunch(): Boolean = this is ShortcutAction.OpenApplication || this is ShortcutAction.OpenRoute
