package com.branlly.pocket.platform.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId

/** User-visible notification trampoline. Keeping an Activity foreground lets Android authorize the resumed handler launch. */
class ContinuationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val identity = ContinuationIntentExtras.read(intent)
        if (identity == null) {
            Toast.makeText(this, "Cette continuation est invalide.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startForegroundService(RoutineExecutionService.resumeIntent(this, identity))
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, FOREGROUND_GRACE_MILLIS)
    }

    companion object {
        private const val FOREGROUND_GRACE_MILLIS = 3_000L

        fun intent(context: Context, identity: ContinuationIdentity): Intent =
            Intent(context, ContinuationActivity::class.java).also { ContinuationIntentExtras.put(it, identity) }
    }
}

object ContinuationIntentExtras {
    private const val CONTINUATION_ID = "continuation_id"
    private const val EXECUTION_ID = "execution_id"
    private const val ROUTINE_ID = "routine_id"
    private const val NODE_ID = "node_id"

    fun put(intent: Intent, identity: ContinuationIdentity): Intent = intent
        .putExtra(CONTINUATION_ID, identity.continuationId)
        .putExtra(EXECUTION_ID, identity.executionId)
        .putExtra(ROUTINE_ID, identity.routineId.value)
        .putExtra(NODE_ID, identity.nodeId.value)

    fun read(intent: Intent?): ContinuationIdentity? {
        val source = intent ?: return null
        val continuationId = source.getStringExtra(CONTINUATION_ID)?.takeIf(String::isNotBlank) ?: return null
        val executionId = source.getStringExtra(EXECUTION_ID)?.takeIf(String::isNotBlank) ?: return null
        val routineId = source.getStringExtra(ROUTINE_ID)?.takeIf(String::isNotBlank) ?: return null
        val nodeId = source.getStringExtra(NODE_ID)?.takeIf(String::isNotBlank) ?: return null
        return runCatching { ContinuationIdentity(continuationId, executionId, ShortcutId(routineId), NodeId(nodeId)) }.getOrNull()
    }
}
