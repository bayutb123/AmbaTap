package com.ambacoding.ambatap.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambacoding.ambatap.data.transfer.InvalidMacroFileException
import com.ambacoding.ambatap.data.transfer.MacroFiles
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val macroFiles: MacroFiles,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setCountdownSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepository.setCountdownSeconds(seconds)
    }

    fun setShowTouchIndicator(show: Boolean) = viewModelScope.launch {
        settingsRepository.setShowTouchIndicator(show)
    }

    fun setPanelIdleOpacity(opacity: Float) = viewModelScope.launch {
        settingsRepository.setPanelIdleOpacity(opacity)
    }

    fun exportAll(uri: Uri) = viewModelScope.launch {
        _messages.send(
            try {
                "${macroFiles.exportAll(uri)} macro diekspor"
            } catch (e: IOException) {
                "Gagal mengekspor: ${e.message}"
            },
        )
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _messages.send(
            try {
                "${macroFiles.import(uri)} macro diimpor"
            } catch (e: InvalidMacroFileException) {
                e.message ?: "File tidak valid"
            } catch (e: IOException) {
                "Gagal membaca file: ${e.message}"
            },
        )
    }
}
