package com.bayutb123.ambatap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bayutb123.ambatap.domain.repository.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MacroItem(val id: Long, val name: String, val actionCount: Int)

data class HomeUiState(
    val macros: List<MacroItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    macroRepository: MacroRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = macroRepository.observeMacros()
        .map { macros ->
            HomeUiState(
                macros = macros.map { MacroItem(it.id, it.name, it.actions.size) },
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
