package com.ambacoding.ambatap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.totalDurationMs
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.player.PlaybackState
import com.ambacoding.ambatap.service.ServiceBridge
import com.ambacoding.ambatap.service.overlay.FloatingPanelState
import com.ambacoding.ambatap.service.overlay.PointPickerRequests
import com.ambacoding.ambatap.ui.components.defaultMacroName
import com.ambacoding.ambatap.ui.components.label
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MacroItem(
    val id: Long,
    val name: String,
    val actionCount: Int,
    val durationMs: Long,
    val repeatLabel: String,
)

data class HomeUiState(
    val macros: List<MacroItem> = emptyList(),
    val isLoading: Boolean = true,
    val serviceConnected: Boolean = false,
    val panelRequested: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
)

sealed interface HomeEvent {
    /** Macro mulai diputar: AmbaTap diminimalkan agar macro berjalan di aplikasi sebelumnya. */
    data object Minimize : HomeEvent

    data class Deleted(val macro: Macro) : HomeEvent

    data class Message(val text: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val macroRepository: MacroRepository,
    private val settingsRepository: SettingsRepository,
    private val player: MacroPlayer,
    serviceBridge: ServiceBridge,
    private val panelState: FloatingPanelState,
    private val pickerRequests: PointPickerRequests,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        macroRepository.observeMacros(),
        serviceBridge.controller,
        panelState.requested,
        player.state,
    ) { macros, controller, panelRequested, playback ->
        HomeUiState(
            macros = macros.map { it.toItem() },
            isLoading = false,
            serviceConnected = controller != null,
            panelRequested = panelRequested,
            playback = playback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** Panel tampil; rekaman dimulai dari tombol merah di panel setelah user membuka aplikasi tujuan. */
    fun prepareRecording() = panelState.show()

    /** Membuka overlay pemilih titik untuk auto clicker baru (satu titik di tengah, ulang terus). */
    fun startAutoClicker(screen: ScreenInfo) {
        val now = System.currentTimeMillis()
        pickerRequests.open(
            Macro(
                name = defaultMacroName("Auto clicker", now),
                actions = listOf(MacroAction.Tap(0.5f, 0.5f, delayBeforeMs = 100)),
                screen = screen,
                config = PlaybackConfig(repeat = RepeatMode.Infinite),
                createdAt = now,
                updatedAt = now,
            ),
        )
        _events.trySend(HomeEvent.Minimize)
    }

    fun togglePanel() {
        if (panelState.requested.value) panelState.hide() else panelState.show()
    }

    fun play(id: Long) {
        viewModelScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            val countdownMs = settingsRepository.settings.first().countdownSeconds * 1_000L
            player.play(macro, countdownMs)
            if (player.state.value.error == null) {
                _events.send(HomeEvent.Minimize)
            } else {
                _events.send(HomeEvent.Message("Aktifkan layanan aksesibilitas dulu"))
            }
        }
    }

    fun stop() = player.stop()

    fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            macroRepository.save(macro.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            val now = System.currentTimeMillis()
            macroRepository.save(macro.copy(id = 0, name = "${macro.name} (salinan)", createdAt = now, updatedAt = now))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val macro = macroRepository.getMacro(id) ?: return@launch
            if (player.state.value.macroId == id) player.stop()
            macroRepository.delete(id)
            _events.send(HomeEvent.Deleted(macro))
        }
    }

    fun undoDelete(macro: Macro) {
        viewModelScope.launch { macroRepository.save(macro) }
    }

    private fun Macro.toItem() = MacroItem(
        id = id,
        name = name,
        actionCount = actions.size,
        durationMs = actions.totalDurationMs(),
        repeatLabel = config.repeat.label(),
    )
}
