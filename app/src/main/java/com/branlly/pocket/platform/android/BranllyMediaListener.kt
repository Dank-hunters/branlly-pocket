package com.branlly.pocket.platform.android

import android.service.notification.NotificationListenerService
import java.util.concurrent.atomic.AtomicBoolean

/** Permission anchor and process-local availability signal for MediaSessionManager access. */
class BranllyMediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        connected.set(true)
    }

    override fun onListenerDisconnected() {
        connected.set(false)
    }

    override fun onDestroy() {
        connected.set(false)
        super.onDestroy()
    }

    companion object {
        private val connected = AtomicBoolean(false)
        fun isConnected(): Boolean = connected.get()
    }
}
