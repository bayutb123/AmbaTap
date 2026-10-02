package com.bayutb123.ambatap.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bayutb123.ambatap.domain.model.Macro
import com.bayutb123.ambatap.engine.player.MacroPlayer
import com.bayutb123.ambatap.engine.player.PlaybackState
import com.bayutb123.ambatap.service.ServiceBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    private val player: MacroPlayer,
    serviceBridge: ServiceBridge,
) : ViewModel() {

    val playback: StateFlow<PlaybackState> = player.state

    val serviceConnected: StateFlow<Boolean> = serviceBridge.controller
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), serviceBridge.controller.value != null)

    fun play(macro: Macro, startDelayMs: Long = 0) = player.play(macro, startDelayMs)

    fun pause() = player.pause()

    fun resume() = player.resume()

    fun stop() = player.stop()
}
