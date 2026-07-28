package com.branlly.pocket.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.catalog.visibleDescriptors
import com.branlly.pocket.domain.execution.RoutineValidator
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.EditorMode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.platform.android.ShortcutExecutor
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
import com.branlly.pocket.ui.editor.ActionConfigurationSheet
import com.branlly.pocket.ui.editor.EditorUiState
import com.branlly.pocket.ui.editor.EditorViewModel
import com.branlly.pocket.ui.editor.PresentationPickerSheet
import com.branlly.pocket.ui.editor.Screen
import com.branlly.pocket.ui.editor.TriggerConfigurationSheet
import com.branlly.pocket.ui.editor.toComposeColor
import com.branlly.pocket.ui.hud.HudChoiceCard
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudCutCornerShape
import com.branlly.pocket.ui.hud.HudIconContainer
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudPrimaryButton
import com.branlly.pocket.ui.hud.HudSecondaryButton
import com.branlly.pocket.ui.hud.HudSectionHeader
import com.branlly.pocket.ui.hud.HudSpacing
import com.branlly.pocket.ui.hud.HudStatusBadge
import com.branlly.pocket.ui.hud.HudSurfaceTheme
import com.branlly.pocket.ui.hud.HudValidationMessage
import com.branlly.pocket.ui.voice.VoiceCommandControl

@Composable
fun BranllyPocketApp(
    sharedMediaLink: String? = null,
    viewModel: EditorViewModel = viewModel(),
) {
    LaunchedEffect(sharedMediaLink) {
        sharedMediaLink?.let(viewModel::receiveSharedMediaLink)
    }
    val state by viewModel.state.collectAsState()
    when (state.screen) {
        Screen.HOME -> HudHomeScreen(state, viewModel)
        Screen.START -> HudSurfaceTheme { StartScreen(viewModel) }
        Screen.GUIDED_TRIGGER -> HudSurfaceTheme { TriggerScreen(viewModel) }
        Screen.ACTION_CHOICE -> HudSurfaceTheme { ActionChoiceScreen(viewModel) }
        Screen.BLUEPRINTS -> HudSurfaceTheme { BlueprintScreen(viewModel) }
        Screen.EDITOR -> HudSurfaceTheme { EditorScreen(state, viewModel) }
    }
}

@Composable
private fun HomeScreen(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val context = LocalContext.current
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { selected ->
                runCatching {
                    context.contentResolver
                        .openInputStream(selected)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                }.onSuccess(viewModel::importRoutine)
                    .onFailure { viewModel.importRoutine("") }
            }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "B",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Branlly Pocket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Vos raccourcis, sans détour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.size(40.dp).clickable { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("⇩", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
        state.message?.let { message ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::clearMessage),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(message, modifier = Modifier.padding(14.dp))
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = viewModel::showStart, shape = RoundedCornerShape(18.dp)) {
                    Text("＋  Nouveau raccourci")
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text("◉", color = MaterialTheme.colorScheme.primary) }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Commande vocale locale", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Dites « musique » ou « navigation »",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VoiceCommandControl { command ->
                        val saved =
                            when (command) {
                                LocalVoiceCommand.NAVIGATION -> {
                                    state.savedShortcuts.firstOrNull { shortcut ->
                                        shortcut.nodes.any { it.enabled && it.action is ShortcutAction.OpenRoute }
                                    }
                                }

                                LocalVoiceCommand.MUSIC -> {
                                    state.savedShortcuts.firstOrNull { shortcut ->
                                        shortcut.nodes.any { it.enabled && it.action is ShortcutAction.OpenApplication }
                                    }
                                }
                            }
                        if (saved != null) {
                            launchSavedShortcut(context, saved)
                        } else {
                            when (command) {
                                LocalVoiceCommand.NAVIGATION -> viewModel.useDepartureBlueprint()
                                LocalVoiceCommand.MUSIC -> viewModel.useMusicBlueprint()
                            }
                        }
                    }
                }
            }
        }
        if (state.savedShortcuts.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Aucun raccourci", fontWeight = FontWeight.Bold)
                        Text("Choisissez un déclencheur, puis l’action à exécuter.")
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mes raccourcis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.savedShortcuts.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(state.savedShortcuts.chunked(2), key = { shortcuts -> shortcuts.joinToString { it.id.value } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { shortcut ->
                        CompactShortcutTile(
                            shortcut = shortcut,
                            onLaunch = { launchSavedShortcut(context, shortcut) },
                            onEdit = { viewModel.editSaved(shortcut) },
                            onPin = {
                                com.branlly.pocket.platform.android.PinnedRoutineShortcut.request(
                                    context,
                                    shortcut.id.value,
                                    shortcut.name,
                                )
                            },
                            onDelete = { viewModel.deleteSaved(shortcut.id) },
                            onExport = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(
                                            Intent.ACTION_SEND,
                                        ).setType("application/json").putExtra(Intent.EXTRA_TEXT, viewModel.exportRoutine(shortcut)),
                                        "Exporter ${shortcut.name}",
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                "https://github.com/Dank-hunters/branlly-pocket/releases/latest/download/Branlly-Pocket.apk",
                            ),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) { Text("↗ Télécharger la mise à jour") }
        }
    }
}

