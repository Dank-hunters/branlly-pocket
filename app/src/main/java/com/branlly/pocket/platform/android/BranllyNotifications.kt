package com.branlly.pocket.platform.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.branlly.pocket.R
import com.branlly.pocket.platform.android.setup.PermissionSettingsIntents

object BranllyNotifications {
    val SMALL_ICON: Int = R.drawable.ic_notification_branlly
    const val CONTINUATION_CHANNEL: String = "external_action_continuations"
    const val EXECUTION_CHANNEL: String = "routine_execution"
    const val MANUAL_MEDIA_CHANNEL: String = "manual_media_playback"
    const val MEDIA_EXECUTION_RESULTS_CHANNEL: String = "media_execution_results"
    const val SUGGESTION_CHANNEL: String = "context_suggestions"

    fun builder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId).setSmallIcon(SMALL_ICON)

    fun ensureContinuationChannel(context: Context): String {
        // v0.15.0 already created this channel as HIGH. Keep its stable id to preserve user choices.
        createChannel(context, CONTINUATION_CHANNEL, "Actions à continuer", NotificationManager.IMPORTANCE_HIGH)
        return CONTINUATION_CHANNEL
    }

    fun ensureExecutionChannel(context: Context): String {
        createChannel(context, EXECUTION_CHANNEL, "Exécution Branlly", NotificationManager.IMPORTANCE_LOW)
        return EXECUTION_CHANNEL
    }

    fun ensureManualMediaChannel(context: Context): String {
        createChannel(context, MANUAL_MEDIA_CHANNEL, "Lecture manuelle", NotificationManager.IMPORTANCE_HIGH)
        return MANUAL_MEDIA_CHANNEL
    }

    fun ensureMediaExecutionResultsChannel(context: Context): String {
        createChannel(context, MEDIA_EXECUTION_RESULTS_CHANNEL, "Résultats de lecture multimédia", NotificationManager.IMPORTANCE_DEFAULT)
        return MEDIA_EXECUTION_RESULTS_CHANNEL
    }

    fun ensureSuggestionChannel(context: Context): String {
        createChannel(context, SUGGESTION_CHANNEL, "Suggestions Branlly", NotificationManager.IMPORTANCE_DEFAULT)
        return SUGGESTION_CHANNEL
    }

    fun continuationChannelSettings(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CONTINUATION_CHANNEL)

    private fun createChannel(
        context: Context,
        id: String,
        name: String,
        importance: Int,
    ) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(id, name, importance),
        )
    }
}

enum class TestNotificationAction {
    CONTINUE,
    CANCEL,
}

object TestNotificationContract {
    const val TIMEOUT_MILLIS: Long = 60_000L

    fun hasBusinessSideEffect(action: TestNotificationAction): Boolean =
        when (action) {
            TestNotificationAction.CONTINUE,
            TestNotificationAction.CANCEL,
            -> false
        }
}

class TestNotificationManager(
    private val context: Context,
) {
    fun show() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = BranllyNotifications.ensureContinuationChannel(context)
        val continueIntent = actionPendingIntent(TestNotificationAction.CONTINUE)
        val cancelIntent = actionPendingIntent(TestNotificationAction.CANCEL)
        manager.notify(
            NOTIFICATION_ID,
            BranllyNotifications
                .builder(context, channel)
                .setContentTitle("Test Branlly Pocket")
                .setContentText("Cette notification vérifie seulement son affichage.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Cette notification reproduit une demande de continuation sans lancer de routine.",
                    ),
                ).setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(TestNotificationContract.TIMEOUT_MILLIS)
                .addAction(0, "Continuer", continueIntent)
                .addAction(0, "Annuler", cancelIntent)
                .build(),
        )
    }

    fun dismiss() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun channelSettingsIntent(): Intent {
        BranllyNotifications.ensureContinuationChannel(context)
        return BranllyNotifications.continuationChannelSettings(context)
    }

    private fun actionPendingIntent(action: TestNotificationAction): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID xor action.ordinal,
            Intent(context, TestNotificationActionReceiver::class.java).setAction(action.intentAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        internal const val NOTIFICATION_ID: Int = 0xB151
    }
}

class TestNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (TestNotificationAction.entries.none { it.intentAction == intent.action }) return
        TestNotificationManager(context).dismiss()
    }
}

private val TestNotificationAction.intentAction: String
    get() = "com.branlly.pocket.TEST_NOTIFICATION_$name"

fun Context.openNotificationSettingsSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { startActivity(PermissionSettingsIntents.appNotifications(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
