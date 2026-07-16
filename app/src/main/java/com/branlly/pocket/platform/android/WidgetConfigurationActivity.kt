package com.branlly.pocket.platform.android

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.ui.theme.BranllyPocketTheme

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED)
        setContent {
            BranllyPocketTheme {
                val shortcuts by SavedShortcutStore(applicationContext).shortcuts.collectAsState(initial = emptyList())
                var selectedIds by remember(widgetId) { mutableStateOf(WidgetPreferences(applicationContext).shortcutIds(widgetId)) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Configurer le widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Choisissez jusqu’à six raccourcis. Touchez-les dans l’ordre souhaité.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (shortcuts.isEmpty()) {
                        item {
                            Text("Créez d’abord un raccourci dans Branlly Pocket.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(shortcuts, key = { it.id }) { shortcut ->
                        val selected = shortcut.id.value in selectedIds
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    selectedIds =
                                        when {
                                            selected -> selectedIds - shortcut.id.value
                                            selectedIds.size < MAX_SHORTCUTS -> selectedIds + shortcut.id.value
                                            else -> selectedIds
                                        }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(shortcut.name, fontWeight = FontWeight.Bold)
                                Text(
                                    shortcut.nodes
                                        .firstOrNull()
                                        ?.action
                                        ?.summary() ?: "Aucune action",
                                )
                                if (selected) {
                                    Text(
                                        "Position ${selectedIds.indexOf(shortcut.id.value) + 1}",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                WidgetPreferences(applicationContext).save(
                                    widgetId,
                                    selectedIds.mapNotNull { id -> shortcuts.firstOrNull { it.id.value == id }?.let { id to it.name } },
                                )
                                BranllyPocketWidget.updateWidget(
                                    applicationContext,
                                    AppWidgetManager.getInstance(applicationContext),
                                    widgetId,
                                )
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Ajouter au widget") }
                        TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_SHORTCUTS = 6
    }
}
