package com.branlly.pocket.ui.editor

import androidx.lifecycle.ViewModel
import com.branlly.pocket.domain.catalog.ActionCatalog
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.catalog.LocalRecommendations
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.ConnectionEvent
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutCategory
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.VolumeStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun startFree() = openEditor(Trigger.ManualButton)

    fun startGuided(trigger: Trigger) = openEditor(trigger)

    fun useDepartureBlueprint() {
        _state.value = EditorUiState(
            screen = Screen.EDITOR,
            draft = ShortcutDefinition(
                name = "Je vais partir",
                category = ShortcutCategory.TRAVEL,
                trigger = Trigger.ManualButton,
                nodes = listOf(
                    ActionNode(
                        action = ShortcutAction.OpenRoute(
                            navigationPackage = InputValue.Fixed("com.google.android.apps.maps"),
                            destination = InputValue.Fixed(""),
                        ),
                    ),
                ),
            ),
        )
    }

    fun useCarBlueprint() {
        _state.value = EditorUiState(
            screen = Screen.EDITOR,
            draft = ShortcutDefinition(
                name = "Mode voiture",
                category = ShortcutCategory.TRAVEL,
                trigger = Trigger.Bluetooth(null, "Appareil à choisir", ConnectionEvent.CONNECTED),
                nodes = listOf(
                    ActionNode(action = ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70))),
                    ActionNode(action = ShortcutAction.OpenApplication(InputValue.AskAtRuntime)),
                    ActionNode(action = ShortcutAction.Wait(2_000)),
                    ActionNode(action = ShortcutAction.OpenRoute(InputValue.AskAtRuntime, InputValue.AskAtRuntime)),
                ),
            ),
        )
    }

    fun showStart() = _state.update { EditorUiState() }
    fun showGuidedTriggers() = _state.update { it.copy(screen = Screen.GUIDED_TRIGGER) }
    fun showBlueprints() = _state.update { it.copy(screen = Screen.BLUEPRINTS) }
    fun showLibrary(index: Int) = _state.update {
        it.copy(insertionIndex = index, libraryVisible = true, selectedNodeId = null)
    }
    fun hideLibrary() = _state.update { it.copy(libraryVisible = false) }
    fun showConfiguration(nodeId: NodeId) = _state.update {
        it.copy(selectedNodeId = nodeId, libraryVisible = false)
    }
    fun hideConfiguration() = _state.update { it.copy(selectedNodeId = null) }

    fun updateAction(nodeId: NodeId, action: ShortcutAction) = updateNodes { nodes ->
        nodes.map { if (it.id == nodeId) it.copy(action = action) else it }
    }

    fun addAction(descriptor: ActionDescriptor) {
        _state.update { current ->
            val draft = current.draft ?: return@update current
            val index = current.insertionIndex.coerceIn(0, draft.nodes.size)
            val nodes = draft.nodes.toMutableList().apply { add(index, ActionNode(action = descriptor.createDefault())) }
            current.copy(draft = draft.copy(nodes = nodes), libraryVisible = false)
        }
    }

    fun remove(nodeId: NodeId) {
        updateNodes { nodes -> nodes.filterNot { it.id == nodeId } }
        _state.update { state ->
            if (state.selectedNodeId == nodeId) state.copy(selectedNodeId = null) else state
        }
    }

    fun duplicate(nodeId: NodeId) = updateNodes { nodes ->
        val index = nodes.indexOfFirst { it.id == nodeId }
        if (index < 0) nodes else nodes.toMutableList().apply {
            add(index + 1, nodes[index].copy(id = NodeId.new()))
        }
    }

    fun toggle(nodeId: NodeId) = updateNodes { nodes ->
        nodes.map { if (it.id == nodeId) it.copy(enabled = !it.enabled) else it }
    }

    fun move(nodeId: NodeId, delta: Int) = updateNodes { nodes ->
        val from = nodes.indexOfFirst { it.id == nodeId }
        val to = (from + delta).coerceIn(0, nodes.lastIndex)
        if (from < 0 || from == to) nodes else nodes.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun rename(name: String) {
        if (name.length <= ShortcutDefinition.MAX_NAME_LENGTH) {
            _state.update { it.copy(draft = it.draft?.copy(name = name)) }
        }
    }

    private fun openEditor(trigger: Trigger) {
        _state.value = EditorUiState(
            screen = Screen.EDITOR,
            draft = ShortcutDefinition(name = "Nouveau raccourci", trigger = trigger, nodes = emptyList()),
        )
    }

    private fun updateNodes(transform: (List<ActionNode>) -> List<ActionNode>) {
        _state.update { state -> state.copy(draft = state.draft?.let { it.copy(nodes = transform(it.nodes)) }) }
    }
}

data class EditorUiState(
    val screen: Screen = Screen.START,
    val draft: ShortcutDefinition? = null,
    val libraryVisible: Boolean = false,
    val insertionIndex: Int = 0,
    val selectedNodeId: NodeId? = null,
) {
    val selectedNode: ActionNode?
        get() = draft?.nodes?.find { it.id == selectedNodeId }

    val suggestions: List<ActionDescriptor>
        get() {
            val current = draft ?: return emptyList()
            val kinds = current.nodes.lastOrNull()?.action?.let(LocalRecommendations::after)
                ?: LocalRecommendations.forTrigger(current.trigger)
            return kinds.mapNotNull { kind -> ActionCatalog.all.find { it.kind == kind } }.take(3)
        }
}

enum class Screen { START, GUIDED_TRIGGER, BLUEPRINTS, EDITOR }
