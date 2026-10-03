package com.ambacoding.ambatap.service.overlay

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Permintaan user untuk menampilkan panel melayang. Panel juga tampil otomatis
 * selama macro berjalan, terlepas dari nilai [requested].
 */
@Singleton
class FloatingPanelState @Inject constructor() {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun show() {
        _requested.value = true
    }

    fun hide() {
        _requested.value = false
    }
}
