package com.branlly.pocket.platform.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.branlly.pocket.R
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAccentColor
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.widgetExecutableAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BranllyPocketWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        goAsync().also { pendingResult ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val available =
                        SavedShortcutStore(context.applicationContext)
                            .shortcuts
                            .first()
                            .filter { it.widgetExecutableAction() != null }
                    appWidgetIds.forEach { widgetId -> initializeWidget(context.applicationContext, manager, widgetId, available) }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_TOGGLE -> {
                WidgetPreferences(context).toggleExpanded(widgetId)
                updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }

            ACTION_RUN -> {
                WidgetPreferences(context).collapse(widgetId)
                updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
                intent.getStringExtra(EXTRA_SHORTCUT_ID)?.let { shortcutId ->
                    goAsync().also { pendingResult ->
                        CoroutineScope(Dispatchers.IO).launch {
                            runShortcut(context.applicationContext, shortcutId)
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        val preferences = WidgetPreferences(context)
        appWidgetIds.forEach(preferences::delete)
    }

    companion object {
        const val ACTION_TOGGLE = "com.branlly.pocket.widget.TOGGLE"
        const val ACTION_RUN = "com.branlly.pocket.widget.RUN"
        const val EXTRA_SHORTCUT_ID = "shortcut_id"

        private fun initializeWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            shortcuts: List<com.branlly.pocket.domain.model.ShortcutDefinition>,
        ) {
            val preferences = WidgetPreferences(context)
            if (preferences.shortcutIds(widgetId).isEmpty()) {
                preferences.save(
                    widgetId,
                    shortcuts.take(DEFAULT_SHORTCUT_COUNT).map { shortcut ->
                        WidgetShortcutSlot(shortcut.id.value, shortcut.widgetLabel.orEmpty(), shortcut.iconKey, shortcut.accentColor)
                    },
                )
            }
            updateWidget(context, manager, widgetId)
        }

        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            val preferences = WidgetPreferences(context)
            val views =
                RemoteViews(
                    context.packageName,
                    if (preferences.isExpanded(widgetId)) R.layout.widget_branlly_expanded else R.layout.widget_branlly_compact,
                )
            views.setOnClickPendingIntent(R.id.widget_center, toggleIntent(context, widgetId))
            if (preferences.isExpanded(widgetId)) {
                SLOT_IDS.forEachIndexed { index, viewId ->
                    val shortcut = preferences.slotAt(widgetId, index)
                    if (shortcut == null) {
                        views.setViewVisibility(viewId, android.view.View.INVISIBLE)
                    } else {
                        views.setViewVisibility(viewId, android.view.View.VISIBLE)
                        views.setTextViewText(viewId, shortcut.label.ifBlank { shortcut.iconKey.widgetGlyph() })
                        views.setTextColor(viewId, shortcut.accentColor.widgetColor())
                        views.setOnClickPendingIntent(viewId, runIntent(context, widgetId, shortcut.id))
                    }
                }
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun toggleIntent(
            context: Context,
            widgetId: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                widgetId,
                Intent(
                    context,
                    BranllyPocketWidget::class.java,
                ).setAction(ACTION_TOGGLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun runIntent(
            context: Context,
            widgetId: Int,
            shortcutId: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                "$widgetId:$shortcutId".hashCode(),
                Intent(context, BranllyPocketWidget::class.java)
                    .setAction(ACTION_RUN)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(EXTRA_SHORTCUT_ID, shortcutId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        suspend fun refreshAll(context: Context) {
            val available = SavedShortcutStore(context).shortcuts.first().filter { it.widgetExecutableAction() != null }
            val byId = available.associateBy { it.id.value }
            val manager = AppWidgetManager.getInstance(context)
            val preferences = WidgetPreferences(context)
            manager.getAppWidgetIds(ComponentName(context, BranllyPocketWidget::class.java)).forEach { widgetId ->
                val retained =
                    preferences.shortcutIds(widgetId).mapNotNull { id ->
                        byId[id]?.let { shortcut ->
                            WidgetShortcutSlot(id, shortcut.widgetLabel.orEmpty(), shortcut.iconKey, shortcut.accentColor)
                        }
                    }
                val additions =
                    available
                        .asSequence()
                        .filterNot { shortcut -> retained.any { it.id == shortcut.id.value } }
                        .take(MAX_SHORTCUT_COUNT - retained.size)
                        .map { shortcut ->
                            WidgetShortcutSlot(shortcut.id.value, shortcut.widgetLabel.orEmpty(), shortcut.iconKey, shortcut.accentColor)
                        }.toList()
                preferences.save(widgetId, retained + additions)
                updateWidget(context, manager, widgetId)
            }
        }

        private suspend fun runShortcut(
            context: Context,
            shortcutId: String,
        ) {
            val shortcut = SavedShortcutStore(context).shortcuts.first().firstOrNull { it.id.value == shortcutId } ?: return
            ShortcutExecutor(context).execute(shortcut)
        }

        private const val DEFAULT_SHORTCUT_COUNT = 3
        private const val MAX_SHORTCUT_COUNT = 6

        private val SLOT_IDS =
            intArrayOf(
                R.id.widget_action_top,
                R.id.widget_action_upper_left,
                R.id.widget_action_upper_right,
                R.id.widget_action_lower_left,
                R.id.widget_action_lower_right,
                R.id.widget_action_bottom,
            )
    }
}

data class WidgetShortcutSlot(
    val id: String,
    val label: String,
    val iconKey: String,
    val accentColor: ShortcutAccentColor,
)

private fun String.widgetGlyph(): String =
    when (this) {
        "route" -> "↗"
        "car" -> "▰"
        "home" -> "⌂"
        "music" -> "♪"
        "camera" -> "◉"
        "phone" -> "☎"
        "message" -> "✉"
        "work" -> "▣"
        "calendar" -> "□"
        "fitness" -> "♥"
        "settings" -> "⚙"
        "bluetooth" -> "ᛒ"
        "moon" -> "☾"
        else -> "ϟ"
    }

private fun ShortcutAccentColor.widgetColor(): Int =
    when (this) {
        ShortcutAccentColor.BLUE -> 0xFF82AFFF.toInt()
        ShortcutAccentColor.CYAN -> 0xFF75E3F5.toInt()
        ShortcutAccentColor.VIOLET -> 0xFFB99CFF.toInt()
        ShortcutAccentColor.PINK -> 0xFFFFA3D2.toInt()
        ShortcutAccentColor.RED -> 0xFFFF9C9C.toInt()
        ShortcutAccentColor.ORANGE -> 0xFFFFB77A.toInt()
        ShortcutAccentColor.YELLOW -> 0xFFFFE082.toInt()
        ShortcutAccentColor.GREEN -> 0xFF9BE49D.toInt()
        ShortcutAccentColor.MINT -> 0xFF87E8C3.toInt()
        ShortcutAccentColor.WHITE -> 0xFFF1F1F7.toInt()
        ShortcutAccentColor.GRAY -> 0xFFC3C6D0.toInt()
    }

class WidgetPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun shortcutIds(widgetId: Int): List<String> =
        preferences
            .getString(KEY_SHORTCUTS + widgetId, "")
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .take(MAX_SHORTCUTS)

    fun shortcutLabels(widgetId: Int): List<String> =
        (0 until MAX_SHORTCUTS).mapNotNull { index -> preferences.getString("$KEY_LABEL$widgetId:$index", null) }

    fun slotAt(
        widgetId: Int,
        index: Int,
    ): WidgetShortcutSlot? {
        val id = shortcutIds(widgetId).getOrNull(index) ?: return null
        val label = preferences.getString("$KEY_LABEL$widgetId:$index", null) ?: return null
        val iconKey = preferences.getString("$KEY_ICON$widgetId:$index", "bolt").orEmpty()
        val accent =
            runCatching { ShortcutAccentColor.valueOf(preferences.getString("$KEY_ACCENT$widgetId:$index", null).orEmpty()) }
                .getOrDefault(ShortcutAccentColor.BLUE)
        return WidgetShortcutSlot(id, label, iconKey, accent)
    }

    fun save(
        widgetId: Int,
        shortcuts: List<WidgetShortcutSlot>,
    ) {
        val selected = shortcuts.distinctBy(WidgetShortcutSlot::id).take(MAX_SHORTCUTS)
        preferences.edit {
            putString(KEY_SHORTCUTS + widgetId, selected.joinToString(",") { it.id })
            selected.forEachIndexed { index, shortcut ->
                putString("$KEY_LABEL$widgetId:$index", shortcut.label.take(MAX_LABEL_LENGTH))
                putString("$KEY_ICON$widgetId:$index", shortcut.iconKey)
                putString("$KEY_ACCENT$widgetId:$index", shortcut.accentColor.name)
            }
            (selected.size until MAX_SHORTCUTS).forEach { index ->
                remove("$KEY_LABEL$widgetId:$index")
                remove("$KEY_ICON$widgetId:$index")
                remove("$KEY_ACCENT$widgetId:$index")
            }
            putBoolean(KEY_EXPANDED + widgetId, false)
        }
    }

    fun isExpanded(widgetId: Int): Boolean = preferences.getBoolean(KEY_EXPANDED + widgetId, false)

    fun toggleExpanded(widgetId: Int) = preferences.edit { putBoolean(KEY_EXPANDED + widgetId, !isExpanded(widgetId)) }

    fun collapse(widgetId: Int) = preferences.edit { putBoolean(KEY_EXPANDED + widgetId, false) }

    fun delete(widgetId: Int) =
        preferences.edit {
            remove(KEY_SHORTCUTS + widgetId)
            remove(KEY_EXPANDED + widgetId)
            (0 until MAX_SHORTCUTS).forEach { index ->
                remove("$KEY_LABEL$widgetId:$index")
                remove("$KEY_ICON$widgetId:$index")
                remove("$KEY_ACCENT$widgetId:$index")
            }
        }

    private companion object {
        const val FILE_NAME = "widget_preferences"
        const val KEY_SHORTCUTS = "shortcuts_"
        const val KEY_EXPANDED = "expanded_"
        const val KEY_LABEL = "label_"
        const val KEY_ICON = "icon_"
        const val KEY_ACCENT = "accent_"
        const val MAX_SHORTCUTS = 6
        const val MAX_LABEL_LENGTH = 80
    }
}
