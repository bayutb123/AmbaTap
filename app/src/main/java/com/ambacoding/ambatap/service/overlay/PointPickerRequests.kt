package com.ambacoding.ambatap.service.overlay

import com.ambacoding.ambatap.domain.model.Macro
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Permintaan dari aplikasi untuk membuka overlay pemilih titik atas sebuah macro
 * (auto clicker baru, atau macro yang posisinya ingin diatur di layar).
 */
@Singleton
class PointPickerRequests @Inject constructor() {
    private val _session = MutableStateFlow<Macro?>(null)
    val session: StateFlow<Macro?> = _session.asStateFlow()

    fun open(macro: Macro) {
        _session.value = macro
    }

    fun close() {
        _session.value = null
    }
}
