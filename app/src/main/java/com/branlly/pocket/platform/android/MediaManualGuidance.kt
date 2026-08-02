package com.branlly.pocket.platform.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.branlly.pocket.domain.execution.ActionExecutionContext
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.platform.android.actions.ManualMediaGuidance

class AndroidManualMediaGuidance(
    context: Context,
) : ManualMediaGuidance {
    private val appContext = context.applicationContext
    private var notificationId: Int? = null

    override fun show(
        action: ShortcutAction.PlayMedia,
        executionContext: ActionExecutionContext,
    ) {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = BranllyNotifications.ensureManualMediaChannel(appContext)
        val id = executionContext.executionId.hashCode() xor executionContext.nodeId.value.hashCode()
        notificationId = id
        val cancel =
            PendingIntent.getService(
                appContext,
                id,
                RoutineExecutionService.cancelActiveIntent(appContext, executionContext.executionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            id,
            BranllyNotifications
                .builder(appContext, channel)
                .setContentTitle("Lancez le média")
                .setContentText("La routine continuera automatiquement dès que la lecture démarrera.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Lancez le média dans ${action.targetAppLabel}. La routine continuera automatiquement.",
                    ),
                ).addAction(0, "Annuler", cancel)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    override fun showInfo(
        message: String,
        executionContext: ActionExecutionContext,
    ) {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val id = executionContext.executionId.hashCode() xor executionContext.nodeId.value.hashCode() xor 0x4d454449
        manager.notify(
            id,
            BranllyNotifications
                .builder(appContext, BranllyNotifications.ensureExecutionChannel(appContext))
                .setContentTitle("Branlly Pocket")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(INFO_TIMEOUT_MILLIS)
                .build(),
        )
        Handler(Looper.getMainLooper()).postDelayed({ manager.cancel(id) }, INFO_TIMEOUT_MILLIS)
    }

    override fun clear() {
        notificationId?.let { appContext.getSystemService(NotificationManager::class.java).cancel(it) }
        notificationId = null
    }

    private companion object {
        const val INFO_TIMEOUT_MILLIS = 5_000L
    }
}
