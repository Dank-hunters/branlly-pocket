package com.branlly.pocket.ui.editor

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.TransportMode
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.platform.android.InstalledApplication
import com.branlly.pocket.platform.android.InstalledApplicationCatalog
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
import com.branlly.pocket.platform.android.actions.BuiltInProviderCatalog
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
    val context = LocalContext.current
    val registry = remember(context) { AndroidActionRegistry.create(context.applicationContext) }
    val registration = registry.registration(node.action.kind)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Configurer l’action", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(registry.summary(node.action), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            if (registration == null || !ActionEditorRegistry.hasProvider(registration.editorKey)) {
                Text("Cette action existe dans les données mais n’est pas configurable ni exécutable dans cette version.")
            } else {
                ActionEditorRegistry.Render(registration.editorKey, node.action, onActionChange)
            }
        }
    }
}

@Composable
internal fun ApplicationForm(
    action: ShortcutAction.OpenApplication,
    onChange: (ShortcutAction) -> Unit,
) {
    val context = LocalContext.current
    val applications by produceState<List<InstalledApplication>>(initialValue = emptyList(), context) {
        value =
            withContext(Dispatchers.IO) {
                InstalledApplicationCatalog(context.applicationContext).load()
            }
    }
    var query by remember { mutableStateOf("") }
    val selectedPackage = (action.packageName as? InputValue.Fixed<String>)?.value
    val mode = if (action.packageName is InputValue.Fixed) ValueMode.FIXED else ValueMode.ASK_AT_RUNTIME
    val filtered =
        remember(applications, query) {
            val normalized = query.trim()
            if (normalized.isEmpty()) {
                applications
            } else {
                applications.filter {
                    it.label.contains(normalized, ignoreCase = true) || it.packageName.contains(normalized, ignoreCase = true)
                }
            }
        }

    ChoiceRow(
        label = "Application",
        choices = ValueMode.entries,
        selected = mode,
        text = { if (it == ValueMode.FIXED) "Toujours utiliser" else "Demander au lancement" },
        onSelected = { selectedMode ->
            val value: InputValue<String> =
                if (selectedMode == ValueMode.FIXED) {
                    selectedPackage?.let { InputValue.Fixed(it) } ?: InputValue.AskAtRuntime
                } else {
                    InputValue.AskAtRuntime
                }
            onChange(action.copy(packageName = value))
        },
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(MAX_SEARCH_LENGTH) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Rechercher par nom ou package") },
        singleLine = true,
    )
    if (query.isNotBlank()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            items(filtered, key = { application -> "${application.packageName}/${application.activityName}" }) { application ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Log.i(
                                "BranllyApplication",
                                "APPLICATION_SELECTED label=${application.label} package=${application.packageName}",
                            )
                            onChange(
                                action.copy(
                                    packageName = InputValue.Fixed(application.packageName),
                                    applicationLabel = application.label,
                                    activityName = application.activityName,
                                ),
                            )
                            query = ""
                        }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        application.icon.toBitmap().asImageBitmap(),
                        contentDescription = application.label,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(application.label, fontWeight = FontWeight.Medium)
                        Text(application.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (mode == ValueMode.FIXED) {
        OutlinedTextField(
            value = action.applicationLabel.orEmpty(),
            onValueChange = { value -> onChange(action.copy(applicationLabel = value.take(MAX_SEARCH_LENGTH).ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nom visible de l’application") },
            singleLine = true,
        )
        OutlinedTextField(
            value = selectedPackage.orEmpty(),
            onValueChange = { value ->
                onChange(action.copy(packageName = InputValue.Fixed(value.trim()), activityName = null))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Package Android exact") },
            supportingText = { Text("Sélectionnez une application ci-dessous ; ce champ permet de vérifier la valeur sauvegardée.") },
            singleLine = true,
        )
    }
    if (selectedPackage != null) {
        val selectedApplication = applications.firstOrNull { it.packageName == selectedPackage }
        Surface(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                selectedApplication?.let { application ->
                    Image(
                        application.icon.toBitmap().asImageBitmap(),
                        contentDescription = application.label,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        selectedApplication?.label ?: action.applicationLabel ?: "Application cible non définie",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(selectedPackage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val title = (action.searchQuery as? InputValue.Fixed<String>)?.value.orEmpty()
        OutlinedTextField(
            value = title,
            onValueChange = { value ->
                val normalized = value.take(MAX_MEDIA_SEARCH_LENGTH)
                onChange(action.copy(searchQuery = normalized.takeIf(String::isNotBlank)?.let { InputValue.Fixed(it) }))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Titre à rechercher (facultatif)") },
            supportingText = { Text("L’application choisie ouvrira sa recherche si elle la prend en charge.") },
            singleLine = true,
        )
        val mediaLink = (action.mediaUri as? InputValue.Fixed<String>)?.value.orEmpty()
        OutlinedTextField(
            value = mediaLink,
            onValueChange = { value ->
                val normalized = value.take(MAX_MEDIA_LINK_LENGTH)
                onChange(action.copy(mediaUri = normalized.takeIf(String::isNotBlank)?.let { InputValue.Fixed(it) }))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Lien du titre exact (facultatif)") },
            supportingText = { Text("Copiez un lien HTTPS partagé par l’application multimédia. Il est prioritaire sur la recherche.") },
            singleLine = true,
        )
    }
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
                items(filtered, key = { application -> "${application.packageName}/${application.activityName}" }) { application ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Log.i(
                                        "BranllyApplication",
                                        "APPLICATION_SELECTED label=${application.label} package=${application.packageName}",
                                    )
                                    onChange(
                                        action.copy(
                                            packageName = InputValue.Fixed(application.packageName),
                                            applicationLabel = application.label,
                                            activityName = application.activityName,
                                        ),
                                    )
                                },
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(12.dp),
                        color =
                            if (application.packageName == selectedPackage) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                application.icon.toBitmap().asImageBitmap(),
                                contentDescription = application.label,
                                modifier = Modifier.size(40.dp),
                            )
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(application.label, fontWeight = FontWeight.Medium)
                                Text(
                                    application.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MediaWaitForm(
    action: ShortcutAction.WaitForMediaPlayback,
    onChange: (ShortcutAction) -> Unit,
) {
    val context = LocalContext.current
    val applications by produceState<List<InstalledApplication>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { InstalledApplicationCatalog(context.applicationContext).load() }
    }
    var query by remember { mutableStateOf("") }
    val selectedPackage = (action.packageName as? InputValue.Fixed<String>)?.value.orEmpty()
    val selected = applications.firstOrNull { it.packageName == selectedPackage }
    Text(selected?.label ?: action.applicationLabel ?: "Application cible non définie", fontWeight = FontWeight.SemiBold)
    Text(selectedPackage.ifBlank { "Sélectionnez une application" }, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = action.applicationLabel.orEmpty(),
        onValueChange = { value -> onChange(action.copy(applicationLabel = value.take(MAX_SEARCH_LENGTH).ifBlank { null })) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nom visible de l’application") },
        singleLine = true,
    )
    OutlinedTextField(
        value = selectedPackage,
        onValueChange = { value -> onChange(action.copy(packageName = InputValue.Fixed(value.trim().take(MAX_SEARCH_LENGTH)))) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Package Android exact") },
        singleLine = true,
    )
    OutlinedTextField(
        value = (action.timeoutMillis / 1_000L).toString(),
        onValueChange = {
            it.toLongOrNull()?.let { seconds ->
                onChange(action.copy(timeoutMillis = (seconds * 1_000L).coerceIn(1_000L, 300_000L)))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Timeout (secondes)") },
        singleLine = true,
    )
    OutlinedTextField(value = query, onValueChange = {
        query = it.take(MAX_SEARCH_LENGTH)
    }, modifier = Modifier.fillMaxWidth(), label = { Text("Rechercher par nom ou package") }, singleLine = true)
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
        items(
            applications.filter {
                query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
            },
            key = { application -> "${application.packageName}/${application.activityName}" },
        ) { application ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        Log.i("BranllyApplication", "APPLICATION_SELECTED label=${application.label} package=${application.packageName}")
                        onChange(
                            action.copy(
                                packageName = InputValue.Fixed(application.packageName),
                                applicationLabel = application.label,
                            ),
                        )
                    }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(application.icon.toBitmap().asImageBitmap(), contentDescription = application.label, modifier = Modifier.size(36.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(application.label, fontWeight = FontWeight.Medium)
                    Text(application.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private const val MAX_SEARCH_LENGTH = 80
private const val MAX_MEDIA_SEARCH_LENGTH = 120
private const val MAX_MEDIA_LINK_LENGTH = 2_000
private const val MAX_ROUTE_DESTINATION_LENGTH = 500

@Composable
internal fun VolumeForm(
    action: ShortcutAction.SetVolume,
    onChange: (ShortcutAction) -> Unit,
) {
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
}

@Composable
internal fun BrightnessForm(
    action: ShortcutAction.SetBrightness,
    onChange: (ShortcutAction) -> Unit,
) {
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
internal fun WaitForm(
    action: ShortcutAction.Wait,
    onChange: (ShortcutAction) -> Unit,
) {
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
}

@Composable
internal fun SoundModeForm(
    action: ShortcutAction.SetSoundMode,
    onChange: (ShortcutAction) -> Unit,
) {
    ChoiceRow(
        label = "Mode sonore",
        choices = SoundMode.entries,
        selected = action.mode,
        text = { it.label() },
        onSelected = { onChange(action.copy(mode = it)) },
    )
}

@Composable
internal fun SettingsForm(
    action: ShortcutAction.OpenSettings,
    onChange: (ShortcutAction) -> Unit,
) {
    ChoiceRow(
        label = "Écran de réglages",
        choices = SettingsPanel.entries,
        selected = action.panel,
        text = { it.label() },
        onSelected = { onChange(action.copy(panel = it)) },
    )
}

@Composable
internal fun RouteForm(
    action: ShortcutAction.OpenRoute,
    onChange: (ShortcutAction) -> Unit,
) {
    val context = LocalContext.current
    val validation = remember(context) { AndroidActionValidationContext(context.applicationContext) }
    val providers = BuiltInProviderCatalog.navigationProviders
    val selectedPackage =
        (action.navigationPackage as? InputValue.Fixed<String>)?.value
            ?: providers.first().packageName
    val selectedProvider = providers.firstOrNull { it.packageName == selectedPackage }
    val destination = (action.destination as? InputValue.Fixed<String>)?.value.orEmpty()
    val destinationMode = if (action.destination is InputValue.Fixed) ValueMode.FIXED else ValueMode.ASK_AT_RUNTIME

    ChoiceRow(
        label = "Application de navigation",
        choices = providers,
        selected = selectedProvider ?: providers.first(),
        text = { provider -> if (validation.isPackageInstalled(provider.packageName)) provider.label else "${provider.label} · non installée" },
        enabled = { provider -> validation.isPackageInstalled(provider.packageName) },
        onSelected = { provider ->
            onChange(
                action.copy(
                    navigationPackage = InputValue.Fixed(provider.packageName),
                    transportMode = action.transportMode.takeIf { it in provider.supportedModes } ?: provider.supportedModes.first(),
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
                if (value.length <= MAX_ROUTE_DESTINATION_LENGTH) {
                    onChange(action.copy(destination = InputValue.Fixed(value)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Adresse ou lieu") },
            supportingText = { Text("Exemple : Gare de Lyon, Paris") },
            singleLine = true,
        )
    }
    val supportedModes = selectedProvider?.supportedModes.orEmpty()
    if (supportedModes.size > 1) {
        ChoiceRow(
            label = "Mode de transport",
            choices = supportedModes.toList(),
            selected = action.transportMode,
            text = { it.label() },
            onSelected = { onChange(action.copy(transportMode = it)) },
        )
    } else if (supportedModes.singleOrNull() == TransportMode.DRIVING) {
        Text("Ce fournisseur utilise le mode voiture.", style = MaterialTheme.typography.bodySmall)
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

private enum class PercentageMode { FIXED, ASK_AT_RUNTIME }

private enum class ValueMode { FIXED, ASK_AT_RUNTIME }

private fun VolumeStream.label() =
    when (this) {
        VolumeStream.MEDIA -> "Multimédia"
        VolumeStream.RING -> "Sonnerie"
        VolumeStream.ALARM -> "Alarme"
    }

private fun SoundMode.label() =
    when (this) {
        SoundMode.NORMAL -> "Normal"
        SoundMode.VIBRATE -> "Vibreur"
        SoundMode.SILENT -> "Silencieux"
        SoundMode.DO_NOT_DISTURB -> "Ne pas déranger"
    }

private fun TransportMode.label() =
    when (this) {
        TransportMode.DRIVING -> "Voiture"
        TransportMode.WALKING -> "À pied"
        TransportMode.BICYCLING -> "Vélo"
        TransportMode.TRANSIT -> "Transports"
    }

private fun SettingsPanel.label() =
    when (this) {
        SettingsPanel.BLUETOOTH -> "Bluetooth"
        SettingsPanel.WIFI -> "Wi-Fi"
        SettingsPanel.BATTERY -> "Batterie"
        SettingsPanel.DISPLAY -> "Affichage"
        SettingsPanel.SOUND -> "Son"
    }
