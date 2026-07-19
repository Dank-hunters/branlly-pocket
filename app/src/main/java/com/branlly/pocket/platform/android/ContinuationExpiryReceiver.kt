package com.branlly.pocket.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarm receiver used only to expire persisted user continuations; it never executes an action node. */
class ContinuationExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val identity = ContinuationIntentExtras.read(intent) ?: return
        RoutineOrchestrator.cancel(context.applicationContext, identity, expired = true)
    }
}
