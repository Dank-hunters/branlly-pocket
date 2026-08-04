package com.branlly.pocket.platform.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Permission anchor and process-local availability signal for MediaSessionManager access. */
class BranllyMediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        listener.set(this)
        connected.set(true)
    }

    override fun onListenerDisconnected() {
        connected.set(false)
        listener.compareAndSet(this, null)
    }

    override fun onDestroy() {
        connected.set(false)
        listener.compareAndSet(this, null)
        super.onDestroy()
    }

    companion object {
        private val connected = AtomicBoolean(false)
        private val listener = AtomicReference<BranllyMediaListener?>(null)

        fun isConnected(): Boolean = connected.get()

        /**
         * Android-only snapshot used by media adapters. The framework notification objects never
         * cross into the domain or coordinator, and an unavailable listener is explicit.
         */
        fun activeNotificationsSnapshot(): List<StatusBarNotification>? =
            listener.get()?.let { service ->
                runCatching { service.activeNotifications.orEmpty().toList() }.getOrNull()
            }
    }
}
