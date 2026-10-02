package com.ambacoding.ambatap.service.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.MainActivity
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.GestureSpec
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.service.realScreenSize
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Mengelola jendela panel melayang: tampil bila diminta user atau selama macro aktif,
 * bisa digeser, dan sementara ditembus saat gesture macro jatuh di atasnya.
 */
class PanelController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: MacroPlayer,
    private val panelState: FloatingPanelState,
    settingsRepository: SettingsRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private val settings = settingsRepository.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val window: OverlayWindow by lazy { createWindow() }
    private var created = false

    fun start() {
        scope.launch {
            combine(
                panelState.requested,
                player.state.map { it.isActive }.distinctUntilChanged(),
            ) { requested, active -> requested || active }
                .distinctUntilChanged()
                .collect { visible -> if (visible) show() else hide() }
        }
    }

    fun destroy() {
        if (created) window.destroy()
    }

    /**
     * Menjalankan [block] dengan panel tidak bisa disentuh bila salah satu titik [gesture]
     * berada di atas panel; tanpa ini, gesture macro akan menekan tombol panel.
     */
    suspend fun <T> passThrough(gesture: GestureSpec, block: suspend () -> T): T {
        val bounds = if (created) window.boundsOnScreen() else null
        val covered = bounds != null &&
            gesture.intersects(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!covered) return block()

        window.setTouchable(false)
        delay(PASS_THROUGH_SETTLE_MS)
        return try {
            block()
        } finally {
            window.setTouchable(true)
        }
    }

    private fun show() {
        window.show()
    }

    private fun hide() {
        if (created) window.hide()
    }

    private fun createWindow(): OverlayWindow {
        created = true
        val screen = context.realScreenSize()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screen.widthPx - (INITIAL_RIGHT_INSET_DP * density).roundToInt()
            y = (screen.heightPx * INITIAL_Y_FRACTION).roundToInt()
        }

        val themed = ContextThemeWrapper(context, R.style.Theme_AmbaTap)
        return OverlayWindow(themed, windowManager, params) {
            AmbaTapTheme(darkTheme = false) {
                val playback by player.state.collectAsStateWithLifecycle()
                val lastMacro by player.lastMacro.collectAsStateWithLifecycle()
                val appSettings by settings.collectAsStateWithLifecycle()
                FloatingPanel(
                    state = playback,
                    canPlay = lastMacro != null,
                    idleOpacity = appSettings.panelIdleOpacity,
                    onDrag = { moveBy(it.x, it.y) },
                    onPlay = {
                        lastMacro?.let { player.play(it, appSettings.countdownSeconds * 1_000L) }
                    },
                    onPause = player::pause,
                    onResume = player::resume,
                    onStop = player::stop,
                    onOpenApp = ::openApp,
                    onClose = panelState::hide,
                )
            }
        }.also { overlay ->
            // Saat ukuran panel berubah (pil ↔ kartu), pastikan tetap di dalam layar.
            overlay.view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> moveBy(0f, 0f) }
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val screen = context.realScreenSize()
        val view = window.view
        val params = window.params
        val newX = (params.x + dx.roundToInt()).coerceIn(0, (screen.widthPx - view.width).coerceAtLeast(0))
        val newY = (params.y + dy.roundToInt()).coerceIn(0, (screen.heightPx - view.height).coerceAtLeast(0))
        // Jangan update bila tidak berubah: dipanggil dari layout listener, update memicu layout lagi.
        if (newX == params.x && newY == params.y) return
        window.update {
            x = newX
            y = newY
        }
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private companion object {
        const val INITIAL_RIGHT_INSET_DP = 84
        const val INITIAL_Y_FRACTION = 0.3f

        /** Waktu agar perubahan flag jendela sampai ke input dispatcher sebelum gesture dikirim. */
        const val PASS_THROUGH_SETTLE_MS = 50L
    }
}
