package com.ambacoding.ambatap.service.trigger

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.recorder.MacroRecorder
import com.ambacoding.ambatap.engine.recorder.RecordingState
import com.ambacoding.ambatap.service.ServiceBridge
import com.ambacoding.ambatap.service.overlay.PointPickerRequests
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Memutar macro dari jadwal atau pemicu buka aplikasi, bila keadaan memungkinkan. */
@Singleton
class TriggerLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: ServiceBridge,
    private val player: MacroPlayer,
    private val recorder: MacroRecorder,
    private val pickerRequests: PointPickerRequests,
    private val settingsRepository: SettingsRepository,
    private val notifier: TriggerNotifier,
) {
    /**
     * @param notifyOnSkip beri tahu lewat notifikasi bila macro batal diputar. Pemicu buka
     *   aplikasi tidak memakainya agar tidak berisik saat AmbaTap sedang dipakai.
     * @return `true` bila macro mulai diputar.
     */
    suspend fun launch(macro: Macro, notifyOnSkip: Boolean): Boolean {
        val reason = skipReason()
        if (reason != null) {
            if (notifyOnSkip) notifier.notifySkipped(macro.id, macro.name, reason)
            return false
        }
        val countdownMs = settingsRepository.settings.first().countdownSeconds * 1_000L
        player.play(macro, countdownMs)
        return player.state.value.error == null
    }

    private fun skipReason(): String? {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return when {
            bridge.controller.value == null -> "Layanan aksesibilitas AmbaTap tidak aktif."
            !power.isInteractive || keyguard.isKeyguardLocked ->
                "Layar mati atau terkunci; gesture tidak bisa dijalankan di layar kunci."
            player.state.value.isActive -> "Macro lain sedang berjalan."
            recorder.state.value.status != RecordingState.Status.IDLE -> "Sedang merekam macro."
            pickerRequests.session.value != null -> "Pemilih titik sedang terbuka."
            else -> null
        }
    }
}
