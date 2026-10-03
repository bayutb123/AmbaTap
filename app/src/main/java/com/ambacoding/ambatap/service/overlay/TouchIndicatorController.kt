package com.ambacoding.ambatap.service.overlay

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.GestureSpec
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.ui.theme.PlayBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lingkaran sesaat di posisi setiap gesture selama macro diputar (setelan "Tampilkan
 * indikator sentuhan"). Jendelanya tidak bisa disentuh, jadi gesture menembus ke aplikasi.
 */
class TouchIndicatorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: MacroPlayer,
    settingsRepository: SettingsRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    private var window: OverlayWindow? = null

    fun start() {
        scope.launch {
            combine(
                settings.map { it.showTouchIndicator },
                player.state.map { it.isActive },
            ) { enabled, playing -> enabled && playing }
                .distinctUntilChanged()
                .collect { visible -> if (visible) show() else window?.hide() }
        }
    }

    fun destroy() {
        window?.destroy()
        window = null
    }

    private fun show() {
        val overlay = window ?: createWindow().also { window = it }
        overlay.show()
    }

    private fun createWindow(): OverlayWindow {
        val params = OverlayWindow.fullScreenParams(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        val themed = ContextThemeWrapper(context, R.style.Theme_AmbaTap)
        return OverlayWindow(themed, windowManager, params) {
            val ripples = remember { mutableStateListOf<Ripple>() }
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

            LaunchedEffect(Unit) {
                player.gestures.collect { gesture ->
                    val ripple = Ripple(gesture)
                    ripples += ripple
                    launch {
                        ripple.progress.animateTo(1f, tween(RIPPLE_MS, easing = LinearEasing))
                        ripples -= ripple
                    }
                }
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords = it },
            ) {
                val layout = coords ?: return@Canvas
                ripples.forEach { ripple ->
                    val t = ripple.progress.value
                    val alpha = 1f - t
                    ripple.gesture.strokes.forEach { stroke ->
                        val points = stroke.points.map { layout.screenToLocal(Offset(it.x, it.y)) }
                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, PlayBlue, alpha = alpha * 0.6f, style = Stroke(4.dp.toPx()))
                        }
                        val center = points.first()
                        drawCircle(PlayBlue, radius = 10.dp.toPx(), center = center, alpha = alpha)
                        drawCircle(
                            PlayBlue,
                            radius = (14 + 18 * t).dp.toPx(),
                            center = center,
                            alpha = alpha,
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                }
            }
        }
    }

    private class Ripple(val gesture: GestureSpec) {
        val progress = Animatable(0f)
    }

    private companion object {
        const val RIPPLE_MS = 450
    }
}
