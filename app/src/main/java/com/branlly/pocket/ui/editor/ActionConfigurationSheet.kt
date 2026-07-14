package com.branlly.pocket.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.domain.model.summary
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
                is ShortcutAction.SetVolume -> VolumeForm(action, onActionChange)
                is ShortcutAction.SetBrightness -> BrightnessForm(action, onActionChange)
                is ShortcutAction.Wait -> WaitForm(action, onActionChange)
                is ShortcutAction.SetSoundMode -> SoundModeForm(action, onActionChange)
                is ShortcutAction.OpenSettings -> SettingsForm(action, onActionChange)
                else -> Text(
                    "Le formulaire détaillé de cette action sera ajouté dans la prochaine étape.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

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
private fun <T> ChoiceRow(
    label: String?,
    choices: List<T>,
    selected: T,
    text: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices) { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelected(choice) },
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

private fun SettingsPanel.label() = when (this) {
    SettingsPanel.BLUETOOTH -> "Bluetooth"
    SettingsPanel.WIFI -> "Wi-Fi"
    SettingsPanel.BATTERY -> "Batterie"
    SettingsPanel.DISPLAY -> "Affichage"
    SettingsPanel.SOUND -> "Son"
}
