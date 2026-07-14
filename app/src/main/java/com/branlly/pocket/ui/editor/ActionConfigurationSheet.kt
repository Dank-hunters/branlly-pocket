package com.branlly.pocket.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.TransportMode
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.platform.android.InstalledApplication
import com.branlly.pocket.platform.android.InstalledApplicationCatalog
import com.branlly.pocket.platform.android.NavigationApps
import com.branlly.pocket.platform.android.RouteLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigurationSheet(
    node: ActionNode,
    onActionChange: (ShortcutAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Configurer l’action", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(node.action.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            when (val action = node.action) {
                is ShortcutAction.OpenApplication -> ApplicationForm(action, onActionChange)
                is ShortcutAction.SetVolume -> VolumeForm(action, onActionChange)
                is ShortcutAction.SetBrightness -> BrightnessForm(action, onActionChange)
                is ShortcutAction.Wait -> WaitForm(action, onActionChange)
                is ShortcutAction.SetSoundMode -> SoundModeForm(action, onActionChange)
                is ShortcutAction.OpenSettings -> SettingsForm(action, onActionChange)
                is ShortcutAction.OpenRoute -> RouteForm(action, onActionChange)
                else -> Text(
                    "Le formulaire détaillé de cette action sera ajouté dans la prochaine étape.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ApplicationForm(action: ShortcutAction.OpenApplication, onChange: (ShortcutAction) -> Unit) {
    val context = LocalContext.current
    val applications by produceState<List<InstalledApplication>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            InstalledApplicationCatalog(context.applicationContext).load()
        }
    }
    var query by remember { mutableStateOf("") }
    val selectedPackage = (action.packageName as? InputValue.Fixed<String>)?.value
    val mode = if (action.packageName is InputValue.Fixed) ValueMode.FIXED else ValueMode.ASK_AT_RUNTIME
    val filtered = remember(applications, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) applications else applications.filter {
            it.label.contains(normalized, ignoreCase = true)
        }
    }

    ChoiceRow(
        label = "Application",
        choices = ValueMode.entries,
        selected = mode,
        text = { if (it == ValueMode.FIXED) "Toujours utiliser" else "Demander au lancement" },
        onSelected = { selectedMode ->
            val value: InputValue<String> = if (selectedMode == ValueMode.FIXED) {
                selectedPackage?.let { InputValue.Fixed(it) } ?: InputValue.AskAtRuntime
            } else {
                InputValue.AskAtRuntime
            }
            onChange(action.copy(packageName = value))
        },
    )
    if (mode == ValueMode.FIXED || selectedPackage == null) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(MAX_SEARCH_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Rechercher une application") },
            singleLine = true,
        )
        if (applications.isEmpty()) {
            Text("Chargement des applications…", style = MaterialTheme.typography.bodySmall)
        } else if (filtered.isEmpty()) {
            Text("Aucune application correspondante.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = InstalledApplication::packageName) { application ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChange(action.copy(packageName = InputValue.Fixed(application.packageName))) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = if (application.packageName == selectedPackage) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(application.label, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private const val MAX_SEARCH_LENGTH = 80

@Composable
private fun VolumeForm(action: ShortcutAction.SetVolume, onChange: (ShortcutAction) -> Unit) {
    ChoiceRow(
        label = "Type de volume",
        choices = VolumeStream.entries,
        selected = action.stream,
        text = { it.label() },
        onSelected = { onChange(action.copy(stream = it)) },
    )
    PercentageInput(
        label = "Niveau",
        value = action.percent,
        onChange = { onChange(action.copy(percent = it)) },
    )
    ToggleRow(
        label = "Restaurer le volume après l’exécution",
        checked = action.restoreAfterExecution,
        onCheckedChange = { onChange(action.copy(restoreAfterExecution = it)) },
    )
}

@Composable
private fun BrightnessForm(action: ShortcutAction.SetBrightness, onChange: (ShortcutAction) -> Unit) {
    PercentageInput(
        label = "Niveau de luminosité",
        value = action.percent,
        onChange = { onChange(action.copy(percent = it)) },
    )
}

@Composable
private fun PercentageInput(
    label: String,
    value: InputValue<Int>,
    onChange: (InputValue<Int>) -> Unit,
) {
    val fixedValue = (value as? InputValue.Fixed<Int>)?.value?.coerceIn(0, 100) ?: 50
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        ChoiceRow(
            label = null,
            choices = PercentageMode.entries,
            selected = if (value is InputValue.Fixed) PercentageMode.FIXED else PercentageMode.ASK_AT_RUNTIME,
            text = { if (it == PercentageMode.FIXED) "Valeur fixe" else "Demander au lancement" },
            onSelected = { mode ->
                onChange(if (mode == PercentageMode.FIXED) InputValue.Fixed(fixedValue) else InputValue.AskAtRuntime)
            },
        )
        if (value is InputValue.Fixed) {
            Text("${value.value.coerceIn(0, 100)} %", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = value.value.coerceIn(0, 100).toFloat(),
                onValueChange = { onChange(InputValue.Fixed(it.roundToInt())) },
                valueRange = 0f..100f,
                steps = 19,
            )
        }
    }
}

@Composable
private fun WaitForm(action: ShortcutAction.Wait, onChange: (ShortcutAction) -> Unit) {
    val seconds = (action.durationMillis / 1_000f).coerceIn(1f, 300f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Durée", fontWeight = FontWeight.SemiBold)
        Text("${seconds.roundToInt()} secondes", style = MaterialTheme.typography.titleLarge)
        Slider(
            value = seconds,
            onValueChange = { onChange(action.copy(durationMillis = it.roundToInt() * 1_000L)) },
            valueRange = 1f..300f,
            steps = 298,
        )
    }
    ToggleRow(
        label = "Permettre l’annulation pendant l’attente",
        checked = action.cancellable,
        onCheckedChange = { onChange(action.copy(cancellable = it)) },
    )
}

@Composable
private fun SoundModeForm(action: ShortcutAction.SetSoundMode, onChange: (ShortcutAction) -> Unit) {
    ChoiceRow(
        label = "Mode sonore",
        choices = SoundMode.entries,
        selected = action.mode,
        text = { it.label() },
        onSelected = { onChange(action.copy(mode = it)) },
    )
}

@Composable
private fun SettingsForm(action: ShortcutAction.OpenSettings, onChange: (ShortcutAction) -> Unit) {
    ChoiceRow(
        label = "Écran de réglages",
        choices = SettingsPanel.entries,
        selected = action.panel,
        text = { it.label() },
        onSelected = { onChange(action.copy(panel = it)) },
    )
}

@Composable
private fun RouteForm(action: ShortcutAction.OpenRoute, onChange: (ShortcutAction) -> Unit) {
    val context = LocalContext.current
    val launcher = RouteLauncher(context)
    val selectedPackage = (action.navigationPackage as? InputValue.Fixed<String>)?.value
        ?: NavigationApps.GOOGLE_MAPS
    val destination = (action.destination as? InputValue.Fixed<String>)?.value.orEmpty()
    val destinationMode = if (action.destination is InputValue.Fixed) ValueMode.FIXED else ValueMode.ASK_AT_RUNTIME

    ChoiceRow(
        label = "Application de navigation",
        choices = NavigationApps.supportedPackages.toList(),
        selected = selectedPackage,
        text = { packageName ->
            val name = if (packageName == NavigationApps.WAZE) "Waze" else "Google Maps"
            if (launcher.isInstalled(packageName)) name else "$name · non installée"
        },
        enabled = launcher::isInstalled,
        onSelected = { packageName ->
            onChange(
                action.copy(
                    navigationPackage = InputValue.Fixed(packageName),
                    transportMode = if (packageName == NavigationApps.WAZE) TransportMode.DRIVING else action.transportMode,
                ),
            )
        },
    )
    ChoiceRow(
        label = "Destination",
        choices = ValueMode.entries,
        selected = destinationMode,
        text = { if (it == ValueMode.FIXED) "Toujours utiliser" else "Demander au lancement" },
        onSelected = { mode ->
            onChange(action.copy(destination = if (mode == ValueMode.FIXED) InputValue.Fixed(destination) else InputValue.AskAtRuntime))
        },
    )
    if (action.destination is InputValue.Fixed) {
        OutlinedTextField(
            value = destination,
            onValueChange = { value ->
                if (value.length <= NavigationApps.MAX_DESTINATION_LENGTH) {
                    onChange(action.copy(destination = InputValue.Fixed(value)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Adresse ou lieu") },
            supportingText = { Text("Exemple : Gare de Lyon, Paris") },
            singleLine = true,
        )
    }
    if (selectedPackage == NavigationApps.GOOGLE_MAPS) {
        ChoiceRow(
            label = "Mode de transport",
            choices = TransportMode.entries,
            selected = action.transportMode,
            text = { it.label() },
            onSelected = { onChange(action.copy(transportMode = it)) },
        )
    } else {
        Text("Waze utilisera le mode voiture.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String?,
    choices: List<T>,
    selected: T,
    text: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices) { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelected(choice) },
                    enabled = enabled(choice),
                    label = { Text(text(choice)) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private enum class PercentageMode { FIXED, ASK_AT_RUNTIME }
private enum class ValueMode { FIXED, ASK_AT_RUNTIME }

private fun VolumeStream.label() = when (this) {
    VolumeStream.MEDIA -> "Multimédia"
    VolumeStream.RING -> "Sonnerie"
    VolumeStream.ALARM -> "Alarme"
}

private fun SoundMode.label() = when (this) {
    SoundMode.NORMAL -> "Normal"
    SoundMode.VIBRATE -> "Vibreur"
    SoundMode.SILENT -> "Silencieux"
    SoundMode.DO_NOT_DISTURB -> "Ne pas déranger"
}

private fun TransportMode.label() = when (this) {
    TransportMode.DRIVING -> "Voiture"
    TransportMode.WALKING -> "À pied"
    TransportMode.BICYCLING -> "Vélo"
    TransportMode.TRANSIT -> "Transports"
}

private fun SettingsPanel.label() = when (this) {
    SettingsPanel.BLUETOOTH -> "Bluetooth"
    SettingsPanel.WIFI -> "Wi-Fi"
    SettingsPanel.BATTERY -> "Batterie"
    SettingsPanel.DISPLAY -> "Affichage"
    SettingsPanel.SOUND -> "Son"
}
