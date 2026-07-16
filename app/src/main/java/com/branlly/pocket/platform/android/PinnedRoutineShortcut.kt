package com.branlly.pocket.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.core.content.getSystemService
import com.branlly.pocket.R
import com.branlly.pocket.data.SavedShortcutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PinnedRoutineShortcut {
    const val EXTRA_SHORTCUT_ID = "branlly_pinned_shortcut_id"

    fun request(
        context: Context,
        shortcutId: String,
        label: String,
    ): Boolean {
        val manager = context.getSystemService<ShortcutManager>() ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val info =
            ShortcutInfo
                .Builder(context, "branlly:$shortcutId")
                .setShortLabel(label.take(25).ifBlank { "Branlly Pocket" })
                .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(Intent(context, PinnedRoutineReceiver::class.java).putExtra(EXTRA_SHORTCUT_ID, shortcutId))
                .build()
        return manager.requestPinShortcut(info, null)
    }
}

class PinnedRoutineReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(PinnedRoutineShortcut.EXTRA_SHORTCUT_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SavedShortcutStore(context).shortcuts.first().firstOrNull { it.id.value == id }?.let {
                    ShortcutExecutor(context.applicationContext).execute(it)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
