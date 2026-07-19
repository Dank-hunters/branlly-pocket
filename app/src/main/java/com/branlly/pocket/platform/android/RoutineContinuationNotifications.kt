package com.branlly.pocket.platform.android

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.branlly.pocket.R
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.execution.RoutineContinuation

interface ContinuationNotificationGateway {
    fun post(continuation: RoutineContinuation, reason: String)
    fun dismiss(continuationId: String)
    fun remove(continuationId: String)
    fun showMessage(message: String)
}

class AndroidContinuationNotificationGateway(private val context: Context) : ContinuationNotificationGateway {
    override fun post(continuation: RoutineContinuation, reason: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Actions à continuer", NotificationManager.IMPORTANCE_HIGH),
        )
        val identity = continuation.identity()
        val continueIntent = PendingIntent.getActivity(
            context,
            continuation.requestCode(CONTINUE_SALT),
            ContinuationActivity.intent(context, identity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            context,
            continuation.requestCode(CANCEL_SALT),
            RoutineExecutionService.cancelIntent(context, identity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            continuation.notificationId(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Continuer la routine")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setContentIntent(continueIntent)
                .addAction(0, "Continuer", continueIntent)
                .addAction(0, "Annuler", cancelIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
        scheduleExpiration(continuation)
    }

    override fun dismiss(continuationId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(continuationId.hashCode())
    }

    override fun remove(continuationId: String) {
        dismiss(continuationId)
        context.getSystemService(AlarmManager::class.java).cancel(expirationPendingIntent(continuationId, null))
    }

    override fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleExpiration(continuation: RoutineContinuation) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            continuation.expiresAtMillis,
            expirationPendingIntent(continuation.continuationId, continuation.identity()),
        )
    }

    private fun expirationPendingIntent(continuationId: String, identity: ContinuationIdentity?): PendingIntent {
        val intent = Intent(context, ContinuationExpiryReceiver::class.java)
            .setAction(RoutineExecutionService.ACTION_EXPIRE)
        identity?.let { ContinuationIntentExtras.put(intent, it) }
        return PendingIntent.getBroadcast(
            context,
            continuationId.hashCode() xor EXPIRE_SALT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun RoutineContinuation.notificationId(): Int = continuationId.hashCode()
    private fun RoutineContinuation.requestCode(salt: Int): Int = continuationId.hashCode() xor salt

    private companion object {
        const val CHANNEL = "external_action_continuations"
        const val CONTINUE_SALT = 0x1431
        const val CANCEL_SALT = 0x2982
        const val EXPIRE_SALT = 0x7a31
    }
}

fun RoutineContinuation.identity() = ContinuationIdentity(continuationId, executionId, routineId, nodeId)
