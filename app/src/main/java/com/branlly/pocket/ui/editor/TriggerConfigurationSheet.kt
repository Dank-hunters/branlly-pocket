package com.branlly.pocket.ui.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.domain.model.ChargerEvent
import com.branlly.pocket.domain.model.ConnectionEvent
import com.branlly.pocket.domain.model.NumericComparison
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.platform.android.PairedBluetoothCatalog
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerConfigurationSheet(
    trigger: Trigger,
    onTriggerChange: (Trigger) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Configurer le déclencheur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            when (trigger) {
                is Trigger.Time -> TimeTriggerForm(trigger, onTriggerChange)
                is Trigger.Bluetooth -> BluetoothTriggerForm(trigger, onTriggerChange)
                is Trigger.Wifi -> WifiTriggerForm(trigger, onTriggerChange)
                is Trigger.Charger -> ChargerTriggerForm(trigger, onTriggerChange)
                is Trigger.BatteryLevel -> BatteryTriggerForm(trigger, onTriggerChange)
                is Trigger.Nfc -> Text("Le tag NFC sera associé lors de l’enregistrement du raccourci.")
                Trigger.ManualButton -> Text("Ce raccourci sera lancé uniquement depuis son bouton.")
                Trigger.Widget -> Text("Le widget sera proposé après l’enregistrement.")
                Trigger.QuickTile -> Text("La tuile rapide sera proposée après l’enregistrement.")
            }
        }
    }
}

@Composable
private fun TimeTriggerForm(trigger: Trigger.Time, onChange: (Trigger) -> Unit) {
    NumberSlider("Heure", trigger.time.hour, 0..23) { hour ->
        onChange(trigger.copy(time = trigger.time.withHour(hour)))
    }
    NumberSlider("Minutes", trigger.time.minute, 0..59) { minute ->
        onChange(trigger.copy(time = trigger.time.withMinute(minute)))
    }
    Text("Jours", fontWeight = FontWeight.SemiBold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DayOfWeek.entries) { day ->
            FilterChip(
                selected = day in trigger.days,
                onClick = {
                    val days = if (day in trigger.days) trigger.days - day else trigger.days + day
                    if (days.isNotEmpty()) onChange(trigger.copy(days = days))
                },
                label = { Text(day.shortLabel()) },
            )
        }
    }
    Text(
        "Lancement à ${trigger.time.hour.toString().padStart(2, '0')}:${trigger.time.minute.toString().padStart(2, '0')}",
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun BluetoothTriggerForm(trigger: Trigger.Bluetooth, onChange: (Trigger) -> Unit) {
    val context = LocalContext.current
    val catalog = remember(context) { PairedBluetoothCatalog(context.applicationContext) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh += 1
    }
    val devices = remember(permissionRefresh) { catalog.load() }

    Text("Appareil associé", fontWeight = FontWeight.SemiBold)
    if (!catalog.hasPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
            Text("Autoriser l’accès aux appareils associés")
        }
    } else if (devices.isEmpty()) {
        Text("Aucun appareil Bluetooth associé trouvé.", style = MaterialTheme.typography.bodySmall)
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(devices, key = { it.address }) { device ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onChange(trigger.copy(deviceAddress = device.address, deviceName = device.name))
                    },
                    color = if (device.address == trigger.deviceAddress) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Text(device.name, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
    ChoiceRow("Événement", ConnectionEvent.entries, trigger.event, ConnectionEvent::label) {
        onChange(trigger.copy(event = it))
    }
    NumberSlider("Délai avant lancement (secondes)", (trigger.delayMillis / 1_000).toInt(), 0..60) {
        onChange(trigger.copy(delayMillis = it * 1_000L))
    }
    ToggleRow("Demander une confirmation", trigger.requiresConfirmation) {
        onChange(trigger.copy(requiresConfirmation = it))
    }
}

@Composable
private fun WifiTriggerForm(trigger: Trigger.Wifi, onChange: (Trigger) -> Unit) {
    OutlinedTextField(
        value = trigger.ssid,
        onValueChange = { if (it.length <= 32) onChange(trigger.copy(ssid = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nom du réseau Wi-Fi") },
        singleLine = true,
    )
    ChoiceRow("Événement", ConnectionEvent.entries, trigger.event, ConnectionEvent::label) {
        onChange(trigger.copy(event = it))
    }
}

@Composable
private fun ChargerTriggerForm(trigger: Trigger.Charger, onChange: (Trigger) -> Unit) {
    ChoiceRow("Événement", ChargerEvent.entries, trigger.event, ChargerEvent::label) {
        onChange(trigger.copy(event = it))
    }
}

@Composable
private fun BatteryTriggerForm(trigger: Trigger.BatteryLevel, onChange: (Trigger) -> Unit) {
    ChoiceRow("Condition", NumericComparison.entries, trigger.comparison, NumericComparison::label) {
        onChange(trigger.copy(comparison = it))
    }
    NumberSlider("Niveau de batterie", trigger.thresholdPercent, 1..100) {
        onChange(trigger.copy(thresholdPercent = it))
    }
}

@Composable
private fun NumberSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text("$label : $value", fontWeight = FontWeight.SemiBold)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun <T> ChoiceRow(label: String, choices: List<T>, selected: T, text: (T) -> String, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
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

private fun DayOfWeek.shortLabel() = when (this) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mer"
    DayOfWeek.THURSDAY -> "Jeu"
    DayOfWeek.FRIDAY -> "Ven"
    DayOfWeek.SATURDAY -> "Sam"
    DayOfWeek.SUNDAY -> "Dim"
}

private fun ConnectionEvent.label() = when (this) {
    ConnectionEvent.CONNECTED -> "Connexion"
    ConnectionEvent.DISCONNECTED -> "Déconnexion"
    ConnectionEvent.BOTH -> "Les deux"
}

private fun ChargerEvent.label() = when (this) {
    ChargerEvent.PLUGGED -> "Branché"
    ChargerEvent.UNPLUGGED -> "Débranché"
    ChargerEvent.BOTH -> "Les deux"
}

private fun NumericComparison.label() = when (this) {
    NumericComparison.LESS_THAN -> "Inférieure à"
    NumericComparison.LESS_OR_EQUAL -> "Inférieure ou égale à"
    NumericComparison.EQUAL -> "Égale à"
    NumericComparison.GREATER_OR_EQUAL -> "Supérieure ou égale à"
    NumericComparison.GREATER_THAN -> "Supérieure à"
}
