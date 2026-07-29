package com.branlly.pocket.platform.android.setup

import android.content.Context

class SetupStateStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun markIncomplete() {
        preferences.edit().putBoolean(KEY_COMPLETED, false).apply()
    }

    fun wasRuntimePermissionRequested(permission: String): Boolean = preferences.getBoolean(requestKey(permission), false)

    fun markRuntimePermissionRequested(permission: String) {
        preferences.edit().putBoolean(requestKey(permission), true).apply()
    }

    private fun requestKey(permission: String): String = "requested_${permission.substringAfterLast('.')}"

    private companion object {
        const val PREFERENCES = "initial_setup"
        const val KEY_COMPLETED = "completed"
    }
}
