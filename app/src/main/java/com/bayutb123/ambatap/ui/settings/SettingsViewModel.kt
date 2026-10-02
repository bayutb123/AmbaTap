package com.bayutb123.ambatap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bayutb123.ambatap.domain.model.AppSettings
import com.bayutb123.ambatap.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setCountdownSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepository.setCountdownSeconds(seconds)
    }

    fun setShowTouchIndicator(show: Boolean) = viewModelScope.launch {
        settingsRepository.setShowTouchIndicator(show)
    }

    fun setPanelIdleOpacity(opacity: Float) = viewModelScope.launch {
        settingsRepository.setPanelIdleOpacity(opacity)
    }
}
