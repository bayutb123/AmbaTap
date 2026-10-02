package com.bayutb123.ambatap.service

import com.bayutb123.ambatap.engine.player.InputController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Titik temu antara accessibility service (dibuat oleh sistem) dan bagian app lain.
 * [controller] bernilai non-null selama service tersambung.
 */
@Singleton
class ServiceBridge @Inject constructor() {
    private val _controller = MutableStateFlow<InputController?>(null)
    val controller: StateFlow<InputController?> = _controller.asStateFlow()

    fun attach(controller: InputController) {
        _controller.value = controller
    }

    fun detach(controller: InputController) {
        _controller.compareAndSet(controller, null)
    }
}
