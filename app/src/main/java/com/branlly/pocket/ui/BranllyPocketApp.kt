package com.branlly.pocket.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.branlly.pocket.domain.catalog.ActionCatalog
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.platform.android.ApplicationLaunchResult
import com.branlly.pocket.platform.android.ApplicationLauncher
import com.branlly.pocket.platform.android.RouteLaunchResult
import com.branlly.pocket.platform.android.RouteLauncher
import com.branlly.pocket.platform.android.ShortcutExecutionResult
import com.branlly.pocket.platform.android.ShortcutExecutor
import com.branlly.pocket.ui.editor.ActionConfigurationSheet
import com.branlly.pocket.ui.editor.EditorUiState
import com.branlly.pocket.ui.editor.EditorViewModel
import com.branlly.pocket.ui.editor.PresentationPickerSheet
import com.branlly.pocket.ui.editor.Screen
import com.branlly.pocket.ui.editor.TriggerConfigurationSheet
import com.branlly.pocket.ui.voice.VoiceCommandControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BranllyPocketApp(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    when (state.screen) {
        Screen.HOME -> HomeScreen(state, viewModel)
        Screen.START -> StartScreen(viewModel)
        Screen.GUIDED_TRIGGER -> TriggerScreen(viewModel)
        Screen.ACTION_CHOICE -> ActionChoiceScreen(viewModel)
        Screen.BLUEPRINTS -> BlueprintScreen(viewModel)
        Screen.EDITOR -> EditorScreen(state, viewModel)
    }
}

@Composable
private fun HomeScreen(
    state: EditorUiState,
    viewModel: EditorViewModel,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Branlly Pocket", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Vos raccourcis, sans complications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = viewModel::showGuidedTriggers, modifier = Modifier.fillMaxWidth()) {
                Text("＋ Nouveau raccourci")
            }
        }
        item {
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
            item { Text("Mes raccourcis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.savedShortcuts, key = { it.id.value }) { shortcut ->
                SavedShortcutCard(
                    shortcut = shortcut,
                    onLaunch = { launchSavedShortcut(context, shortcut) },
                    onEdit = { viewModel.editSaved(shortcut) },
                    onDelete = { viewModel.deleteSaved(shortcut.id) },
                )
            }
        }
    }
}

