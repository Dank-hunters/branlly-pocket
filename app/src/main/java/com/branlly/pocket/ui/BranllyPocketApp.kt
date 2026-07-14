package com.branlly.pocket.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.branlly.pocket.domain.catalog.ActionCatalog
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ChargerEvent
import com.branlly.pocket.domain.model.NumericComparison
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.summary
import com.branlly.pocket.ui.editor.EditorUiState
import com.branlly.pocket.ui.editor.EditorViewModel
import com.branlly.pocket.ui.editor.Screen
import java.time.LocalTime

@Composable
fun BranllyPocketApp(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    when (state.screen) {
        Screen.START -> StartScreen(viewModel)
        Screen.GUIDED_TRIGGER -> TriggerScreen(viewModel)
        Screen.BLUEPRINTS -> BlueprintScreen(viewModel)
        Screen.EDITOR -> EditorScreen(state, viewModel)
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
        TriggerChoice("En appuyant sur un bouton") { viewModel.startGuided(Trigger.ManualButton) }
        TriggerChoice("À une heure précise") { viewModel.startGuided(Trigger.Time(LocalTime.of(8, 0))) }
        TriggerChoice("Lors d’une connexion Bluetooth") {
            viewModel.startGuided(com.branlly.pocket.domain.model.Trigger.Bluetooth(null, "Appareil à choisir", com.branlly.pocket.domain.model.ConnectionEvent.CONNECTED))
        }
        TriggerChoice("Lors d’une connexion Wi-Fi") {
            viewModel.startGuided(Trigger.Wifi("Réseau à choisir", com.branlly.pocket.domain.model.ConnectionEvent.CONNECTED))
        }
        TriggerChoice("Lors du branchement du chargeur") { viewModel.startGuided(Trigger.Charger(ChargerEvent.PLUGGED)) }
        TriggerChoice("Selon le niveau de batterie") { viewModel.startGuided(Trigger.BatteryLevel(20, NumericComparison.LESS_OR_EQUAL)) }
        TriggerChoice("Avec un tag NFC") { viewModel.startGuided(Trigger.Nfc()) }
        TriggerChoice("Depuis un widget") { viewModel.startGuided(Trigger.Widget) }
        TriggerChoice("Depuis une tuile rapide") { viewModel.startGuided(Trigger.QuickTile) }
    }
}

@Composable
private fun BlueprintScreen(viewModel: EditorViewModel) {
    Page(
        title = "Blueprints",
        subtitle = "Chaque élément restera entièrement modifiable.",
        onBack = viewModel::showStart,
    ) {
        MethodCard("TRAJET", "Mode voiture", "Bluetooth → volume → musique → navigation", true, viewModel::useCarBlueprint)
        listOf("Départ au travail", "Mode nuit", "Sport", "Réunion", "Batterie faible", "Routine du matin").forEach { title ->
            MethodCard("BIENTÔT", title, "Blueprint local en préparation", false, {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(state: EditorUiState, viewModel: EditorViewModel) {
    val draft = state.draft ?: return
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
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
            }
            item { TriggerCard(draft) }
            item { InsertButton { viewModel.showLibrary(0) } }
            itemsIndexed(draft.nodes, key = { _, node -> node.id.value }) { index, node ->
                ActionCard(
                    node = node,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.nodes.lastIndex,
                    onMoveUp = { viewModel.move(node.id, -1) },
                    onMoveDown = { viewModel.move(node.id, 1) },
                    onToggle = { viewModel.toggle(node.id) },
                    onDuplicate = { viewModel.duplicate(node.id) },
                    onDelete = { viewModel.remove(node.id) },
                )
                InsertButton { viewModel.showLibrary(index + 1) }
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
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            tonalElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Tester") }
                Button(onClick = {}, enabled = draft.nodes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Aperçu") }
            }
        }
    }
    if (state.libraryVisible) {
        ModalBottomSheet(onDismissRequest = viewModel::hideLibrary) {
            ActionLibrary(draft.trigger, viewModel::addAction)
        }
    }
}

@Composable
private fun TriggerCard(draft: ShortcutDefinition) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("DÉCLENCHEUR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(draft.trigger.summary(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Toucher pour configurer", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActionCard(
    node: ActionNode,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (node.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ACTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(node.action.summary(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Switch(checked = node.enabled, onCheckedChange = { onToggle() })
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓") }
                TextButton(onClick = onDuplicate) { Text("Dupliquer") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ActionLibrary(trigger: Trigger, onSelected: (ActionDescriptor) -> Unit) {
    val ordered = ActionCatalog.orderedFor(trigger)
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
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
private fun MethodCard(badge: String, title: String, description: String, prominent: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
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
private fun TriggerChoice(title: String, onClick: () -> Unit) {
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
        OutlinedButton(onClick = onClick) { Text("＋ Ajouter ici") }
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

private fun ActionCategory.label(): String = when (this) {
    ActionCategory.OPEN -> "Ouvrir"
    ActionCategory.DEVICE -> "Régler le téléphone"
    ActionCategory.COMMUNICATE -> "Communiquer"
    ActionCategory.ORGANIZE -> "Organiser"
    ActionCategory.CONTROL -> "Contrôler le raccourci"
}
