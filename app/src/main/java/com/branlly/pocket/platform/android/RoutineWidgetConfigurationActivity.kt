package com.branlly.pocket.platform.android

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudPrimaryButton
import com.branlly.pocket.ui.hud.HudSecondaryButton
import com.branlly.pocket.ui.hud.HudSurfaceTheme
import com.branlly.pocket.ui.theme.BranllyPocketTheme
import kotlinx.coroutines.launch

class RoutineWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED)
        val type = widgetType(widgetId)
        setContent {
            BranllyPocketTheme {
                HudSurfaceTheme {
                    RoutineWidgetConfigurationScreen(
                        widgetId = widgetId,
                        type = type,
                        onFinished = { finish() },
                    )
                }
            }
        }
    }

    private fun widgetType(widgetId: Int): WidgetType {
        val provider =
            AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(widgetId)
                ?.provider
                ?.className
        return WidgetType.entries.firstOrNull { it.providerClass.name == provider } ?: WidgetType.ROUTINES
    }

    private fun finishConfiguration(widgetId: Int) {
        setResult(
            Activity.RESULT_OK,
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun RoutineWidgetConfigurationScreen(
        widgetId: Int,
        type: WidgetType,
        onFinished: () -> Unit,
    ) {
        val available by SavedShortcutStore(applicationContext).shortcuts.collectAsState(initial = emptyList())
        val selectable = available.filter { it.nodes.isNotEmpty() }
        val selected =
            remember { mutableStateListOf<String>().apply { addAll(WidgetPreferences(applicationContext).routineIds(widgetId)) } }
        val scope = rememberCoroutineScope()
        val policy = remember(type) { WidgetSelectionPolicy(type.maxRoutines) }

        Column(
            modifier = Modifier.fillMaxSize().background(HudColors.Background).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (type == WidgetType.ROUTINES) "ROUTINES" else "ROUTINES + CRÉER",
                color = HudColors.CyanBright,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Sélectionnez jusqu’à ${type.maxRoutines} routines dans l’ordre souhaité.",
                color = HudColors.TextSecondary,
            )
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectable, key = { it.id.value }) { routine ->
                    WidgetRoutineOption(routine, selected.indexOf(routine.id.value) + 1) {
                        val updated = policy.toggle(selected, routine.id.value)
                        selected.clear()
                        selected.addAll(updated)
                    }
                }
            }
            HudPrimaryButton(
                text = "Enregistrer",
                onClick = {
                    scope.launch {
                        RoutineWidgets.configure(applicationContext, widgetId, type, selected)
                        finishConfiguration(widgetId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                showLeadingGlyph = false,
            )
            HudSecondaryButton(
                text = "Annuler",
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetRoutineOption(
        routine: ShortcutDefinition,
        selectedPosition: Int,
        onClick: () -> Unit,
    ) {
        HudPanel(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            borderColor = if (selectedPosition > 0) HudColors.CyanBright else HudColors.Grid,
        ) {
            Text(
                text = if (selectedPosition > 0) "$selectedPosition  ${routine.name}" else routine.name,
                color = HudColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