@Composable
private fun SavedShortcutCard(
    shortcut: ShortcutDefinition,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(shortcut.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                shortcut.nodes
                    .firstOrNull()
                    ?.action
                    ?.summary() ?: "Aucune action",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Toucher pour lancer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "Les déclencheurs automatiques apparaîtront ici dès que leur moteur Android sera réellement opérationnel.",
                modifier = Modifier.padding(16.dp),
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
            badge = "OUVRIR",
            title = "Une application",
            description = "Choisir une application installée sur le téléphone.",
            prominent = true,
            onClick = viewModel::useMusicBlueprint,
        )
        MethodCard(
            badge = "DÉPLACEMENT",
            title = "Un itinéraire",
            description = "Choisir Waze ou Google Maps et une destination.",
            prominent = false,
            onClick = viewModel::useDepartureBlueprint,
        )
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "Les autres actions seront ajoutées ici uniquement lorsqu’elles seront exécutables.",
                modifier = Modifier.padding(16.dp),
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
            "Choisir Waze ou Google Maps et une destination.",
            true,
            viewModel::useDepartureBlueprint,
        )
        MethodCard("TRAJET", "Mode voiture", "Bluetooth → volume → musique → navigation", false, viewModel::useCarBlueprint)
        listOf("Départ au travail", "Mode nuit", "Sport", "Réunion", "Batterie faible", "Routine du matin").forEach { title ->
            MethodCard("BIENTÔT", title, "Blueprint local en préparation", false, {})
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
    val automaticPauseCount =
        draft.nodes.filter(ActionNode::enabled).map(ActionNode::action).zipWithNext().count { (current, next) ->
            (current is ShortcutAction.OpenApplication || current is ShortcutAction.OpenRoute) &&
                (next is ShortcutAction.OpenApplication || next is ShortcutAction.OpenRoute)
        }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface(tonalElevation = 5.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = { testShortcut(context, draft) }, modifier = Modifier.weight(1f)) {
                        Text("Tester")
                    }
                    Button(onClick = viewModel::saveDraft, enabled = draft.nodes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Enregistrer")
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).statusBarsPadding(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::showStart) { Text("‹ Retour") }
                    Spacer(Modifier.weight(1f))
                    Text("MODE SIMPLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("Éditeur visuel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::rename,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("Nom du raccourci") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.widgetLabel.orEmpty(),
                    onValueChange = viewModel::updateWidgetLabel,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Texte du widget (facultatif, 4 caractères)") },
                    singleLine = true,
                )
                OutlinedButton(onClick = viewModel::showPresentationPicker, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Icône et couleur")
                }
            }
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
                    onDelete = { viewModel.remove(node.id) },
                )
                InsertButton { viewModel.showLibrary(index + 1) }
            }
            if (automaticPauseCount > 0) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "Pause automatique de 5 s entre applications externes. Ajoutez Attendre pour choisir un délai différent.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item {
                    Text("Suggestions locales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Calculées sur cet appareil", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(state.suggestions, key = { it.kind }) { suggestion ->
                            OutlinedButton(onClick = {
                                viewModel.showLibrary(draft.nodes.size)
                                viewModel.addAction(suggestion)
                            }) { Text("+ ${suggestion.title}") }
                        }
                    }
                }
            }
        }
    }
    if (state.libraryVisible) {
        ModalBottomSheet(onDismissRequest = viewModel::hideLibrary) {
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
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("DÉCLENCHEUR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(draft.trigger.summary(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Toucher pour configurer", style = MaterialTheme.typography.bodySmall)
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
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (node.enabled) 1f else 0.62f)
                .animateContentSize()
                .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (node.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ACTION $index", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(node.action.summary(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Switch(checked = node.enabled, onCheckedChange = { onToggle() })
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓") }
                Button(onClick = onEdit) { Text("Modifier") }
                Spacer(Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDuplicate) { Text("Dupliquer") }
                TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ActionLibrary(
    trigger: Trigger,
    onSelected: (ActionDescriptor) -> Unit,
) {
    val ordered = ActionCatalog.orderedFor(trigger)
    LazyColumn(
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Ajouter une action", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Ordre adapté au déclencheur, entièrement hors ligne.", modifier = Modifier.padding(bottom = 12.dp))
        }
        ActionCategory.entries.forEach { category ->
            val actions = ordered.filter { it.category == category }
            if (actions.isNotEmpty()) {
                item { Text(category.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp)) }
                items(actions, key = { it.kind }) { descriptor ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(descriptor) },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(descriptor.title, fontWeight = FontWeight.SemiBold)
                            Text(descriptor.description, style = MaterialTheme.typography.bodySmall)
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
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ Retour") }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
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
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (prominent) 3.dp else 1.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (prominent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            if (badge.isNotEmpty()) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TriggerChoice(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("›", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InsertButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick) { Text("＋ Ajouter ici") }
    }
}

@Composable
private fun PrivacyNotice() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            "Privé par conception · Sans IA · Sans Internet · Données locales",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun launchSavedShortcut(
    context: Context,
    shortcut: ShortcutDefinition,
) {
    CoroutineScope(Dispatchers.Main.immediate).launch {
        when (val result = ShortcutExecutor(context.applicationContext).execute(shortcut)) {
            ShortcutExecutionResult.Completed -> {
                Unit
            }

            is ShortcutExecutionResult.Failed -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }

            is ShortcutExecutionResult.UnsupportedAction -> {
                Toast
                    .makeText(
                        context,
                        "L’action ${result.kind} n’est pas encore exécutable.",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }
}

private fun testShortcut(
    context: Context,
    shortcut: ShortcutDefinition,
) {
    val action =
        shortcut.nodes
            .asSequence()
            .filter(ActionNode::enabled)
            .map(ActionNode::action)
            .firstOrNull { it is ShortcutAction.OpenRoute || it is ShortcutAction.OpenApplication }
    val message =
        when (action) {
            is ShortcutAction.OpenRoute -> {
                routeLaunchMessage(RouteLauncher(context.applicationContext).launch(action))
            }

            is ShortcutAction.OpenApplication -> {
                val packageName = (action.packageName as? InputValue.Fixed<String>)?.value
                val searchQuery = (action.searchQuery as? InputValue.Fixed<String>)?.value
                applicationLaunchMessage(ApplicationLauncher(context.applicationContext).launch(packageName, searchQuery))
            }

            else -> {
                "Aucune action testable pour le moment."
            }
        }
    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun routeLaunchMessage(result: RouteLaunchResult): String? =
    when (result) {
        RouteLaunchResult.Launched -> null
        RouteLaunchResult.MissingApplication -> "L’application de navigation choisie n’est pas installée."
        RouteLaunchResult.MissingDestination -> "Indiquez une destination avant de tester."
        RouteLaunchResult.RuntimeValueRequired -> "La saisie au lancement sera disponible prochainement."
        RouteLaunchResult.UnsupportedApplication -> "Cette application de navigation n’est pas prise en charge."
        RouteLaunchResult.RejectedBySystem -> "Android a refusé l’ouverture de l’itinéraire."
    }

private fun applicationLaunchMessage(result: ApplicationLaunchResult): String? =
    when (result) {
        ApplicationLaunchResult.Launched -> null
        ApplicationLaunchResult.RuntimeValueRequired -> "Choisissez une application avant de tester."
        ApplicationLaunchResult.InvalidPackage -> "L’application sélectionnée n’est pas valide."
        ApplicationLaunchResult.MissingApplication -> "L’application sélectionnée n’est plus installée."
        ApplicationLaunchResult.RejectedBySystem -> "Android a refusé l’ouverture de l’application."
    }

private fun ActionCategory.label(): String =
    when (this) {
        ActionCategory.OPEN -> "Ouvrir"
        ActionCategory.DEVICE -> "Régler le téléphone"
        ActionCategory.COMMUNICATE -> "Communiquer"
        ActionCategory.ORGANIZE -> "Organiser"
        ActionCategory.CONTROL -> "Contrôler le raccourci"
    }
