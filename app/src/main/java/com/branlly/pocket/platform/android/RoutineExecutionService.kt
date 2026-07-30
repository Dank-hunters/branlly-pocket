package com.branlly.pocket.platform.android

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.branlly.pocket.data.PersistentRoutineExecutionStateStore
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.execution.ContinuationIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class ExecutionJobRegistry {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun register(
        executionId: String,
        job: Job,
    ): Boolean = jobs.putIfAbsent(executionId, job) == null

    fun complete(
        executionId: String,
        job: Job,
    ) {
        jobs.remove(executionId, job)
    }

    fun contains(executionId: String): Boolean = jobs.containsKey(executionId)

    fun cancel(executionId: String): Boolean {
        val job = jobs.remove(executionId) ?: return false
        job.cancel()
        return true
    }
}

/** Foreground transport for every new, resumed, cancelled or expired execution command. */
class RoutineExecutionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeCommands = AtomicInteger(0)
    private val executionJobs = ExecutionJobRegistry()

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val sourceIntent = intent ?: Intent().setAction(ACTION_START)
        val command = sourceIntent.action ?: ACTION_START
        if (command == ACTION_START || command == ACTION_TEST) {
            val store = PersistentRoutineExecutionStateStore(applicationContext)
            val active = store.active(System.currentTimeMillis())
            if (active?.status == com.branlly.pocket.domain.execution.ExecutionStatus.RUNNING &&
                !executionJobs.contains(active.executionId)
            ) {
                store.finish(active.executionId)
            }
        }
        activeCommands.incrementAndGet()
        startForeground(NOTIFICATION_ID, notification("Routine en cours", "Préparation…"))
        val executionId =
            when (command) {
                ACTION_START, ACTION_TEST, ACTION_CANCEL_ACTIVE -> sourceIntent.getStringExtra(EXTRA_EXECUTION_ID)
                ACTION_RESUME -> ContinuationIntentExtras.read(sourceIntent)?.executionId
                else -> null
            }
        var job: Job? = null
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result =
                        when (command) {
                            ACTION_START -> executeNew(sourceIntent)
                            ACTION_RESUME -> resume(sourceIntent)
                            ACTION_TEST -> executeTransient(sourceIntent)
                            ACTION_CANCEL -> cancel(sourceIntent, expired = false)
                            ACTION_EXPIRE -> cancel(sourceIntent, expired = true)
                            ACTION_CANCEL_ACTIVE -> cancelActive(sourceIntent)
                            else -> RoutineExecutionResult.ContinuationRejected("Commande inconnue.")
                        }
                    Log.i(
                        TAG,
                        "APP_PACKAGE=$packageName command=$command state=FINISHED result=$result timestamp=${System.currentTimeMillis()}",
                    )
                } catch (error: Throwable) {
                    if (command == ACTION_START || command == ACTION_TEST) {
                        executionId?.let { PersistentRoutineExecutionStateStore(applicationContext).finish(it) }
                    }
                    Log.e(TAG, "APP_PACKAGE=$packageName command=$command state=CRASHED", error)
                } finally {
                    if (command != ACTION_CANCEL_ACTIVE) {
                        executionId?.let { id -> job?.let { executionJobs.complete(id, it) } }
                    }
                    if (activeCommands.decrementAndGet() == 0) stopSelf()
                }
            }
        if (command != ACTION_CANCEL_ACTIVE) executionId?.let { id -> job?.let { executionJobs.register(id, it) } }
        job.start()
        return START_NOT_STICKY
    }

    private suspend fun executeNew(intent: Intent): RoutineExecutionResult {
        val shortcutId =
            intent.getStringExtra(EXTRA_SHORTCUT_ID)
                ?: return RoutineExecutionResult.ContinuationRejected("Routine absente.")
        val executionId =
            intent.getStringExtra(EXTRA_EXECUTION_ID)
                ?: return RoutineExecutionResult.ContinuationRejected("Identifiant d’exécution absent.")
        val shortcut =
            SavedShortcutStore(applicationContext).shortcuts.first().firstOrNull { it.id.value == shortcutId }
                ?: return RoutineExecutionResult.ContinuationRejected("Routine introuvable.")
        Log.i(
            TAG,
            "APP_PACKAGE=$packageName execution=$executionId routine=${shortcut.id.value} name=${shortcut.name} state=STARTED timestamp=${System.currentTimeMillis()}",
        )
        update(shortcut.name, "Exécution en arrière-plan")
        return RoutineOrchestrator.execute(applicationContext, shortcut, executionId)
    }

    private suspend fun executeTransient(intent: Intent): RoutineExecutionResult {
        val executionId =
            intent.getStringExtra(EXTRA_EXECUTION_ID)
                ?: return RoutineExecutionResult.ContinuationRejected("Identifiant d’exécution absent.")
        val snapshot =
            intent
                .getStringExtra(EXTRA_ROUTINE_SNAPSHOT)
                ?.let { SavedShortcutStore(applicationContext).decodeSnapshot(it) }
                ?: return RoutineExecutionResult.ContinuationRejected("Action de test invalide.")
        return RoutineOrchestrator.execute(applicationContext, snapshot, executionId)
    }

    private suspend fun resume(intent: Intent): RoutineExecutionResult {
        val identity =
            ContinuationIntentExtras.read(intent)
                ?: return RoutineExecutionResult.ContinuationRejected("Continuation invalide.")
        Log.i(
            TAG,
            "APP_PACKAGE=$packageName execution=${identity.executionId} continuation=${identity.continuationId} node=${identity.nodeId.value} state=RESUME_REQUESTED",
        )
        update("Routine en cours", "Reprise de l’action en cours")
        val destination = intent.getStringExtra(EXTRA_ROUTE_DESTINATION)?.trim().orEmpty()
        return RoutineOrchestrator.resume(
            applicationContext,
            identity,
            if (destination.isBlank()) emptyMap() else mapOf("destination" to destination),
        )
    }

    private fun cancel(
        intent: Intent,
        expired: Boolean,
    ): RoutineExecutionResult {
        val identity =
            ContinuationIntentExtras.read(intent)
                ?: return RoutineExecutionResult.ContinuationRejected("Continuation invalide.")
        Log.i(
            TAG,
            "APP_PACKAGE=$packageName execution=${identity.executionId} continuation=${identity.continuationId} node=${identity.nodeId.value} state=${if (expired) "EXPIRED" else "CANCEL_REQUESTED"}",
        )
        return RoutineOrchestrator.cancel(applicationContext, identity, expired)
    }

    private fun cancelActive(intent: Intent): RoutineExecutionResult {
        val executionId =
            intent.getStringExtra(EXTRA_EXECUTION_ID)
                ?: return RoutineExecutionResult.ContinuationRejected("Identifiant d’exécution absent.")
        executionJobs.cancel(executionId)
        val store = PersistentRoutineExecutionStateStore(applicationContext)
        val continuation = store.active(System.currentTimeMillis())?.takeIf { it.executionId == executionId }?.continuation
        if (continuation != null) {
            return RoutineOrchestrator.cancel(
                applicationContext,
                ContinuationIdentity(
                    continuation.continuationId,
                    continuation.executionId,
                    continuation.routineId,
                    continuation.nodeId,
                ),
            )
        }
        store.finish(executionId)
        return RoutineExecutionResult.Cancelled("Annulation utilisateur.")
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
        val channel = BranllyNotifications.ensureExecutionChannel(this)
        return BranllyNotifications
            .builder(this, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.branlly.pocket.action.START_ROUTINE"
        const val ACTION_RESUME = "com.branlly.pocket.action.RESUME_ROUTINE"
        const val ACTION_TEST = "com.branlly.pocket.action.TEST_ROUTINE"
        const val ACTION_CANCEL = "com.branlly.pocket.action.CANCEL_ROUTINE"
        const val ACTION_EXPIRE = "com.branlly.pocket.action.EXPIRE_ROUTINE"
        const val ACTION_CANCEL_ACTIVE = "com.branlly.pocket.action.CANCEL_ACTIVE_ROUTINE"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val EXTRA_EXECUTION_ID = "execution_id"
        private const val EXTRA_ROUTINE_SNAPSHOT = "routine_snapshot"
        const val EXTRA_ROUTE_DESTINATION = "route_destination"
        private const val TAG = "BranllyRoutine"

        fun start(
            context: Context,
            shortcutId: String,
        ) {
            context.startForegroundService(
                Intent(context, RoutineExecutionService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SHORTCUT_ID, shortcutId)
                    .putExtra(EXTRA_EXECUTION_ID, UUID.randomUUID().toString()),
            )
        }

        fun startTransient(
            context: Context,
            routine: com.branlly.pocket.domain.model.ShortcutDefinition,
        ) {
            context.startForegroundService(
                Intent(context, RoutineExecutionService::class.java)
                    .setAction(ACTION_TEST)
                    .putExtra(EXTRA_EXECUTION_ID, UUID.randomUUID().toString())
                    .putExtra(EXTRA_ROUTINE_SNAPSHOT, SavedShortcutStore(context.applicationContext).encodeSnapshot(routine)),
            )
        }

        fun cancelActiveIntent(
            context: Context,
            executionId: String,
        ): Intent =
            Intent(context, RoutineExecutionService::class.java)
                .setAction(ACTION_CANCEL_ACTIVE)
                .putExtra(EXTRA_EXECUTION_ID, executionId)

        fun resumeIntent(
            context: Context,
            identity: ContinuationIdentity,
        ): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_RESUME),
                identity,
            )

        fun cancelIntent(
            context: Context,
            identity: ContinuationIdentity,
        ): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_CANCEL),
                identity,
            )

        fun expireIntent(
            context: Context,
            identity: ContinuationIdentity,
        ): Intent =
            ContinuationIntentExtras.put(
                Intent(context, RoutineExecutionService::class.java).setAction(ACTION_EXPIRE),
                identity,
            )
    }
}