@Composable
private fun CompactShortcutTile(
    shortcut: ShortcutDefinition,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actionRegistry = remember(context) { AndroidActionRegistry.create(context.applicationContext) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.clickable(onClick = onLaunch),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        val accent = shortcut.accentColor.toComposeColor()
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = accent.copy(alpha = 0.16f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        shortcutGlyph(shortcut.iconKey),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                Text(shortcut.name, maxLines = 1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    shortcut.nodes
                        .firstOrNull()
                        ?.action
                        ?.let(actionRegistry::summary) ?: "Aucune action",
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${shortcut.nodes.count { it.enabled }} action(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    Text(
                        "⋯",
                        modifier = Modifier.clickable { menuExpanded = true }.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Modifier") }, onClick = {
                            menuExpanded = false
                            onEdit()
                        })
                        DropdownMenuItem(text = { Text("Épingler sur l’accueil") }, onClick = {
                            menuExpanded = false
                            onPin()
                        })
                        DropdownMenuItem(text = { Text("Exporter") }, onClick = {
                            menuExpanded = false
                            onExport()
                        })
                        DropdownMenuItem(text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) }, onClick = {
                            menuExpanded =
                                false
                            ; confirmDelete = true
                        })
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce raccourci ?") },
            text = { Text(shortcut.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SavedShortcutCard(
    shortcut: ShortcutDefinition,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val actionRegistry = remember(context) { AndroidActionRegistry.create(context.applicationContext) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            shortcutGlyph(shortcut.iconKey),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(shortcut.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        shortcut.nodes
                            .firstOrNull()
                            ?.action
                            ?.let(actionRegistry::summary) ?: "Aucune action",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) { Text("Modifier") }
                TextButton(onClick = { confirmDelete = true }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce raccourci ?") },
            text = { Text(shortcut.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun StartScreen(viewModel: EditorViewModel) {
    Page(title = "Créer un raccourci", subtitle = "Choisissez votre point de départ") {
        MethodCard(
            badge = "RECOMMANDÉ",
            title = "Création guidée",
            description = "Construire étape par étape avec des choix simples.",
            prominent = true,
            onClick = viewModel::showGuidedTriggers,
        )
        MethodCard("", "Utiliser un blueprint", "Partir d’un modèle prêt à personnaliser.", false, viewModel::showBlueprints)
        MethodCard("AVANCÉ", "Création libre", "Construire directement une séquence visuelle.", false, viewModel::startFree)
        VoiceCommandControl { command ->
            when (command) {
                LocalVoiceCommand.NAVIGATION -> viewModel.useDepartureBlueprint()
                LocalVoiceCommand.MUSIC -> viewModel.useMusicBlueprint()
            }
        }
        PrivacyNotice()
    }
}

@Composable
private fun TriggerScreen(viewModel: EditorViewModel) {
    Page(
        title = "Comment souhaitez-vous lancer ce raccourci ?",
        subtitle = "Seuls les réglages utiles seront demandés.",
        onBack = viewModel::showStart,
    ) {
        TriggerChoice("Bouton dans Branlly Pocket") { viewModel.startGuided(Trigger.ManualButton) }
        HudPanel {
            Text(
                "Les déclencheurs automatiques apparaîtront ici dès que leur moteur Android sera réellement opérationnel.",
                color = HudColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionChoiceScreen(viewModel: EditorViewModel) {
    Page(
        title = "Que doit faire ce raccourci ?",
        subtitle = "Choisissez une action. Vous la configurerez juste après.",
        onBack = viewModel::showGuidedTriggers,
    ) {
        MethodCard(
            badge = "APPAREIL",
            title = "Activer le Bluetooth",
            description = "Afficher la demande système uniquement si nécessaire.",
            prominent = true,
            onClick = viewModel::useBluetoothAction,
        )
        MethodCard(
            badge = "MÉDIA",
            title = "Jouer un média",
            description = "Choisir une application et une recherche.",
            prominent = false,
            onClick = viewModel::usePlayMediaAction,
        )
        MethodCard(
            badge = "OUVRIR",
            title = "Une application",
            description = "Choisir une application installée sur le téléphone.",
            prominent = false,
            onClick = viewModel::useMusicBlueprint,
        )
        MethodCard(
            badge = "DÉPLACEMENT",
            title = "Un itinéraire",
            description = "Choisir une application de navigation et une destination.",
            prominent = false,
            onClick = viewModel::useDepartureBlueprint,
        )
        HudPanel {
            Text(
                "Les autres actions seront ajoutées ici uniquement lorsqu’elles seront exécutables.",
                color = HudColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BlueprintScreen(viewModel: EditorViewModel) {
    Page(
        title = "Blueprints",
        subtitle = "Chaque élément restera entièrement modifiable.",
        onBack = viewModel::showStart,
    ) {
        MethodCard(
            "PRÊT À TESTER",
            "Je vais partir",
            "Choisir une application de navigation et une destination.",
            true,
            viewModel::useDepartureBlueprint,
        )
        MethodCard("TRAJET", "Mode voiture", "Bluetooth → volume → musique → navigation", false, viewModel::useCarBlueprint)
        listOf(
            "Départ au travail",
            "Retour à la maison",
            "Mode concentration",
            "Salle de sport",
            "Coucher",
            "Voyage",
            "Musique",
            "Mode conduite",
            "Appel rapide",
        ).forEach { title ->
            MethodCard(
                "MODÈLE",
                title,
                "Point de départ local, à compléter avant l’enregistrement.",
                false,
            ) { viewModel.useTemplate(title) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val draft = state.draft ?: return
    val context = LocalContext.current
    val validationIssues =
        remember(context, draft) {
            RoutineValidator(
                AndroidActionRegistry.create(context.applicationContext),
                AndroidActionValidationContext(context.applicationContext),
            ).validate(draft)
        }
    val requiresMediaAccess = draft.nodes.any { it.enabled && it.action is ShortcutAction.PlayMedia }
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaAccessEnabled by remember(context) {
        mutableStateOf(context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    mediaAccessEnabled = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HudColors.Background,
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(HudColors.BackgroundRaised)
                        .border(1.dp, HudColors.Grid, HudCutCornerShape)
                        .navigationBarsPadding()
                        .padding(horizontal = HudSpacing.Screen, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudSectionHeader(
                    title = "Validation",
                    detail = if (validationIssues.isEmpty()) "Routine prête" else "${validationIssues.size} point(s) à corriger",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HudSecondaryButton(
                        text = "Tester",
                        onClick = { testShortcut(context, draft) },
                        modifier = Modifier.weight(1f),
                        enabled = validationIssues.isEmpty() && (!requiresMediaAccess || mediaAccessEnabled),
                        height = 54.dp,
                    )
                    HudPrimaryButton(
                        text = "Enregistrer",
                        onClick = viewModel::saveDraft,
                        modifier = Modifier.weight(1f),
                        enabled = validationIssues.isEmpty(),
                        labelFontSize = 13.sp,
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HudColors.Background)
                    .padding(scaffoldPadding)
                    .statusBarsPadding(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(start = HudSpacing.Screen, end = HudSpacing.Screen, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(HudSpacing.Gap),
        ) {
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::showStart) {
                            Text("‹ RETOUR", color = HudColors.CyanBright, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.weight(1f))
                        HudStatusBadge(
                            if (draft.mode == EditorMode.ADVANCED) "MODE AVANCÉ" else "MODE SIMPLE",
                            HudColors.Cyan,
                        )
                    }
                    Text(
                        "Éditeur visuel",
                        color = HudColors.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Les actions s’exécutent de haut en bas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudColors.TextSecondary,
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = viewModel::rename,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nom visible du raccourci") },
                        supportingText = { Text("Ex. « Travail », « Salle de sport » ou « Retour maison ».") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.widgetLabel.orEmpty(),
                        onValueChange = viewModel::updateWidgetLabel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Texte du widget (facultatif, 4 caractères)") },
                        singleLine = true,
                    )
                    HudSecondaryButton("Icône et couleur", viewModel::showPresentationPicker, Modifier.fillMaxWidth())
                    validationIssues.filter { it.nodeId == null }.forEach { issue ->
                        HudValidationMessage(issue.message)
                    }
                    if (requiresMediaAccess && !mediaAccessEnabled) {
                        HudValidationMessage("Autorisez le contrôle de lecture avant de tester une action média.")
                        HudSecondaryButton(
                            text = "Autoriser le contrôle de lecture",
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            accent = HudColors.Warning,
                        )
                    }
                }
            }
            item { HudSectionHeader("Timeline de routine", "${draft.nodes.size} action(s)") }
            item { TriggerCard(draft, viewModel::showTriggerConfiguration) }
            item { InsertButton { viewModel.showLibrary(0) } }
            itemsIndexed(draft.nodes, key = { _, node -> node.id.value }) { index, node ->
                ActionCard(
                    index = index + 1,
                    node = node,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.nodes.lastIndex,
                    onMoveUp = { viewModel.move(node.id, -1) },
                    onMoveDown = { viewModel.move(node.id, 1) },
                    onEdit = { viewModel.showConfiguration(node.id) },
                    onToggle = { viewModel.toggle(node.id) },
                    onDuplicate = { viewModel.duplicate(node.id) },
                    onDelay = { viewModel.cycleDelayBefore(node.id) },
                    onContinueOnError = { viewModel.toggleContinueOnError(node.id) },
                    onTest = { testShortcut(context, draft.copy(nodes = listOf(node))) },
                    onDelete = { viewModel.remove(node.id) },
                    validationMessages = validationIssues.filter { it.nodeId == node.id.value }.map { it.message },
                    testEnabled =
                        validationIssues.none { it.nodeId == node.id.value } &&
                            (node.action !is ShortcutAction.PlayMedia || mediaAccessEnabled),
                )
                InsertButton { viewModel.showLibrary(index + 1) }
            }
            if (state.suggestions.isNotEmpty()) {
                item {
                    HudPanel {
                        HudSectionHeader("Suggestions locales", "Hors ligne")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.suggestions, key = { it.kind }) { suggestion ->
                                HudSecondaryButton(
                                    text = "+ ${suggestion.title}",
                                    onClick = {
                                        viewModel.showLibrary(draft.nodes.size)
                                        viewModel.addAction(suggestion)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.libraryVisible) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hideLibrary,
            containerColor = HudColors.BackgroundRaised,
            contentColor = HudColors.TextPrimary,
        ) {
            ActionLibrary(draft.trigger, draft.mode, viewModel::addAction)
        }
    }
    if (state.presentationPickerVisible) {
        PresentationPickerSheet(
            iconKey = draft.iconKey,
            accentColor = draft.accentColor,
            onChange = viewModel::updatePresentation,
            onDismiss = viewModel::hidePresentationPicker,
        )
    }
    if (state.triggerConfigurationVisible) {
        TriggerConfigurationSheet(
            trigger = draft.trigger,
            onTriggerChange = viewModel::updateTrigger,
            onDismiss = viewModel::hideTriggerConfiguration,
        )
    }
    state.selectedNode?.let { node ->
        ActionConfigurationSheet(
            node = node,
            onActionChange = { viewModel.updateAction(node.id, it) },
            onDismiss = viewModel::hideConfiguration,
        )
    }
}

@Composable
private fun TriggerCard(
    draft: ShortcutDefinition,
    onClick: () -> Unit,
) {
    HudPanel(modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudIconContainer("▶", Modifier.size(40.dp), HudColors.Cyan)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("DÉCLENCHEUR", style = MaterialTheme.typography.labelSmall, color = HudColors.Cyan)
                Text(
                    draft.trigger.summary(),
                    color = HudColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Toucher pour configurer", color = HudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = HudColors.CyanBright, fontSize = 22.sp)
        }
    }
}

@Composable
private fun ActionCard(
    index: Int,
    node: ActionNode,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelay: () -> Unit,
    onContinueOnError: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    validationMessages: List<String>,
    testEnabled: Boolean,
) {
    val context = LocalContext.current
    val actionRegistry = remember(context) { AndroidActionRegistry.create(context.applicationContext) }
    val registration = actionRegistry.registration(node.action.kind)
    val statusColor =
        when {
            validationMessages.isNotEmpty() -> HudColors.Error
            !node.enabled -> HudColors.Disabled
            else -> HudColors.Success
        }
    val statusText =
        when {
            validationMessages.isNotEmpty() -> "INVALIDE"
            !node.enabled -> "DÉSACTIVÉE"
            else -> "VALIDE"
        }
    HudPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (node.enabled) 1f else 0.66f)
                .animateContentSize()
                .clickable(onClick = onEdit),
        borderColor = if (validationMessages.isNotEmpty()) HudColors.Error.copy(alpha = 0.7f) else HudColors.CyanMuted,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "⠿",
                color = HudColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
            )
            Spacer(Modifier.size(6.dp))
            HudStatusBadge(index.toString(), HudColors.Cyan)
            Spacer(Modifier.size(8.dp))
            HudIconContainer(actionGlyph(node.action.kind), Modifier.size(40.dp), statusColor)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp)) {
                Text(
                    registration?.title ?: node.action.kind.name,
                    color = HudColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    actionRegistry.summary(node.action),
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = node.enabled, onCheckedChange = { onToggle() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            HudStatusBadge(statusText, statusColor)
            TextButton(onClick = onDelay) {
                Text(
                    if (node.delayBeforeMillis == 0L) "DÉLAI : AUCUN" else "DÉLAI : ${node.delayBeforeMillis / 1_000} S",
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onContinueOnError) {
                Text(
                    if (node.errorStrategy is com.branlly.pocket.domain.model.ErrorStrategy.Stop) "ARRÊT SI ÉCHEC" else "CONTINUER SI ÉCHEC",
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        validationMessages.forEach { message -> HudValidationMessage(message) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HudSecondaryButton("↑", onMoveUp, Modifier.weight(0.35f), canMoveUp)
            HudSecondaryButton("↓", onMoveDown, Modifier.weight(0.35f), canMoveDown)
            HudSecondaryButton("Modifier", onEdit, Modifier.weight(1.3f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onTest, enabled = testEnabled) { Text("Tester") }
            TextButton(onClick = onDuplicate) { Text("Dupliquer") }
            TextButton(onClick = onDelete) { Text("Supprimer", color = HudColors.Error) }
        }
    }
}

@Composable
private fun ActionLibrary(
    trigger: Trigger,
    mode: EditorMode,
    onSelected: (ActionDescriptor) -> Unit,
) {
    val context = LocalContext.current
    val ordered =
        remember(context, trigger, mode) {
            AndroidActionRegistry.create(context.applicationContext).visibleDescriptors(
                trigger,
                includeAdvanced = mode == EditorMode.ADVANCED,
            )
        }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().background(HudColors.BackgroundRaised),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(start = HudSpacing.Screen, end = HudSpacing.Screen, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HudSectionHeader("Ajouter une action", "${ordered.size} disponibles")
            Text("Ordre adapté au déclencheur, entièrement hors ligne.", color = HudColors.TextSecondary)
        }
        ActionCategory.entries.forEach { category ->
            val actions = ordered.filter { it.category == category }
            if (actions.isNotEmpty()) {
                item { HudSectionHeader(category.label(), "${actions.size}", Modifier.padding(top = 10.dp)) }
                items(actions, key = { it.kind }) { descriptor ->
                    HudPanel(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(descriptor) },
                        borderColor = HudColors.Grid,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudIconContainer(actionGlyph(descriptor.kind), Modifier.size(40.dp), HudColors.Cyan)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    descriptor.title,
                                    color = HudColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    descriptor.description,
                                    color = HudColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("＋", color = HudColors.CyanBright, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HudColors.Background).statusBarsPadding(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(start = HudSpacing.Screen, end = HudSpacing.Screen, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(HudSpacing.Gap),
    ) {
        item {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("‹ RETOUR", color = HudColors.CyanBright, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            Text(
                "CONFIGURATION LOCALE",
                color = HudColors.Cyan,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(title, color = HudColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(HudSpacing.Gap)) { content() } }
    }
}

@Composable
private fun MethodCard(
    badge: String,
    title: String,
    description: String,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val glyph =
        when {
            title == "Création guidée" -> "◎"
            title == "Utiliser un blueprint" -> "▦"
            title == "Création libre" -> "⠿"
            title.contains("Bluetooth", ignoreCase = true) -> "ᛒ"
            title.contains("média", ignoreCase = true) || title.contains("Musique", ignoreCase = true) -> "♫"
            title.contains("application", ignoreCase = true) -> "▣"
            title.contains("itinéraire", ignoreCase = true) || title.contains("partir", ignoreCase = true) -> "⌖"
            else -> "◆"
        }
    HudChoiceCard(badge, title, description, glyph, prominent, onClick)
}

@Composable
private fun TriggerChoice(
    title: String,
    onClick: () -> Unit,
) {
    HudChoiceCard(
        badge = "DÉCLENCHEUR",
        title = title,
        description = "Lancement manuel depuis l’application.",
        glyph = "▶",
        prominent = true,
        onClick = onClick,
    )
}

@Composable
private fun InsertButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(HudColors.Cyan.copy(alpha = 0.035f), HudCutCornerShape)
                .border(1.dp, HudColors.Grid, HudCutCornerShape),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text("＋ AJOUTER ICI", color = HudColors.CyanBright, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PrivacyNotice() {
    HudPanel {
        Text(
            "◆ PRIVÉ PAR CONCEPTION",
            color = HudColors.Cyan,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Sans IA · Sans Internet · Données locales",
            color = HudColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun launchSavedShortcut(
    context: Context,
    shortcut: ShortcutDefinition,
) {
    com.branlly.pocket.platform.android.RoutineExecutionService
        .start(context.applicationContext, shortcut.id.value)
}

private fun testShortcut(
    context: Context,
    shortcut: ShortcutDefinition,
) {
    com.branlly.pocket.platform.android.RoutineExecutionService
        .startTransient(context.applicationContext, shortcut)
}

private fun actionGlyph(kind: ActionKind): String =
    when (kind) {
        ActionKind.ENABLE_BLUETOOTH -> "ᛒ"
        ActionKind.PLAY_MEDIA -> "♫"
        ActionKind.OPEN_ROUTE -> "⌖"
        ActionKind.OPEN_APPLICATION -> "▣"
        ActionKind.WAIT_FOR_MEDIA_PLAYBACK, ActionKind.WAIT -> "◷"
        ActionKind.SET_VOLUME -> "◖"
        ActionKind.SET_BRIGHTNESS -> "☼"
        ActionKind.SET_SOUND_MODE -> "◉"
        ActionKind.OPEN_SETTINGS -> "⚙"
        else -> "◆"
    }

private fun shortcutGlyph(iconKey: String): String =
    when (iconKey) {
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

private fun ActionCategory.label(): String =
    when (this) {
        ActionCategory.OPEN -> "Ouvrir"
        ActionCategory.DEVICE -> "Régler le téléphone"
        ActionCategory.COMMUNICATE -> "Communiquer"
        ActionCategory.ORGANIZE -> "Organiser"
        ActionCategory.CONTROL -> "Contrôler le raccourci"
    }
