package com.branlly.pocket.platform.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.branlly.pocket.R
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.execution.ContinuationIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Foreground transport for every new, resumed, cancelled or expired execution command. */
class RoutineExecutionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeCommands = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sourceIntent = intent ?: Intent().setAction(ACTION_START)
        val command = sourceIntent.action ?: ACTION_START
        activeCommands.incrementAndGet()
        startForeground(NOTIFICATION_ID, notification("Routine en cours", "Préparation…"))
        scope.launch {
            try {
                val result = when (command) {
                    ACTION_START -> executeNew(sourceIntent)
                    ACTION_RESUME -> resume(sourceIntent)
                    ACTION_CANCEL -> cancel(sourceIntent, expired = false)
                    ACTION_EXPIRE -> cancel(sourceIntent, expired = true)
                    else -> RoutineExecutionResult.ContinuationRejected("Commande inconnue.")
                }
                Log.i(TAG, "APP_PACKAGE=$packageName command=$command state=FINISHED result=$result timestamp=${System.currentTimeMillis()}")
            } catch (error: Throwable) {
                Log.e(TAG, "APP_PACKAGE=$packageName command=$command state=CRASHED", error)
            } finally {
                if (activeCommands.decrementAndGet() == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun executeNew(intent: Intent): RoutineExecutionResult {
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
            ?: return RoutineExecutionResult.ContinuationRejected("Routine absente.")
        val executionId = UUID.randomUUID().toString()
        val shortcut = SavedShortcutStore(applicationContext).shortcuts.first().firstOrNull { it.id.value == shortcutId }
            ?: return RoutineExecutionResult.ContinuationRejected("Routine introuvable.")
        Log.i(
            TAG,
            "APP_PACKAGE=$packageName execution=$executionId routine=${shortcut.id.value} name=${shortcut.name} state=STARTED timestamp=${System.currentTimeMillis()}",
        )
        update(shortcut.name, "Exécution en arrière-plan")
        return RoutineOrchestrator.execute(applicationContext, shortcut, executionId)
    }

    private suspend fun resume(intent: Intent): RoutineExecutionResult {
        val identity = ContinuationIntentExtras.read(intent)
            ?: return RoutineExecutionResult.ContinuationRejected("Continuation invalide.")
        Log.i(TAG, "APP_PACKAGE=$packageName execution=${identity.executionId} continuation=${identity.continuationId} node=${identity.nodeId.value} state=RESUME_REQUESTED")
        update("Reprise de la routine", "Validation de la continuation…")
        return RoutineOrchestrator.resume(applicationContext, identity)
    }

    private fun cancel(intent: Intent, expired: Boolean): RoutineExecutionResult {
        val identity = ContinuationIntentExtras.read(intent)
            ?: return RoutineExecutionResult.ContinuationRejected("Continuation invalide.")
        Log.i(
            TAG,
            "APP_PACKAGE=$packageName execution=${identity.executionId} continuation=${identity.continuationId} node=${identity.nodeId.value} state=${if (expired) "EXPIRED" else "CANCEL_REQUESTED"}",
        )
        return RoutineOrchestrator.cancel(applicationContext, identity, expired)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun update(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text))
    }

    private fun notification(title: String, text: String): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Exécution Branlly", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.branlly.pocket.action.START_ROUTINE"
        const val ACTION_RESUME = "com.branlly.pocket.action.RESUME_ROUTINE"
        const val ACTION_CANCEL = "com.branlly.pocket.action.CANCEL_ROUTINE"
        const val ACTION_EXPIRE = "com.branlly.pocket.action.EXPIRE_ROUTINE"
        private const val CHANNEL = "routine_execution"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val TAG = "BranllyRoutine"

        fun start(context: Context, shortcutId: String) {
            context.startForegroundService(
                Intent(context, RoutineExecutionService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SHORTCUT_ID, shortcutId),
            )
        }

        fun resumeIntent(context: Context, identity: ContinuationIdentity): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_RESUME),
                identity,
            )

        fun cancelIntent(context: Context, identity: ContinuationIdentity): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_CANCEL),
                identity,
            )

        fun expireIntent(context: Context, identity: ContinuationIdentity): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_EXPIRE),
                identity,
            )
    }
}
