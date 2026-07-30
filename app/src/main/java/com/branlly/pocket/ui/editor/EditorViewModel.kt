package com.branlly.pocket.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.branlly.pocket.data.SavedShortcutStore
import com.branlly.pocket.domain.catalog.ActionDescriptor
import com.branlly.pocket.domain.execution.RoutineValidator
import com.branlly.pocket.domain.model.ActionNode
import com.branlly.pocket.domain.model.EditorMode
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.NodeId
import com.branlly.pocket.domain.model.ShortcutAccentColor
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.ShortcutCategory
import com.branlly.pocket.domain.model.ShortcutDefinition
import com.branlly.pocket.domain.model.ShortcutId
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.platform.android.BranllyPocketWidget
import com.branlly.pocket.platform.android.actions.AndroidActionRegistry
import com.branlly.pocket.platform.android.actions.AndroidActionValidationContext
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

    fun showHome() = _state.update { state -> EditorUiState(screen = Screen.HOME, savedShortcuts = state.savedShortcuts) }

    fun showStart() = startFree()

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
        _state.update { current ->
            val original = current.draft?.nodes?.firstOrNull { it.id == nodeId } ?: return@update current
            current.copy(selectedNodeId = nodeId, actionDraft = original, libraryVisible = false)
        }

    fun updateActionDraft(action: ShortcutAction) =
        _state.update { current -> current.actionDraft?.let { current.copy(actionDraft = it.copy(action = action)) } ?: current }

    fun confirmActionDraft() =
        _state.update { current ->
            val pending = current.actionDraft ?: return@update current
            val draft = current.draft ?: return@update current
            val nodes = ActionDraftTransaction.commit(draft.nodes, pending, current.insertionIndex)
            current.copy(draft = draft.copy(nodes = nodes), selectedNodeId = null, actionDraft = null)
        }

    fun hideConfiguration() = _state.update { it.copy(selectedNodeId = null, actionDraft = null) }

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
            val inserted = ActionNode(action = action)
            current.copy(
                libraryVisible = false,
                selectedNodeId = inserted.id,
                actionDraft = inserted,
                insertionIndex = index,
            )
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
            store.save(draft.copy(name = draft.name.trim(), mode = EditorMode.ADVANCED))
            BranllyPocketWidget.refreshAll(getApplication())
            _state.update { state -> EditorUiState(savedShortcuts = state.savedShortcuts, message = "Raccourci enregistré.") }
        }
    }

    fun editSaved(shortcut: ShortcutDefinition) {
        _state.update {
            it.copy(screen = Screen.EDITOR, draft = shortcut.copy(mode = EditorMode.ADVANCED), message = null)
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
                draft =
                    ShortcutDefinition(
                        name = "Nouveau raccourci",
                        trigger = trigger,
                        nodes = emptyList(),
                        mode = EditorMode.ADVANCED,
                    ),
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
    val actionDraft: ActionNode? = null,
    val triggerConfigurationVisible: Boolean = false,
    val presentationPickerVisible: Boolean = false,
    val savedShortcuts: List<ShortcutDefinition> = emptyList(),
    val message: String? = null,
) {
    val selectedNode: ActionNode?
        get() = actionDraft

    val suggestions: List<ActionDescriptor>
        get() = emptyList()
}

enum class Screen { HOME, EDITOR }

private fun Trigger.hasConfiguration(): Boolean =
    when (this) {
        Trigger.ManualButton, Trigger.Widget, Trigger.QuickTile -> false
        else -> true
    }
