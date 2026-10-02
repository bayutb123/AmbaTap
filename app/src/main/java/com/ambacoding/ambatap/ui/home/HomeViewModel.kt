package com.ambacoding.ambatap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.service.ServiceBridge
import com.ambacoding.ambatap.service.overlay.FloatingPanelState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MacroItem(val id: Long, val name: String, val actionCount: Int)

data class HomeUiState(
    val macros: List<MacroItem> = emptyList(),
    val isLoading: Boolean = true,
    val serviceConnected: Boolean = false,
    val panelRequested: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    macroRepository: MacroRepository,
    serviceBridge: ServiceBridge,
    private val panelState: FloatingPanelState,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        macroRepository.observeMacros(),
        serviceBridge.controller,
        panelState.requested,
    ) { macros, controller, panelRequested ->
        HomeUiState(
            macros = macros.map { MacroItem(it.id, it.name, it.actions.size) },
            isLoading = false,
            serviceConnected = controller != null,
            panelRequested = panelRequested,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Panel tampil; rekaman dimulai dari tombol merah di panel setelah user membuka aplikasi tujuan. */
    fun prepareRecording() = panelState.show()

    fun togglePanel() {
        if (panelState.requested.value) panelState.hide() else panelState.show()
    }
}
