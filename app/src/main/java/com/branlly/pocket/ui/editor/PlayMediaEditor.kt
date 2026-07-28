package com.branlly.pocket.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.branlly.pocket.domain.model.MediaErrorStrategy
import com.branlly.pocket.domain.model.MediaSelectionPolicy
import com.branlly.pocket.domain.model.PreferredMediaContentType
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.platform.android.InstalledApplication
import com.branlly.pocket.platform.android.InstalledApplicationCatalog
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun PlayMediaForm(
    action: ShortcutAction.PlayMedia,
    onChange: (ShortcutAction) -> Unit,
) {
    val context = LocalContext.current
    val applications by produceState<List<InstalledApplication>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { InstalledApplicationCatalog(context.applicationContext).load() }
    }
    var appQuery by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    val filtered =
        remember(applications, appQuery) {
            applications.filter { appQuery.isBlank() || it.label.contains(appQuery, true) || it.packageName.contains(appQuery, true) }
        }

    HudSectionHeader("Application multimédia", "Application réelle")
    OutlinedTextField(
        value = appQuery,
        onValueChange = { appQuery = it.take(200) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Rechercher une application") },
        singleLine = true,
    )
    if (appQuery.isNotBlank()) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            items(filtered, key = { "${it.packageName}/${it.activityName}" }) { application ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChange(
                                action.copy(
                                    targetAppLabel = application.label,
                                    targetPackage = application.packageName,
                                    activityName = application.activityName,
                                ),
                            )
                            appQuery = ""
                        }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(application.icon.toBitmap().asImageBitmap(), application.label, Modifier.size(36.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(application.label, fontWeight = FontWeight.Medium)
                        Text(
                            application.packageName,
                            color = HudColors.TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
    if (action.targetPackage.isNotBlank()) {
        HudPanel(borderColor = HudColors.Success.copy(alpha = 0.65f)) {
            Text(action.targetAppLabel, color = HudColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                action.targetPackage,
                color = HudColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        HudPanel(borderColor = HudColors.Warning.copy(alpha = 0.65f)) {
            Text("Choisissez une application.", color = HudColors.Warning)
        }
    }
    OutlinedTextField(
        value = action.searchQuery,
        onValueChange = { onChange(action.copy(searchQuery = it.take(500))) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Recherche") },
        supportingText = {
            if (action.searchQuery.isBlank() &&
                action.mediaUri.isNullOrBlank()
            ) {
                Text("Une recherche ou une URI est obligatoire.")
            }
        },
        isError = action.searchQuery.isBlank() && action.mediaUri.isNullOrBlank(),
        singleLine = true,
    )

    HudPanel(
        modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced },
        borderColor = HudColors.Grid,
    ) {
        HudSectionHeader(
            if (advanced) "Options avancées" else "Afficher les options avancées",
            if (advanced) "Masquer" else "Ouvrir",
        )
    }
    if (advanced) {
        OutlinedTextField(
            value = action.mediaUri.orEmpty(),
            onValueChange = { onChange(action.copy(mediaUri = it.take(500).ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URI ou URL exacte") },
            singleLine = true,
        )
        OutlinedTextField(
            value = action.artist.orEmpty(),
            onValueChange = { onChange(action.copy(artist = it.take(200).ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Artiste facultatif") },
            singleLine = true,
        )
        HudSectionHeader("Type préféré")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PreferredMediaContentType.entries) { type ->
                FilterChip(
                    type == action.preferredContentType,
                    { onChange(action.copy(preferredContentType = type)) },
                    { Text(type.label()) },
                )
            }
        }
        HudSectionHeader("Règle de sélection")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaSelectionPolicy.entries) { policy ->
                FilterChip(policy == action.selectionPolicy, { onChange(action.copy(selectionPolicy = policy)) }, { Text(policy.label()) })
            }
        }
        Text("Timeout : ${action.timeoutMs / 1_000} secondes")
        Slider(
            value = action.timeoutMs / 1_000f,
            onValueChange = { onChange(action.copy(timeoutMs = it.roundToInt().toLong().coerceIn(15, 300) * 1_000)) },
            valueRange = 15f..300f,
        )
        Toggle("Autoriser le fallback manuel", action.allowManualFallback) { onChange(action.copy(allowManualFallback = it)) }
        Toggle("Automatisation avancée — indisponible dans cette phase", false, enabled = false) {}
        HudSectionHeader("Stratégie d’erreur")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaErrorStrategy.entries) { strategy ->
                FilterChip(
                    strategy == action.errorStrategy,
                    { onChange(action.copy(errorStrategy = strategy)) },
                    { Text(strategy.label()) },
                )
            }
        }
    }
}

@Composable
private fun Toggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun PreferredMediaContentType.label() =
    when (this) {
        PreferredMediaContentType.AUTO -> "Automatique"
        PreferredMediaContentType.SONG -> "Morceau"
        PreferredMediaContentType.VIDEO -> "Vidéo"
        PreferredMediaContentType.PLAYLIST -> "Playlist"
        PreferredMediaContentType.PODCAST -> "Podcast"
    }

private fun MediaSelectionPolicy.label() =
    when (this) {
        MediaSelectionPolicy.BEST_PLAYABLE_MATCH -> "Meilleure correspondance"
        MediaSelectionPolicy.FIRST_PLAYABLE -> "Premier résultat jouable"
        MediaSelectionPolicy.EXACT_MATCH -> "Correspondance exacte"
        MediaSelectionPolicy.ASK_USER -> "Demander"
    }

private fun MediaErrorStrategy.label() =
    when (this) {
        MediaErrorStrategy.TRY_NEXT_STRATEGY -> "Essayer la suivante"
        MediaErrorStrategy.STOP_ON_FIRST_FAILURE -> "Arrêter au premier échec"
    }
