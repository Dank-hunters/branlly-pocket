package com.branlly.pocket.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.platform.android.BranllyMediaListener
import com.branlly.pocket.platform.android.PinnedRoutineShortcut
import com.branlly.pocket.platform.android.RoutineExecutionService
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.ui.editor.EditorUiState
import com.branlly.pocket.ui.editor.EditorViewModel
import com.branlly.pocket.ui.hud.ActionStepRow
import com.branlly.pocket.ui.hud.HudBottomNavigation
import com.branlly.pocket.ui.hud.HudCard
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudCutCornerShape
import com.branlly.pocket.ui.hud.HudIconContainer
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudPrimaryButton
import com.branlly.pocket.ui.hud.HudSecondaryButton
import com.branlly.pocket.ui.hud.HudSpacing
import com.branlly.pocket.ui.hud.HudStatusBadge
import com.branlly.pocket.ui.hud.StatusRing
import com.branlly.pocket.ui.hud.isHudCompact
import com.branlly.pocket.ui.voice.VoiceCommandControl

@Composable
internal fun HudHomeScreen(
    state: EditorUiState,
    viewModel: EditorViewModel,
    missingCapabilityWarning: String?,
    onOpenSetup: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val actionRegistry = remember(context) { AndroidActionRegistry.create(context.applicationContext) }
    val selected = state.savedShortcuts.firstOrNull()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { selectedFile ->
                runCatching {
                    context.contentResolver
                        .openInputStream(selectedFile)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                }.onSuccess(viewModel::importRoutine)
                    .onFailure { viewModel.importRoutine("") }
            }
        }
    val importAction = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
    val launchSelected = { selected?.let { launchHudShortcut(context, it) } ?: viewModel.showStart() }
    val hasError =
        state.message?.contains("erreur", ignoreCase = true) == true || state.message?.contains("échec", ignoreCase = true) == true
    val ringColor = if (hasError) HudColors.Error else HudColors.Cyan
    val mediaAuthorized = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    val listenerConnected = BranllyMediaListener.isConnected()

    Scaffold(
        containerColor = HudColors.Background,
        bottomBar = {
            HudBottomNavigation(
                onHome = {},
                onCreate = viewModel::showStart,
                onImport = importAction,
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HudColors.Background)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(scaffoldPadding),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = HudSpacing.Screen,
                    end = HudSpacing.Screen,
                    top = 7.dp,
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(HudSpacing.Gap),
        ) {
            item {
                HudHeader(
                    onImport = importAction,
                    onVoiceCommand = { command -> handleVoiceCommand(command, state, viewModel, context) },
                )
            }
            state.message?.let { message ->
                item {
                    HudPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::clearMessage)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudStatusBadge(if (hasError) "ALERTE" else "INFO", if (hasError) HudColors.Error else HudColors.Cyan)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                message,
                                modifier = Modifier.weight(1f),
                                color = HudColors.TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            missingCapabilityWarning?.let { missing ->
                item {
                    HudPanel(modifier = Modifier.fillMaxWidth(), borderColor = HudColors.Warning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudStatusBadge("À corriger", HudColors.Warning)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Autorisation retirée", color = HudColors.TextPrimary, fontWeight = FontWeight.Bold)
                                Text(missing, color = HudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HudSecondaryButton(
                            text = "Configuration et autorisations",
                            onClick = onOpenSetup,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = isHudCompact(maxWidth)
                    if (compact) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusRing(
                                label = if (hasError) "ALERTE" else "PRÊT",
                                detail = selected?.let { "${it.nodes.count { node -> node.enabled }} ACTIONS" } ?: "AUCUNE ROUTINE",
                                color = ringColor,
                                modifier = Modifier.clickable(onClick = launchSelected),
                            )
                            HudStatusCards(
                                mediaAuthorized = mediaAuthorized,
                                listenerConnected = listenerConnected,
                                routineCount = state.savedShortcuts.size,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1.08f), contentAlignment = Alignment.Center) {
                                StatusRing(
                                    label = if (hasError) "ALERTE" else "PRÊT",
                                    detail = selected?.let { "${it.nodes.count { node -> node.enabled }} ACTIONS" } ?: "AUCUNE ROUTINE",
                                    color = ringColor,
                                    modifier = Modifier.clickable(onClick = launchSelected),
                                )
                            }
                            HudStatusCards(
                                mediaAuthorized = mediaAuthorized,
                                listenerConnected = listenerConnected,
                                routineCount = state.savedShortcuts.size,
                                modifier = Modifier.weight(0.92f),
                            )
                        }
                    }
                }
            }
            if (selected == null) {
                item {
                    HudPanel(modifier = Modifier.fillMaxWidth(), glow = true) {
                        Text(
                            "AUCUNE ROUTINE",
                            color = HudColors.CyanBright,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Créez votre première routine locale pour afficher son état et ses étapes.",
                            color = HudColors.TextSecondary,
                        )
                    }
                }
            } else {
                item {
                    HudRoutinePanel(
                        shortcut = selected,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = { menuExpanded = it },
                        onLaunch = launchSelected,
                        onEdit = { viewModel.editSaved(selected) },
                        onPin = { PinnedRoutineShortcut.request(context, selected.id.value, selected.name) },
                        onExport = { exportRoutine(context, viewModel, selected) },
                        onDelete = { confirmDelete = true },
                    )
                }
                item {
                    HudPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "ÉTAPES DE ROUTINE",
                                modifier = Modifier.weight(1f),
                                color = HudColors.CyanBright,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.8.sp,
                            )
                            Text(
                                "${selected.nodes.size} ÉTAPES",
                                color = HudColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                        selected.nodes.take(MAX_VISIBLE_STEPS).forEachIndexed { index, node ->
                            val registration = actionRegistry.registration(node.action.kind)
                            ActionStepRow(
                                index = index + 1,
                                glyph = hudActionGlyph(node.action.kind),
                                title = registration?.title ?: node.action.kind.name,
                                summary = actionRegistry.summary(node.action),
                                status = if (node.enabled) "PRÊTE" else "INACTIVE",
                                statusColor = if (node.enabled) HudColors.Success else HudColors.Disabled,
                                showConnector = index < minOf(selected.nodes.lastIndex, MAX_VISIBLE_STEPS - 1),
                            )
                        }
                        if (selected.nodes.size > MAX_VISIBLE_STEPS) {
                            Text(
                                "+ ${selected.nodes.size - MAX_VISIBLE_STEPS} autres actions",
                                color = HudColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
            item {
                HudPrimaryButton(
                    text = if (selected == null) "Créer une routine" else "Lancer la routine",
                    onClick = launchSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                HudSecondaryButton(
                    text = "Configuration et autorisations",
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "↗ TÉLÉCHARGER LA MISE À JOUR",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { openLatestRelease(context) }
                            .padding(vertical = 5.dp),
                    color = HudColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce raccourci ?") },
            text = { Text(selected.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSaved(selected.id)
                    },
                ) { Text("Supprimer", color = HudColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun HudHeader(
    onImport: () -> Unit,
    onVoiceCommand: (LocalVoiceCommand) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (isHudCompact(maxWidth)) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudIconContainer("B", modifier = Modifier.size(46.dp), accent = HudColors.CyanBright)
                    HudBrandText(Modifier.weight(1f).padding(start = 11.dp))
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    HudHeaderActions(onImport, onVoiceCommand)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudIconContainer("B", modifier = Modifier.size(46.dp), accent = HudColors.CyanBright)
                HudBrandText(Modifier.weight(1f).padding(start = 11.dp, end = 8.dp))
                HudHeaderActions(onImport, onVoiceCommand)
            }
        }
    }
}

@Composable
private fun HudBrandText(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "BRANLLY POCKET",
            color = HudColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            letterSpacing = 0.9.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            "ROUTINES LOCALES • EXÉCUTION DIRECTE",
            color = HudColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 0.45.sp,
        )
    }
}

@Composable
private fun HudHeaderActions(
    onImport: () -> Unit,
    onVoiceCommand: (LocalVoiceCommand) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable(onClick = onImport)) {
            HudIconContainer("⇩", modifier = Modifier.size(48.dp), accent = HudColors.TextSecondary)
        }
        Spacer(Modifier.width(7.dp))
        VoiceCommandControl(onCommand = onVoiceCommand)
    }
}

@Composable
private fun HudStatusCards(
    mediaAuthorized: Boolean,
    listenerConnected: Boolean,
    routineCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudSystemCard(
            "CONTRÔLE LECTURE",
            if (mediaAuthorized) "AUTORISÉ" else "REQUIS",
            if (mediaAuthorized) HudColors.Success else HudColors.Warning,
        )
        HudSystemCard(
            "SERVICE MÉDIA",
            if (listenerConnected) "ACTIF" else "EN VEILLE",
            if (listenerConnected) HudColors.Success else HudColors.TextSecondary,
        )
        HudSystemCard("ROUTINES", routineCount.toString(), HudColors.Cyan)
    }
}

@Composable
private fun HudSystemCard(
    label: String,
    value: String,
    color: Color,
) {
    HudCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = HudColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 0.45.sp,
            )
            Box(Modifier.size(5.dp).background(color, CircleShape))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HudColors.Grid.copy(alpha = 0.7f)))
        Text(
            value,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun HudRoutinePanel(
    shortcut: ShortcutDefinition,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    HudPanel(modifier = Modifier.fillMaxWidth(), glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "ROUTINE SÉLECTIONNÉE",
                    color = HudColors.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    shortcut.name,
                    color = HudColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HudStatusBadge("PRÊTE", HudColors.Success, Modifier.padding(start = 8.dp))
            Box {
                Text(
                    "⋮",
                    modifier =
                        Modifier
                            .clickable {
                                onMenuExpandedChange(
                                    true,
                                )
                            }.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    color = HudColors.CyanBright,
                    fontSize = 23.sp,
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                    DropdownMenuItem(text = { Text("Modifier") }, onClick = {
                        onMenuExpandedChange(false)
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Épingler sur l’accueil") }, onClick = {
                        onMenuExpandedChange(false)
                        onPin()
                    })
                    DropdownMenuItem(text = { Text("Exporter") }, onClick = {
                        onMenuExpandedChange(false)
                        onExport()
                    })
                    DropdownMenuItem(text = { Text("Supprimer", color = HudColors.Error) }, onClick = {
                        onMenuExpandedChange(false)
                        onDelete()
                    })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HudColors.Grid))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudMetric("DÉCLENCHEUR", triggerLabel(shortcut.trigger), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(25.dp).background(HudColors.Grid))
            HudMetric("ACTIONS", shortcut.nodes.count { it.enabled }.toString(), Modifier.weight(0.72f))
            Box(Modifier.width(1.dp).height(25.dp).background(HudColors.Grid))
            HudMetric("MODE", shortcut.mode.name, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudPrimaryButton("Exécuter", onLaunch, Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .background(HudColors.Cyan.copy(alpha = 0.07f), HudCutCornerShape)
                        .border(1.dp, HudColors.Grid, HudCutCornerShape)
                        .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", color = HudColors.CyanBright, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun HudMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = HudColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 8.sp, maxLines = 1)
        Text(
            value.uppercase(),
            color = HudColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun handleVoiceCommand(
    command: LocalVoiceCommand,
    state: EditorUiState,
    viewModel: EditorViewModel,
    context: Context,
) {
    val saved =
        when (command) {
            LocalVoiceCommand.NAVIGATION -> {
                state.savedShortcuts.firstOrNull { shortcut ->
                    shortcut.nodes.any { it.enabled && it.action.kind == ActionKind.OPEN_ROUTE }
                }
            }

            LocalVoiceCommand.MUSIC -> {
                state.savedShortcuts.firstOrNull { shortcut ->
                    shortcut.nodes.any { it.enabled && it.action.kind == ActionKind.PLAY_MEDIA }
                }
            }
        }
    if (saved != null) {
        launchHudShortcut(context, saved)
    } else {
        viewModel.startFree()
    }
}

private fun launchHudShortcut(
    context: Context,
    shortcut: ShortcutDefinition,
) {
    RoutineExecutionService.start(context.applicationContext, shortcut.id.value)
}

private fun exportRoutine(
    context: Context,
    viewModel: EditorViewModel,
    shortcut: ShortcutDefinition,
) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TEXT, viewModel.exportRoutine(shortcut)),
            "Exporter ${shortcut.name}",
        ),
    )
}

private fun openLatestRelease(context: Context) {
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/Dank-hunters/branlly-pocket/releases/latest/download/Branlly-Pocket.apk"),
        ),
    )
}

private fun triggerLabel(trigger: Trigger): String =
    when (trigger) {
        Trigger.ManualButton -> "MANUEL"
        is Trigger.Time -> "HORAIRE"
        is Trigger.Bluetooth -> "BLUETOOTH"
        is Trigger.Wifi -> "WI-FI"
        is Trigger.Charger -> "CHARGEUR"
        is Trigger.BatteryLevel -> "BATTERIE"
        is Trigger.Nfc -> "NFC"
        Trigger.Widget -> "WIDGET"
        Trigger.QuickTile -> "RÉGLAGE RAPIDE"
    }

private fun hudActionGlyph(kind: ActionKind): String =
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

private const val MAX_VISIBLE_STEPS = 6
