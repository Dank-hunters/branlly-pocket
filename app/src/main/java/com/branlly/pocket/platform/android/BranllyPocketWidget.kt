package com.branlly.pocket.platform.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import com.branlly.pocket.MainActivity
import com.branlly.pocket.R
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.model.ShortcutDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget exposing up to four saved routines. */
class BranllyPocketWidget : RoutineWidgetProvider(WidgetType.ROUTINES) {
    companion object {
        suspend fun refreshAll(context: Context) = RoutineWidgets.refreshAll(context)
    }
}

/** Home-screen widget exposing up to three saved routines and the root Create destination. */
class BranllyPocketCreateWidget : RoutineWidgetProvider(WidgetType.ROUTINES_WITH_CREATE)

abstract class RoutineWidgetProvider(
    private val type: WidgetType,
) : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        goAsync().also { pendingResult ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    appWidgetIds.forEach { widgetId -> RoutineWidgets.update(context.applicationContext, widgetId, type) }
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
        if (intent.action != RoutineWidgetIntents.ACTION_RUN_ROUTINE) return
        val routineId = intent.getStringExtra(RoutineWidgetIntents.EXTRA_ROUTINE_ID) ?: return
        goAsync().also { pendingResult ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (RoutineWidgets.routineExists(context.applicationContext, routineId)) {
                        RoutineExecutionService.start(context.applicationContext, routineId)
                    }
                } finally {
                    pendingResult.finish()
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

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray,
    ) {
        val preferences = WidgetPreferences(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) -> preferences.copy(oldId, newId) }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }
}

enum class WidgetType(
    val maxRoutines: Int,
    val layoutId: Int,
    val providerClass: Class<out AppWidgetProvider>,
) {
    ROUTINES(4, R.layout.widget_routines, BranllyPocketWidget::class.java),
    ROUTINES_WITH_CREATE(3, R.layout.widget_routines_with_create, BranllyPocketCreateWidget::class.java),
}

/** Intent metadata kept pure so widget actions remain collision-free and unit-testable. */
data class RoutineWidgetAction(
    val action: String,
    val appWidgetId: Int,
    val routineId: String? = null,
) {
    val requestCode: Int = listOf(action, appWidgetId.toString(), routineId.orEmpty()).joinToString(":").hashCode()
}

object RoutineWidgetIntents {
    const val ACTION_RUN_ROUTINE = "com.branlly.pocket.widget.RUN_ROUTINE"
    const val ACTION_OPEN_CREATE = "com.branlly.pocket.widget.OPEN_CREATE"
    const val EXTRA_ROUTINE_ID = "routine_id"

    fun run(
        appWidgetId: Int,
        routineId: String,
    ): RoutineWidgetAction = RoutineWidgetAction(ACTION_RUN_ROUTINE, appWidgetId, routineId)

    fun openCreate(appWidgetId: Int): RoutineWidgetAction = RoutineWidgetAction(ACTION_OPEN_CREATE, appWidgetId)
}

object RoutineWidgets {
    private val slotIds = intArrayOf(R.id.widget_slot_one, R.id.widget_slot_two, R.id.widget_slot_three, R.id.widget_slot_four)

    suspend fun configure(
        context: Context,
        appWidgetId: Int,
        type: WidgetType,
        routineIds: List<String>,
    ) {
        WidgetPreferences(context).save(appWidgetId, type, routineIds)
        update(context, appWidgetId, type)
    }

    suspend fun update(
        context: Context,
        appWidgetId: Int,
        type: WidgetType,
    ) {
        val available = SavedShortcutStore(context).shortcuts.first()
        val preferences = WidgetPreferences(context)
        val selected = WidgetRoutineResolver.resolve(preferences.routineIds(appWidgetId), available, type.maxRoutines)
        preferences.save(appWidgetId, type, selected.map { it.id.value })
        render(context, appWidgetId, type, selected)
    }

