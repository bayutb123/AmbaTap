package com.ambacoding.ambatap.engine.recorder

import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.withDelay
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecordingState(
    val status: Status = Status.IDLE,
    val actions: List<MacroAction> = emptyList(),
    val screen: ScreenInfo? = null,
    val startedAtMs: Long = 0,
    /** Waktu mulai jeda yang sedang berlangsung, atau `null`. */
    val pausedAtMs: Long? = null,
    val pausedTotalMs: Long = 0,
    val finishedAtMs: Long? = null,
) {
    enum class Status { IDLE, RECORDING, PAUSED, REVIEW }

    /** Sedang merekam atau dijeda (overlay perekaman tampil). */
    val isCapturing: Boolean get() = status == Status.RECORDING || status == Status.PAUSED

    /** Lama perekaman efektif (tanpa jeda) pada waktu [nowMs]. */
    fun elapsedMs(nowMs: Long): Long {
        val end = finishedAtMs ?: pausedAtMs ?: nowMs
        return (end - startedAtMs - pausedTotalMs).coerceAtLeast(0)
    }
}

/**
 * Menyusun macro dari gesture yang sudah diteruskan ke aplikasi. Semua waktu memakai
 * jam monotonic yang sama (`SystemClock.uptimeMillis` di perangkat) dan dikirim pemanggil.
 */
@Singleton
class MacroRecorder @Inject constructor() {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Akhir gesture terakhir, sudah digeser maju sebesar lama jeda sesudahnya. */
    private var lastEndMs: Long? = null

    fun start(screen: ScreenInfo, nowMs: Long): Boolean {
        if (_state.value.status != RecordingState.Status.IDLE) return false
        lastEndMs = null
        _state.value = RecordingState(
            status = RecordingState.Status.RECORDING,
            screen = screen,
            startedAtMs = nowMs,
        )
        return true
    }

    fun pause(nowMs: Long) {
        if (_state.value.status != RecordingState.Status.RECORDING) return
        _state.update { it.copy(status = RecordingState.Status.PAUSED, pausedAtMs = nowMs) }
    }

    fun resume(nowMs: Long) {
        val current = _state.value
        val pausedAt = current.pausedAtMs ?: return
        if (current.status != RecordingState.Status.PAUSED) return
        val pausedFor = (nowMs - pausedAt).coerceAtLeast(0)
        // Waktu jeda tidak masuk ke jeda antar aksi.
        lastEndMs = lastEndMs?.plus(pausedFor)
        _state.value = current.copy(
            status = RecordingState.Status.RECORDING,
            pausedAtMs = null,
            pausedTotalMs = current.pausedTotalMs + pausedFor,
        )
    }

    /**
     * Menambahkan aksi yang sudah diteruskan ke aplikasi pada [startMs]..[endMs]. Jeda sebelum
     * aksi dihitung dari akhir aksi sebelumnya, sesuai waktu yang benar-benar dialami aplikasi.
     */
    fun append(action: MacroAction, startMs: Long, endMs: Long) {
        if (!_state.value.isCapturing) return
        val delay = lastEndMs?.let { (startMs - it).coerceAtLeast(0) } ?: 0
        lastEndMs = endMs
        val withDelay = action.withDelay(delay)
        _state.update { it.copy(actions = it.actions + withDelay) }
    }

    /** Selesai merekam: masuk ke tinjauan, atau langsung batal bila belum ada aksi. */
    fun finish(nowMs: Long) {
        val current = _state.value
        if (!current.isCapturing) return
        if (current.actions.isEmpty()) {
            reset()
            return
        }
        val pausedFor = current.pausedAtMs?.let { nowMs - it } ?: 0
        _state.value = current.copy(
            status = RecordingState.Status.REVIEW,
            pausedAtMs = null,
            pausedTotalMs = current.pausedTotalMs + pausedFor,
            finishedAtMs = nowMs,
        )
    }

    /** Kembali ke IDLE: membatalkan rekaman, membuang draft, atau menutup setelah disimpan. */
    fun reset() {
        lastEndMs = null
        _state.value = RecordingState()
    }

    fun buildDraft(name: String, config: PlaybackConfig, createdAt: Long): Macro? {
        val current = _state.value
        if (current.status != RecordingState.Status.REVIEW) return null
        return Macro(
            name = name,
            actions = current.actions,
            screen = current.screen ?: return null,
            config = config,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }
}
