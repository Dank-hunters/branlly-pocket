package com.branlly.pocket.platform.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.model.ChargerEvent
import com.branlly.pocket.domain.model.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Suggestions only: the routine starts only after the user taps the notification. */
class ContextSuggestionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val event =
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> ChargerEvent.PLUGGED
                Intent.ACTION_POWER_DISCONNECTED -> ChargerEvent.UNPLUGGED
                else -> return
            }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SavedShortcutStore(context)
                    .shortcuts
                    .first()
                    .filter {
                        it.enabled &&
                            (it.trigger as? Trigger.Charger)?.event.let { configured ->
                                configured == event ||
                                    configured == ChargerEvent.BOTH
                            }
                    }.forEach { showSuggestion(context, it.id.value, it.name) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showSuggestion(
        context: Context,
        id: String,
        name: String,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = BranllyNotifications.ensureSuggestionChannel(context)
        val intent = Intent(context, PinnedRoutineReceiver::class.java).putExtra(PinnedRoutineShortcut.EXTRA_SHORTCUT_ID, id)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            id.hashCode(),
            BranllyNotifications
                .builder(context, channel)
                .setContentTitle("Routine suggérée")
                .setContentText("Chargeur détecté — lancer $name ?")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }
}
