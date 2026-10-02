package com.ambacoding.ambatap.service.overlay

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.service.realScreenSize
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Overlay pemilih titik untuk auto clicker manual dan untuk mengatur posisi aksi macro
 * langsung di atas aplikasi tujuan. Disembunyikan sementara selama uji putar.
 */
class PointPickerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val requests: PointPickerRequests,
    private val player: MacroPlayer,
    private val repository: MacroRepository,
    settingsRepository: SettingsRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private var session: Macro? = null
    private val actions = mutableStateListOf<MacroAction>()
    private var config by mutableStateOf(PlaybackConfig())
    private var selected by mutableStateOf<Int?>(null)

    private var window: OverlayWindow? = null

    fun start() {
        scope.launch {
            requests.session.collect { macro -> if (macro != null && macro !== session) load(macro) }
        }
        scope.launch {
            combine(
                requests.session.map { it != null },
                player.state.map { it.isActive },
            ) { open, playing -> open && !playing }
                .distinctUntilChanged()
                .collect { visible -> if (visible) show() else window?.hide() }
        }
    }

    fun destroy() {
        window?.destroy()
        window = null
        requests.close()
    }

    private fun load(macro: Macro) {
        session = macro
        actions.clear()
        actions.addAll(macro.actions)
        config = macro.config
        selected = null
    }

    private fun current(): Macro? = session?.copy(actions = actions.toList(), config = config)

    private fun show() {
        val overlay = window ?: createWindow().also { window = it }
        overlay.show()
    }

    private fun createWindow(): OverlayWindow {
        val themed = ContextThemeWrapper(context, R.style.Theme_AmbaTap)
        return OverlayWindow(themed, windowManager, OverlayWindow.fullScreenParams()) {
            AmbaTapTheme(darkTheme = false) {
                PointPicker(
                    name = session?.name.orEmpty(),
                    actions = actions,
                    repeat = config.repeat,
                    selected = selected,
                    screen = context.realScreenSize(),
                    callbacks = PickerActions(
                        onSelect = { selected = it },
                        onChange = { index, action -> if (index in actions.indices) actions[index] = action },
                        onDelete = { index ->
                            if (index in actions.indices) actions.removeAt(index)
                            selected = null
                        },
                        onAddTap = {
                            actions += MacroAction.Tap(0.5f, 0.5f, delayBeforeMs = DEFAULT_INTERVAL_MS)
                            selected = actions.lastIndex
                        },
                        onAddSwipe = {
                            actions += MacroAction.Swipe(
                                points = listOf(TimedPoint(0.5f, 0.65f, 0), TimedPoint(0.5f, 0.35f, 300)),
                                durationMs = 300,
                                delayBeforeMs = DEFAULT_INTERVAL_MS,
                            )
                            selected = actions.lastIndex
                        },
                        onCycleRepeat = {
                            val index = RepeatCycle.indexOf(config.repeat)
                            config = config.copy(repeat = RepeatCycle[(index + 1) % RepeatCycle.size])
                        },
                        onTestPlay = ::testPlay,
                        onSave = ::save,
                        onClose = requests::close,
                    ),
                )
            }
        }
    }

    private fun testPlay() {
        val macro = current() ?: return
        player.play(macro, startDelayMs = settings.value.countdownSeconds * 1_000L)
    }

    private fun save() {
        val macro = current() ?: return
        scope.launch {
            val saved = macro.copy(updatedAt = System.currentTimeMillis())
            val id = repository.save(saved)
            player.load(saved.copy(id = id))
            session = null
            requests.close()
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 100L

        /** Urutan pilihan tombol "Ulang" di toolbar. */
        val RepeatCycle = listOf(RepeatMode.Count(1), RepeatMode.Count(10), RepeatMode.Count(100), RepeatMode.Infinite)
    }
}
