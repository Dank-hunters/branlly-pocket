package com.branlly.pocket.platform.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.branlly.pocket.data.PersistentRoutineExecutionStateStore
import com.branlly.pocket.domain.execution.ContinuationIdentity
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutId
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground continuation trampoline; route continuations collect their structured destination before claiming. */
class ContinuationActivity : Activity() {
    private var identity: ContinuationIdentity? = null
    private val completed = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = ContinuationIntentExtras.read(intent)
        if (parsed == null) {
            Toast.makeText(this, "Cette continuation est invalide.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        identity = parsed
        val continuation = PersistentRoutineExecutionStateStore(applicationContext).active(System.currentTimeMillis())?.continuation
        if (continuation?.continuationId == parsed.continuationId &&
            continuation.workflowCheckpoint?.stateKey == ROUTE_DESTINATION_STATE
        ) {
            showDestinationPrompt(parsed)
        } else {
            resume(parsed)
        }
    }

    private fun showDestinationPrompt(identity: ContinuationIdentity) {
        val field =
            EditText(this).apply {
                hint = "Destination"
                isSingleLine = true
                setTextColor(0xFFE5F6FC.toInt())
                setHintTextColor(0xFF7F9DB0.toInt())
            }
        val confirm =
            Button(this).apply {
                text = "Confirmer"
                isEnabled = false
            }
        val cancel = Button(this).apply { text = "Annuler" }
        field.addTextChangedListener(SimpleTextWatcher { confirm.isEnabled = it.trim().isNotEmpty() })
        confirm.setOnClickListener {
            val value = field.text.toString().trim()
            if (value.isNotEmpty() && completed.compareAndSet(false, true)) {
                startForegroundService(
                    RoutineExecutionService
                        .resumeIntent(this, identity)
                        .putExtra(RoutineExecutionService.EXTRA_ROUTE_DESTINATION, value),
                )
                finish()
            }
        }
        cancel.setOnClickListener { cancel(identity) }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 60, 40, 40)
                setBackgroundColor(0xFF030B16.toInt())
                addView(
                    TextView(this@ContinuationActivity).apply {
                        text = "Choisir une destination"
                        textSize = 22f
                        setTextColor(0xFF67DDF5.toInt())
                    },
                )
                addView(field)
                addView(confirm)
                addView(cancel)
            },
        )
        field.requestFocus()
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    @Deprecated("Android framework callback")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        identity?.let(::cancel) ?: super.onBackPressed()
    }

    private fun cancel(identity: ContinuationIdentity) {
        if (completed.compareAndSet(false, true)) startForegroundService(RoutineExecutionService.cancelIntent(this, identity))
        finish()
    }

    private fun resume(identity: ContinuationIdentity) {
        startForegroundService(RoutineExecutionService.resumeIntent(this, identity))
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, FOREGROUND_GRACE_MILLIS)
    }

    companion object {
        private const val FOREGROUND_GRACE_MILLIS = 3_000L
        private const val ROUTE_DESTINATION_STATE = "route_destination_v1"

        fun intent(
            context: Context,
            identity: ContinuationIdentity,
        ): Intent = Intent(context, ContinuationActivity::class.java).also { ContinuationIntentExtras.put(it, identity) }
    }
}

private class SimpleTextWatcher(
    private val changed: (String) -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int,
    ) = Unit

    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int,
    ) = changed(s?.toString().orEmpty())

    override fun afterTextChanged(s: android.text.Editable?) = Unit
}

object ContinuationIntentExtras {
    private const val CONTINUATION_ID = "continuation_id"
    private const val EXECUTION_ID = "execution_id"
    private const val ROUTINE_ID = "routine_id"
    private const val NODE_ID = "node_id"

    fun put(
        intent: Intent,
        identity: ContinuationIdentity,
    ): Intent =
        intent
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
