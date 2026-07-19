package com.branlly.pocket.platform.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.branlly.pocket.platform.android.actions.BluetoothEnableGateway
import com.branlly.pocket.platform.android.actions.BluetoothEnableRequestResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal enum class BluetoothSystemRequestResult {
    ACCEPTED,
    REFUSED,
    PERMISSION_DENIED,
}

/** One-shot registry. Late results are retained only until the matching waiter consumes them. */
internal class BluetoothRequestResultRegistry {
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<BluetoothSystemRequestResult>>()
    private val earlyResults = ConcurrentHashMap<String, BluetoothSystemRequestResult>()

    suspend fun await(requestId: String, timeoutMillis: Long): BluetoothSystemRequestResult? {
        earlyResults.remove(requestId)?.let { return it }
        val deferred = CompletableDeferred<BluetoothSystemRequestResult>()
        val existing = waiters.putIfAbsent(requestId, deferred)
        val selected = existing ?: deferred
        earlyResults.remove(requestId)?.let { result -> selected.complete(result) }
        return try {
            withTimeoutOrNull(timeoutMillis) { selected.await() }
        } finally {
            waiters.remove(requestId, selected)
            earlyResults.remove(requestId)
        }
    }

    fun publish(requestId: String, result: BluetoothSystemRequestResult) {
        val waiter = waiters[requestId]
        if (waiter == null || !waiter.complete(result)) earlyResults[requestId] = result
    }

    fun hasPendingWork(): Boolean = waiters.isNotEmpty() || earlyResults.isNotEmpty()
}

private object BluetoothRequestBroker {
    val registry = BluetoothRequestResultRegistry()
}

/** Minimal non-exported Activity owning runtime permission and ACTION_REQUEST_ENABLE results. */
class BluetoothRequestActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestEnable() else complete(BluetoothSystemRequestResult.PERMISSION_DENIED)
    }
    private val enableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        complete(if (result.resultCode == Activity.RESULT_OK) BluetoothSystemRequestResult.ACCEPTED else BluetoothSystemRequestResult.REFUSED)
    }

    private val requestId: String? get() = intent?.getStringExtra(EXTRA_REQUEST_ID)?.takeIf(String::isNotBlank)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (requestId == null) {
            finish()
            return
        }
        if (savedInstanceState == null) beginRequest()
    }

    private fun beginRequest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requestEnable()
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestEnable() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null) {
            complete(BluetoothSystemRequestResult.REFUSED)
        } else if (adapter.state == BluetoothAdapter.STATE_ON) {
            complete(BluetoothSystemRequestResult.ACCEPTED)
        } else {
            enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun complete(result: BluetoothSystemRequestResult) {
        val id = requestId ?: return finish()
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(id, result.name).commit()
        BluetoothRequestBroker.registry.publish(id, result)
        finish()
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        internal const val PREFERENCES = "bluetooth_request_results"

        fun intent(context: Context, requestId: String): Intent =
            Intent(context, BluetoothRequestActivity::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

class AndroidBluetoothEnableGateway(
    context: Context,
) : BluetoothEnableGateway {
    private val appContext = context.applicationContext

    override suspend fun requestEnable(requestId: String, timeoutMillis: Long): BluetoothEnableRequestResult {
        val startedAt = System.currentTimeMillis()
        consumePersisted(requestId)?.let { result -> return resolveSystemResult(result, timeoutMillis) }
        return try {
            appContext.startActivity(BluetoothRequestActivity.intent(appContext, requestId))
            val systemResult = BluetoothRequestBroker.registry.await(requestId, timeoutMillis)
                ?: consumePersisted(requestId)
                ?: return BluetoothEnableRequestResult.TimedOut
            val remaining = (timeoutMillis - (System.currentTimeMillis() - startedAt)).coerceAtLeast(1L)
            resolveSystemResult(systemResult, remaining)
        } catch (error: SecurityException) {
            BluetoothEnableRequestResult.Failed(error.message ?: "Android a refusé la demande Bluetooth.")
        } finally {
            appContext.getSharedPreferences(BluetoothRequestActivity.PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(requestId).apply()
        }
    }

    private suspend fun resolveSystemResult(
        result: BluetoothSystemRequestResult,
        timeoutMillis: Long,
    ): BluetoothEnableRequestResult = when (result) {
        BluetoothSystemRequestResult.ACCEPTED -> {
            if (awaitStateOn(timeoutMillis)) BluetoothEnableRequestResult.Enabled else BluetoothEnableRequestResult.TimedOut
        }
        BluetoothSystemRequestResult.REFUSED -> BluetoothEnableRequestResult.Refused
        BluetoothSystemRequestResult.PERMISSION_DENIED -> BluetoothEnableRequestResult.PermissionDenied
    }

    private fun consumePersisted(requestId: String): BluetoothSystemRequestResult? {
        val preferences = appContext.getSharedPreferences(BluetoothRequestActivity.PREFERENCES, Context.MODE_PRIVATE)
        val value = preferences.getString(requestId, null) ?: return null
        preferences.edit().remove(requestId).commit()
        return runCatching { BluetoothSystemRequestResult.valueOf(value) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitStateOn(timeoutMillis: Long): Boolean {
        val adapter = appContext.getSystemService(BluetoothManager::class.java).adapter ?: return false
        if (runCatching { adapter.state == BluetoothAdapter.STATE_ON }.getOrDefault(false)) return true
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON && continuation.isActive) {
                            runCatching { appContext.unregisterReceiver(this) }
                            continuation.resume(true)
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                continuation.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }
                if (runCatching { adapter.state == BluetoothAdapter.STATE_ON }.getOrDefault(false) && continuation.isActive) {
                    runCatching { appContext.unregisterReceiver(receiver) }
                    continuation.resume(true)
                }
            }
        } ?: false
    }
}
