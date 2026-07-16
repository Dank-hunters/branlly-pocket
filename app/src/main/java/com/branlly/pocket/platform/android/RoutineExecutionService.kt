package com.branlly.pocket.platform.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.branlly.pocket.R
import com.branlly.pocket.data.SavedShortcutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Foreground owner for user-started routines; it survives activity backgrounding. */
class RoutineExecutionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val shortcutId = intent?.getStringExtra(EXTRA_SHORTCUT_ID) ?: return START_NOT_STICKY
        val executionId = UUID.randomUUID().toString()
        startForeground(NOTIFICATION_ID, notification("Routine en cours", "Préparation…"))
        scope.launch {
            val shortcut = SavedShortcutStore(applicationContext).shortcuts.first().firstOrNull { it.id.value == shortcutId }
            if (shortcut == null) {
                Log.w(TAG, "execution=$executionId missingRoutine=$shortcutId")
                stopSelf(startId)
                return@launch
            }
            Log.i(TAG, "execution=$executionId routine=${shortcut.id.value} state=STARTED timestamp=${System.currentTimeMillis()}")
            update("${shortcut.name}", "Exécution en arrière-plan")
            val result = ShortcutExecutor(applicationContext).execute(shortcut)
            Log.i(
                TAG,
                "execution=$executionId routine=${shortcut.id.value} state=FINISHED result=$result timestamp=${System.currentTimeMillis()}",
            )
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun update(
        title: String,
        text: String,
    ) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text))
    }

    private fun notification(
        title: String,
        text: String,
    ): android.app.Notification {
        getSystemService(
            NotificationManager::class.java,
        ).createNotificationChannel(NotificationChannel(CHANNEL, "Exécution Branlly", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat
            .Builder(
                this,
                CHANNEL,
            ).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "routine_execution"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val TAG = "BranllyRoutine"

        fun start(
            context: Context,
            shortcutId: String,
        ) {
            context.startForegroundService(Intent(context, RoutineExecutionService::class.java).putExtra(EXTRA_SHORTCUT_ID, shortcutId))
        }
    }
}
