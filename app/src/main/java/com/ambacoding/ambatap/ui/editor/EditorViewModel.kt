package com.ambacoding.ambatap.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.move
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.service.overlay.PointPickerRequests
import com.ambacoding.ambatap.ui.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val macro: Macro? = null,
    val dirty: Boolean = false,
    val notFound: Boolean = false,
)

sealed interface EditorEvent {
    data object Minimize : EditorEvent

    data class Message(val text: String) : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MacroRepository,
    private val settingsRepository: SettingsRepository,
    private val player: MacroPlayer,
    private val pickerRequests: PointPickerRequests,
) : ViewModel() {

    private val macroId = savedStateHandle.toRoute<EditorRoute>().macroId

    private val draft = MutableStateFlow<Macro?>(null)
    private val dirty = MutableStateFlow(false)
    private val notFound = MutableStateFlow(false)

    val uiState: StateFlow<EditorUiState> = combine(draft, dirty, notFound) { macro, isDirty, missing ->
        EditorUiState(macro = macro, dirty = isDirty, notFound = missing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Perubahan dari luar (mis. posisi diatur di overlay) diambil selama belum ada edit lokal.
            repository.observeMacro(macroId).collect { stored ->
                if (stored == null) {
                    notFound.value = draft.value == null
                } else if (!dirty.value) {
                    draft.value = stored
                }
            }
        }
    }

    private fun edit(transform: (Macro) -> Macro) {
        draft.update { it?.let(transform) }
        dirty.value = true
    }

    private fun editConfig(transform: (PlaybackConfig) -> PlaybackConfig) =
        edit { it.copy(config = transform(it.config)) }

    private fun editActions(transform: (List<MacroAction>) -> List<MacroAction>) =
        edit { it.copy(actions = transform(it.actions)) }

    fun setName(name: String) = edit { it.copy(name = name) }

    fun setConfig(config: PlaybackConfig) = editConfig { config }

    fun updateAction(index: Int, action: MacroAction) =
        editActions { list -> list.mapIndexed { i, a -> if (i == index) action else a } }

    fun moveAction(index: Int, delta: Int) = editActions { it.move(index, index + delta) }

    fun deleteAction(index: Int) = editActions { list -> list.filterIndexed { i, _ -> i != index } }

    fun addAction(action: MacroAction) = editActions { it + action }

    fun save() {
        val macro = draft.value ?: return
        val toSave = macro.copy(name = macro.name.trim().ifEmpty { "Tanpa nama" }, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            repository.save(toSave)
            draft.value = toSave
            dirty.value = false
            _events.send(EditorEvent.Message("Tersimpan"))
        }
    }

    fun discardChanges() {
        dirty.value = false
        viewModelScope.launch { draft.value = repository.getMacro(macroId) }
    }

    /**
     * Menyimpan perubahan lalu membuka overlay pemilih titik di atas aplikasi sebelumnya.
     * Hasil dari overlay disimpan ke database dan masuk lagi ke editor lewat [MacroRepository.observeMacro].
     */
    fun editOnScreen() {
        val macro = draft.value ?: return
        viewModelScope.launch {
            val saved = macro.copy(updatedAt = System.currentTimeMillis())
            repository.save(saved)
            dirty.value = false
            pickerRequests.open(saved)
            _events.send(EditorEvent.Minimize)
        }
    }

    /** Menyimpan perubahan lalu memutar macro di aplikasi sebelumnya setelah hitung mundur. */
    fun play() {
        val macro = draft.value ?: return
        viewModelScope.launch {
            if (dirty.value) {
                repository.save(macro.copy(updatedAt = System.currentTimeMillis()))
                dirty.value = false
            }
            val countdownMs = settingsRepository.settings.first().countdownSeconds * 1_000L
            player.play(macro, countdownMs)
            val error = player.state.value.error
            _events.send(
                if (error == null) EditorEvent.Minimize else EditorEvent.Message("Aktifkan layanan aksesibilitas dulu"),
            )
        }
    }
}