    suspend fun refreshAll(context: Context) {
        WidgetType.entries.forEach { type ->
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, type.providerClass)).forEach { widgetId -> update(context, widgetId, type) }
        }
    }

    suspend fun routineExists(
        context: Context,
        routineId: String,
    ): Boolean = SavedShortcutStore(context).shortcuts.first().any { it.id.value == routineId && it.nodes.isNotEmpty() }

    private fun render(
        context: Context,
        appWidgetId: Int,
        type: WidgetType,
        routines: List<ShortcutDefinition>,
    ) {
        val views = RemoteViews(context.packageName, type.layoutId)
        slotIds.forEachIndexed { index, viewId ->
            val routine = routines.getOrNull(index)
            if (routine == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, "${routine.iconKey.widgetGlyph()}\n${routine.name.widgetLabel()}")
                views.setOnClickPendingIntent(
                    viewId,
                    runPendingIntent(context, type, RoutineWidgetIntents.run(appWidgetId, routine.id.value)),
                )
            }
        }
        if (type == WidgetType.ROUTINES_WITH_CREATE) {
            views.setViewVisibility(R.id.widget_create, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_create,
                createPendingIntent(context, RoutineWidgetIntents.openCreate(appWidgetId)),
            )
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    private fun runPendingIntent(
        context: Context,
        type: WidgetType,
        action: RoutineWidgetAction,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.requestCode,
            Intent(context, type.providerClass)
                .setAction(action.action)
                .setData(Uri.parse("branlly-widget://${action.appWidgetId}/${action.routineId}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, action.appWidgetId)
                .putExtra(RoutineWidgetIntents.EXTRA_ROUTINE_ID, action.routineId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createPendingIntent(
        context: Context,
        action: RoutineWidgetAction,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            action.requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(action.action)
                .setData(Uri.parse("branlly-widget://${action.appWidgetId}/create"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

object WidgetRoutineResolver {
    fun resolve(
        selectedIds: List<String>,
        available: List<ShortcutDefinition>,
        limit: Int,
    ): List<ShortcutDefinition> {
        val byId = available.filter { it.nodes.isNotEmpty() }.associateBy { it.id.value }
        return selectedIds.distinct().mapNotNull(byId::get).take(limit)
    }
}

class WidgetSelectionPolicy(
    private val limit: Int,
) {
    fun toggle(
        selected: List<String>,
        routineId: String,
    ): List<String> =
        when {
            routineId in selected -> selected.filterNot { it == routineId }
            selected.size >= limit -> selected
            else -> selected + routineId
        }
}

object WidgetPreferenceKeys {
    fun routineIds(widgetId: Int): String = "routines_$widgetId"

    fun type(widgetId: Int): String = "type_$widgetId"
}

class WidgetPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun routineIds(widgetId: Int): List<String> =
        preferences
            .getString(WidgetPreferenceKeys.routineIds(widgetId), "")
            .orEmpty()
            .split(',')
            .filter(String::isNotBlank)

    fun save(
        widgetId: Int,
        type: WidgetType,
        routineIds: List<String>,
    ) {
        val selected = routineIds.distinct().take(type.maxRoutines)
        preferences.edit {
            putString(WidgetPreferenceKeys.routineIds(widgetId), selected.joinToString(","))
            putString(WidgetPreferenceKeys.type(widgetId), type.name)
        }
    }

    fun copy(
        fromWidgetId: Int,
        toWidgetId: Int,
    ) {
        val type = preferences.getString(WidgetPreferenceKeys.type(fromWidgetId), WidgetType.ROUTINES.name).orEmpty()
        preferences.edit {
            putString(WidgetPreferenceKeys.routineIds(toWidgetId), preferences.getString(WidgetPreferenceKeys.routineIds(fromWidgetId), ""))
            putString(WidgetPreferenceKeys.type(toWidgetId), type)
        }
    }

    fun delete(widgetId: Int) =
        preferences.edit {
            remove(WidgetPreferenceKeys.routineIds(widgetId))
            remove(WidgetPreferenceKeys.type(widgetId))
        }

    private companion object {
        const val FILE_NAME = "routine_widget_preferences"
    }
}

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

private fun String.widgetLabel(): String = trim().ifBlank { "Routine" }.take(16)
