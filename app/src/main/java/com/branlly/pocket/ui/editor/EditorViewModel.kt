package com.branlly.pocket.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.execution.RoutineValidator
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ConnectionEvent
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAccentColor
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutCategory
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.VolumeStream
import com.branlly.pocket.platform.android.BranllyPocketWidget
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
import com.branlly.pocket.platform.android.actions.BuiltInProviderCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = SavedShortcutStore(application)
    private val actionRegistry = AndroidActionRegistry.create(application)
    private val validator = RoutineValidator(actionRegistry, AndroidActionValidationContext(application))
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.shortcuts.collect { shortcuts ->
                _state.update { it.copy(savedShortcuts = shortcuts) }
            }
        }
    }

    fun startFree() = openEditor(Trigger.ManualButton)

    fun startGuided(trigger: Trigger) {
        _state.update { state ->
            state.copy(
                screen = Screen.ACTION_CHOICE,
                draft = ShortcutDefinition(name = "Nouveau raccourci", trigger = trigger, nodes = emptyList()),
                triggerConfigurationVisible = false,
            )
        }
    }

    fun useMusicBlueprint() {
        val node = ActionNode(action = ShortcutAction.OpenApplication(InputValue.AskAtRuntime))
        _state.update { state ->
            EditorUiState(
                screen = Screen.EDITOR,
                draft =
                    ShortcutDefinition(
                        name = "Mode musique",
                        category = ShortcutCategory.WELLBEING,
                        trigger = state.draft?.trigger ?: Trigger.ManualButton,
                        nodes = listOf(node),
                    ),
                selectedNodeId = node.id,
                savedShortcuts = state.savedShortcuts,
            )
        }
    }

    fun receiveSharedMediaLink(link: String) {
        val node =
            ActionNode(
                action =
                    ShortcutAction.OpenApplication(
                        packageName = InputValue.AskAtRuntime,
                        mediaUri = InputValue.Fixed(link),
                    ),
            )
        _state.update { state ->
            EditorUiState(
                screen = Screen.EDITOR,
                draft =
                    ShortcutDefinition(
                        name = "Titre partagé",
                        category = ShortcutCategory.WELLBEING,
                        trigger = Trigger.ManualButton,
                        nodes = listOf(node),
                    ),
                selectedNodeId = node.id,
                savedShortcuts = state.savedShortcuts,
                message = "Choisissez l’application qui ouvrira ce titre.",
            )
        }
    }

    fun useDepartureBlueprint() {
        val node =
            ActionNode(
                action =
                    ShortcutAction.OpenRoute(
                        navigationPackage = InputValue.Fixed(BuiltInProviderCatalog.defaultNavigationPackage),
                        destination = InputValue.Fixed(""),
                    ),
            )
        _state.update { state ->
            EditorUiState(
                screen = Screen.EDITOR,
                draft =
                    ShortcutDefinition(
                        name = "Nouvel itinéraire",
                        category = ShortcutCategory.TRAVEL,
                        trigger = state.draft?.trigger ?: Trigger.ManualButton,
                        nodes = listOf(node),
                    ),
                selectedNodeId = node.id,
                savedShortcuts = state.savedShortcuts,
            )
        }
    }

    fun useTemplate(name: String) {
        val category =
            when (name) {
                "Départ au travail", "Retour à la maison", "Voyage", "Mode conduite" -> ShortcutCategory.TRAVEL
                "Musique", "Salle de sport", "Coucher", "Mode concentration" -> ShortcutCategory.WELLBEING
                else -> ShortcutCategory.COMMUNICATION
            }
        val actions =
            when (name) {
                "Mode concentration", "Coucher" -> {
                    listOf(ActionNode(action = ShortcutAction.SetSoundMode(com.branlly.pocket.domain.model.SoundMode.DO_NOT_DISTURB)))
                }

                "Mode conduite" -> {
                    listOf(
                        ActionNode(action = ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70))),
                        ActionNode(action = ShortcutAction.OpenApplication(InputValue.AskAtRuntime)),
                        ActionNode(action = ShortcutAction.OpenRoute(InputValue.AskAtRuntime, InputValue.AskAtRuntime)),
                    )
                }

                else -> {
                    listOf(ActionNode(action = ShortcutAction.OpenApplication(InputValue.AskAtRuntime)))
                }
            }
        _state.update { state ->
            EditorUiState(
                screen = Screen.EDITOR,
                draft = ShortcutDefinition(name = name, category = category, trigger = Trigger.ManualButton, nodes = actions),
                selectedNodeId = actions.firstOrNull()?.id,
                savedShortcuts = state.savedShortcuts,
            )
        }
    }

    fun useCarBlueprint() {
        _state.value =
            EditorUiState(
                screen = Screen.EDITOR,
                draft =
                    ShortcutDefinition(
                        name = "Mode voiture",
                        category = ShortcutCategory.TRAVEL,
                        trigger = Trigger.Bluetooth(null, "Appareil à choisir", ConnectionEvent.CONNECTED),
                        nodes =
                            listOf(
                                ActionNode(action = ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70))),
                                ActionNode(action = ShortcutAction.OpenApplication(InputValue.AskAtRuntime)),
                                ActionNode(action = ShortcutAction.Wait(2_000)),
                                ActionNode(action = ShortcutAction.OpenRoute(InputValue.AskAtRuntime, InputValue.AskAtRuntime)),
                            ),
                    ),
            )
    }

    fun importRoutine(raw: String) {
        val imported = store.import(raw)
        if (imported == null) {
            _state.update { it.copy(message = "Fichier Branlly Pocket invalide ou non pris en charge.") }
        } else {
            val issues = validator.validate(imported)
            if (issues.isNotEmpty()) {
                _state.update { it.copy(message = "Import refusé : ${issues.first().message}") }
                return
            }
            viewModelScope.launch {
                store.save(imported)
                _state.update { it.copy(message = "Raccourci importé : ${imported.name}") }
            }
        }
    }

    fun exportRoutine(shortcut: ShortcutDefinition): String = store.export(shortcut)

    fun showStart() = _state.update { state -> EditorUiState(savedShortcuts = state.savedShortcuts) }

    fun showGuidedTriggers() = _state.update { it.copy(screen = Screen.GUIDED_TRIGGER) }

    fun showBlueprints() = _state.update { it.copy(screen = Screen.BLUEPRINTS) }

    fun showLibrary(index: Int) =
        _state.update {
            it.copy(
                insertionIndex = index,
                libraryVisible = true,
                selectedNodeId = null,
                triggerConfigurationVisible = false,
            )
        }

    fun hideLibrary() = _state.update { it.copy(libraryVisible = false) }

    fun showConfiguration(nodeId: NodeId) =
        _state.update {
            it.copy(selectedNodeId = nodeId, libraryVisible = false)
        }

    fun hideConfiguration() = _state.update { it.copy(selectedNodeId = null) }

    fun showPresentationPicker() = _state.update { it.copy(presentationPickerVisible = true) }

    fun hidePresentationPicker() = _state.update { it.copy(presentationPickerVisible = false) }

    fun updatePresentation(
        iconKey: String,
        accentColor: ShortcutAccentColor,
    ) = _state.update { state ->
        state.copy(draft = state.draft?.copy(iconKey = iconKey, accentColor = accentColor))
    }

    fun updateWidgetLabel(label: String) =
        _state.update { state ->
            state.copy(
                draft =
                    state.draft?.copy(
                        widgetLabel = label.trim().take(ShortcutDefinition.MAX_WIDGET_LABEL_LENGTH).ifBlank { null },
                    ),
            )
        }

    fun showTriggerConfiguration() =
        _state.update {
            it.copy(triggerConfigurationVisible = true, libraryVisible = false, selectedNodeId = null)
        }

    fun hideTriggerConfiguration() = _state.update { it.copy(triggerConfigurationVisible = false) }

    fun updateTrigger(trigger: Trigger) =
        _state.update { state ->
            state.copy(draft = state.draft?.copy(trigger = trigger))
        }

    fun updateAction(
        nodeId: NodeId,
        action: ShortcutAction,
    ) = updateNodes { nodes ->
        nodes.map { if (it.id == nodeId) it.copy(action = action) else it }
    }

    fun addAction(descriptor: ActionDescriptor) {
        _state.update { current ->
            val draft = current.draft ?: return@update current
            val index = current.insertionIndex.coerceIn(0, draft.nodes.size)
            val defaultAction = descriptor.createDefault()
            val action =
                if (defaultAction is ShortcutAction.WaitForMediaPlayback) {
                    val previous = draft.nodes.getOrNull(index - 1)?.action as? ShortcutAction.OpenApplication
                    defaultAction.copy(
                        packageName = previous?.packageName ?: defaultAction.packageName,
                        applicationLabel = previous?.applicationLabel,
                    )
                } else {
                    defaultAction
                }
            val nodes = draft.nodes.toMutableList().apply { add(index, ActionNode(action = action)) }
            current.copy(draft = draft.copy(nodes = nodes), libraryVisible = false)
        }
    }

    fun remove(nodeId: NodeId) {
        updateNodes { nodes -> nodes.filterNot { it.id == nodeId } }
        _state.update { state ->
            if (state.selectedNodeId == nodeId) state.copy(selectedNodeId = null) else state
        }
    }

    fun duplicate(nodeId: NodeId) =
        updateNodes { nodes ->
            val index = nodes.indexOfFirst { it.id == nodeId }
            if (index < 0) {
                nodes
            } else {
                nodes.toMutableList().apply {
                    add(index + 1, nodes[index].copy(id = NodeId.new()))
                }
            }
        }

    fun toggle(nodeId: NodeId) =
        updateNodes { nodes ->
            nodes.map { if (it.id == nodeId) it.copy(enabled = !it.enabled) else it }
        }

    fun cycleDelayBefore(nodeId: NodeId) =
        updateNodes { nodes ->
            nodes.map {
                if (it.id ==
                    nodeId
                ) {
                    it.copy(
                        delayBeforeMillis =
                            when (it.delayBeforeMillis) {
                                0L -> 2_000L
                                2_000L -> 5_000L
                                else -> 0L
                            },
                    )
                } else {
                    it
                }
            }
        }

    fun toggleContinueOnError(nodeId: NodeId) =
        updateNodes { nodes ->
            nodes.map {
                if (it.id ==
                    nodeId
                ) {
                    it.copy(
                        errorStrategy = if (it.errorStrategy is com.branlly.pocket.domain.model.ErrorStrategy.Stop) com.branlly.pocket.domain.model.ErrorStrategy.Continue else com.branlly.pocket.domain.model.ErrorStrategy.Stop,
                    )
                } else {
                    it
                }
            }
        }

    fun move(
        nodeId: NodeId,
        delta: Int,
    ) {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            val nodes = draft.nodes
            val from = nodes.indexOfFirst { it.id == nodeId }
            val to = (from + delta).coerceIn(0, nodes.lastIndex)
            if (from < 0 || from == to) return@update state
            val reordered = nodes.toMutableList().apply { add(to, removeAt(from)) }
            state.copy(draft = draft.copy(nodes = reordered))
        }
    }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        val issues = validator.validate(draft)
        if (issues.isNotEmpty()) {
            _state.update { it.copy(message = issues.first().message) }
            return
        }
        viewModelScope.launch {
            store.save(draft.copy(name = draft.name.trim()))
            BranllyPocketWidget.refreshAll(getApplication())
            _state.update { state -> EditorUiState(savedShortcuts = state.savedShortcuts, message = "Raccourci enregistré.") }
        }
    }

    fun editSaved(shortcut: ShortcutDefinition) {
        _state.update {
            it.copy(screen = Screen.EDITOR, draft = shortcut, message = null)
        }
    }

    fun deleteSaved(id: ShortcutId) {
        viewModelScope.launch {
            store.delete(id)
            BranllyPocketWidget.refreshAll(getApplication())
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun rename(name: String) {
        if (name.length <= ShortcutDefinition.MAX_NAME_LENGTH) {
            _state.update { it.copy(draft = it.draft?.copy(name = name)) }
        }
    }

    private fun openEditor(
        trigger: Trigger,
        configureTrigger: Boolean = false,
    ) {
        _state.value =
            EditorUiState(
                screen = Screen.EDITOR,
                draft = ShortcutDefinition(name = "Nouveau raccourci", trigger = trigger, nodes = emptyList()),
                triggerConfigurationVisible = configureTrigger,
            )
    }

    private fun updateNodes(transform: (List<ActionNode>) -> List<ActionNode>) {
        _state.update { state -> state.copy(draft = state.draft?.let { it.copy(nodes = transform(it.nodes)) }) }
    }
}

data class EditorUiState(
    val screen: Screen = Screen.HOME,
    val draft: ShortcutDefinition? = null,
    val libraryVisible: Boolean = false,
    val insertionIndex: Int = 0,
    val selectedNodeId: NodeId? = null,
    val triggerConfigurationVisible: Boolean = false,
    val presentationPickerVisible: Boolean = false,
    val savedShortcuts: List<ShortcutDefinition> = emptyList(),
    val message: String? = null,
) {
    val selectedNode: ActionNode?
        get() = draft?.nodes?.find { it.id == selectedNodeId }

    val suggestions: List<ActionDescriptor>
        get() = emptyList()
}

enum class Screen { HOME, START, GUIDED_TRIGGER, ACTION_CHOICE, BLUEPRINTS, EDITOR }

private fun Trigger.hasConfiguration(): Boolean =
    when (this) {
        Trigger.ManualButton, Trigger.Widget, Trigger.QuickTile -> false
        else -> true
    }
