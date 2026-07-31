@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalDensity
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
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
import com.branlly.pocket.platform.android.setup.InitialSetupDecision
import com.branlly.pocket.platform.android.setup.PermissionCapabilityResolver
import com.branlly.pocket.platform.android.setup.SetupStateStore
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
import com.branlly.pocket.ui.hud.isHudCompact
import com.branlly.pocket.ui.setup.InitialSetupScreen
import com.branlly.pocket.ui.setup.title
import com.branlly.pocket.ui.voice.VoiceCommandControl

@Composable
fun BranllyPocketApp(
    sharedMediaLink: String? = null,
    openCreateRequest: Int = 0,
    viewModel: EditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val setupStore = remember(context) { SetupStateStore(context.applicationContext) }
    val capabilityResolver = remember(context) { PermissionCapabilityResolver(context.applicationContext) }
    var setupSnapshot by remember { mutableStateOf(capabilityResolver.resolve()) }
    var setupCompleted by remember { mutableStateOf(setupStore.isCompleted()) }
    var limitedModeForCurrentLaunch by remember { mutableStateOf(false) }
    var setupOpenedFromSettings by remember { mutableStateOf(false) }

    fun refreshCapabilities() {
        setupSnapshot = capabilityResolver.resolve()
    }

    DisposableEffect(lifecycleOwner, capabilityResolver) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshCapabilities()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(sharedMediaLink) {
        sharedMediaLink?.let(viewModel::receiveSharedMediaLink)
    }
    LaunchedEffect(openCreateRequest) {
        if (openCreateRequest > 0) viewModel.showStart()
    }
    val state by viewModel.state.collectAsState()
    val showSetup =
        InitialSetupDecision.shouldShowAssistant(
            setupCompleted = setupCompleted,
            limitedModeForCurrentLaunch = limitedModeForCurrentLaunch,
            openedFromSettings = setupOpenedFromSettings,
        )
    val revokedCapabilities = InitialSetupDecision.revokedCapabilities(setupCompleted, setupSnapshot)
    HudSurfaceTheme {
        if (showSetup) {
            InitialSetupScreen(
                snapshot = setupSnapshot,
                store = setupStore,
                openedFromSettings = setupOpenedFromSettings,
                onRefresh = ::refreshCapabilities,
                onComplete = {
                    setupStore.markCompleted()
                    setupCompleted = true
                    setupOpenedFromSettings = false
                    limitedModeForCurrentLaunch = false
                    refreshCapabilities()
                },
                onContinueLimited = {
                    setupStore.markIncomplete()
                    setupCompleted = false
                    setupOpenedFromSettings = false
                    limitedModeForCurrentLaunch = true
                },
                onClose = { setupOpenedFromSettings = false },
            )
        } else {
            when (state.screen) {
                Screen.HOME -> {
                    HudHomeScreen(
                        state = state,
                        viewModel = viewModel,
                        missingCapabilityWarning = revokedCapabilities.joinToString { it.title() }.ifBlank { null },
                        onOpenSetup = { setupOpenedFromSettings = true },
                    )
                }

                Screen.EDITOR -> {
                    EditorScreen(state, viewModel)
                }
            }
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
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HudColors.Background,
        bottomBar = {
            if (!imeVisible) {
                HudEditorActionBar(
                    validationCount = validationIssues.size,
                    testEnabled = validationIssues.isEmpty() && (!requiresMediaAccess || mediaAccessEnabled),
                    saveEnabled = validationIssues.isEmpty(),
                    onTest = { testShortcut(context, draft) },
                    onSave = viewModel::saveDraft,
                )
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HudColors.Background)
                    .padding(scaffoldPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .imePadding(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(start = HudSpacing.Screen, end = HudSpacing.Screen, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(HudSpacing.Gap),
        ) {
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::showHome) {
                            Text("‹ RETOUR", color = HudColors.CyanBright, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.weight(1f))
                        HudStatusBadge(
                            "ÉDITEUR LIBRE",
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
            ActionLibrary(draft.trigger, viewModel::addAction)
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
            onActionChange = viewModel::updateActionDraft,
            onConfirm = viewModel::confirmActionDraft,
            onDismiss = viewModel::hideConfiguration,
        )
    }
}

@Composable
private fun HudEditorActionBar(
    validationCount: Int,
    testEnabled: Boolean,
    saveEnabled: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .background(HudColors.BackgroundRaised)
                .border(1.dp, HudColors.Grid, HudCutCornerShape)
                .padding(horizontal = HudSpacing.Screen, vertical = 9.dp),
    ) {
        val stackButtons = maxWidth < HudSpacing.NarrowWidth
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudSectionHeader(
                title = "Validation",
                detail = if (validationCount == 0) "Routine prête" else "$validationCount point(s) à corriger",
            )
            if (stackButtons) {
                HudSecondaryButton("Tester", onTest, Modifier.fillMaxWidth(), testEnabled, height = 50.dp)
                HudPrimaryButton(
                    "Enregistrer",
                    onSave,
                    Modifier.fillMaxWidth(),
                    saveEnabled,
                    labelFontSize = 13.sp,
                    showLeadingGlyph = false,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HudSecondaryButton(
                        text = "Tester",
                        onClick = onTest,
                        modifier = Modifier.weight(1f),
                        enabled = testEnabled,
                        height = 54.dp,
                    )
                    HudPrimaryButton(
                        text = "Enregistrer",
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = saveEnabled,
                        labelFontSize = 13.sp,
                        showLeadingGlyph = false,
                    )
                }
            }
        }
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = isHudCompact(maxWidth)
            Column(verticalArrangement = Arrangement.spacedBy(HudSpacing.Tight)) {
                ActionCardIdentity(
                    index = index,
                    title = registration?.title ?: node.action.kind.name,
                    summary = actionRegistry.summary(node.action),
                    glyph = actionGlyph(node.action.kind),
                    statusColor = statusColor,
                    enabled = node.enabled,
                    compact = compact,
                    onToggle = onToggle,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                            if (node.errorStrategy is com.branlly.pocket.domain.model.ErrorStrategy.Stop) {
                                "ARRÊT SI ÉCHEC"
                            } else {
                                "CONTINUER SI ÉCHEC"
                            },
                            color = HudColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                validationMessages.forEach { message -> HudValidationMessage(message) }
                if (compact) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        HudSecondaryButton("↑", onMoveUp, Modifier.widthIn(min = 56.dp), canMoveUp)
                        HudSecondaryButton("↓", onMoveDown, Modifier.widthIn(min = 56.dp), canMoveDown)
                        HudSecondaryButton("Modifier", onEdit, Modifier.widthIn(min = 132.dp))
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HudSecondaryButton("↑", onMoveUp, Modifier.weight(0.35f), canMoveUp)
                        HudSecondaryButton("↓", onMoveDown, Modifier.weight(0.35f), canMoveDown)
                        HudSecondaryButton("Modifier", onEdit, Modifier.weight(1.3f))
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(onClick = onTest, enabled = testEnabled) { Text("Tester") }
                    TextButton(onClick = onDuplicate) { Text("Dupliquer") }
                    TextButton(onClick = onDelete) { Text("Supprimer", color = HudColors.Error) }
                }
            }
        }
    }
}

@Composable
private fun ActionCardIdentity(
    index: Int,
    title: String,
    summary: String,
    glyph: String,
    statusColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⠿", color = HudColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Spacer(Modifier.size(6.dp))
            HudStatusBadge(index.toString(), HudColors.Cyan)
            Spacer(Modifier.size(8.dp))
            HudIconContainer(glyph, Modifier.size(40.dp), statusColor)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp)) {
                Text(
                    title,
                    color = HudColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (compact) 4 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!compact) Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
        if (compact) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (enabled) "ACTION ACTIVE" else "ACTION DÉSACTIVÉE",
                    modifier = Modifier.weight(1f),
                    color = if (enabled) HudColors.Success else HudColors.Disabled,
                    style = MaterialTheme.typography.labelSmall,
                )
                Switch(checked = enabled, onCheckedChange = { onToggle() })
            }
        }
    }
}

@Composable
private fun ActionLibrary(
    trigger: Trigger,
    onSelected: (ActionDescriptor) -> Unit,
) {
    val context = LocalContext.current
    val ordered =
        remember(context, trigger) {
            AndroidActionRegistry.create(context.applicationContext).visibleDescriptors(
                trigger,
                includeAdvanced = true,
            )
        }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .background(HudColors.BackgroundRaised),
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
                                    maxLines = 2,
                                )
                                Text(
                                    descriptor.description,
                                    color = HudColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
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
