package com.bayutb123.ambatap.ui.playground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bayutb123.ambatap.engine.player.PlaybackState
import com.bayutb123.ambatap.engine.player.PlaybackState.Status
import com.bayutb123.ambatap.service.realScreenSize
import com.bayutb123.ambatap.ui.components.AmbaIcons
import com.bayutb123.ambatap.ui.theme.MonoStyle
import java.util.Locale

private enum class TouchType(val label: String) { TAP("Tap"), LONG_PRESS("Long press"), SWIPE("Swipe") }

/** Sentuhan yang diterima kanvas. [localPoints] untuk digambar, [screenStart] dalam piksel layar. */
private class RecordedTouch(
    val type: TouchType,
    val localPoints: List<Offset>,
    val screenStart: Offset,
    val durationMs: Long,
    /** Jarak ke target terdekat dalam piksel layar. */
    val errorPx: Float?,
)

private const val MAX_TOUCHES = 30
private const val OTHER_APP_DELAY_MS = 5_000L

@Composable
fun PlaygroundScreen(
    onBack: () -> Unit,
    viewModel: PlaygroundViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screen = remember(configuration) { context.realScreenSize() }
    val targetsOnScreen = remember(screen) {
        PlaygroundMacros.targets.map { (x, y) -> Offset(x * screen.widthPx, y * screen.heightPx) }
    }

    val touches = remember { mutableStateListOf<RecordedTouch>() }
    var canvasCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val colors = MaterialTheme.colorScheme

    Scaffold(containerColor = colors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(AmbaIcons.Back, contentDescription = "Kembali")
                }
                Text(
                    "Playground",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { touches.clear() }) { Text("Bersihkan") }
            }

            Text(
                statusText(playback, connected),
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected) colors.onSurfaceVariant else colors.secondary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                when (playback.status) {
                    Status.IDLE -> {
                        PlaygroundMacros.inApp.forEach { macro ->
                            Button(onClick = { viewModel.play(macro) }, enabled = connected) {
                                Text(macro.name.removePrefix("Uji "))
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.play(PlaygroundMacros.scrollOtherApp, OTHER_APP_DELAY_MS) },
                            enabled = connected,
                        ) { Text("Scroll di app lain") }
                    }
                    Status.PAUSED -> {
                        Button(onClick = viewModel::resume) { Text("Lanjut") }
                        StopButton(viewModel::stop)
                    }
                    Status.PLAYING -> {
                        OutlinedButton(onClick = viewModel::pause) { Text("Jeda") }
                        StopButton(viewModel::stop)
                    }
                    Status.COUNTDOWN -> StopButton(viewModel::stop)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { canvasCoords = it }
                    .pointerInput(targetsOnScreen) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val points = mutableListOf(down.position)
                            var endTime = down.uptimeMillis
                            while (true) {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id } ?: break
                                points += change.position
                                endTime = change.uptimeMillis
                                if (!change.pressed) break
                            }
                            val duration = endTime - down.uptimeMillis
                            val type = when {
                                (points.last() - points.first()).getDistance() > viewConfiguration.touchSlop ->
                                    TouchType.SWIPE
                                duration >= viewConfiguration.longPressTimeoutMillis -> TouchType.LONG_PRESS
                                else -> TouchType.TAP
                            }
                            val screenStart = canvasCoords?.localToScreen(points.first()) ?: return@awaitEachGesture
                            val error = targetsOnScreen.minOfOrNull { (it - screenStart).getDistance() }
                            touches += RecordedTouch(type, points, screenStart, duration, error)
                            if (touches.size > MAX_TOUCHES) touches.removeAt(0)
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val coords = canvasCoords ?: return@Canvas
                    val targetRadius = 18.dp.toPx()
                    targetsOnScreen.forEach { target ->
                        val c = coords.screenToLocal(target)
                        drawCircle(colors.outline, targetRadius, c, style = Stroke(2.dp.toPx()))
                        drawLine(colors.outline, c - Offset(targetRadius, 0f), c + Offset(targetRadius, 0f))
                        drawLine(colors.outline, c - Offset(0f, targetRadius), c + Offset(0f, targetRadius))
                    }
                    touches.forEach { touch ->
                        when (touch.type) {
                            TouchType.SWIPE -> {
                                val path = Path().apply {
                                    moveTo(touch.localPoints.first().x, touch.localPoints.first().y)
                                    touch.localPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(path, colors.primary, style = Stroke(3.dp.toPx()))
                                drawCircle(colors.primary, 5.dp.toPx(), touch.localPoints.first())
                            }
                            TouchType.TAP -> drawCircle(colors.primary, 7.dp.toPx(), touch.localPoints.first())
                            TouchType.LONG_PRESS -> drawCircle(colors.secondary, 9.dp.toPx(), touch.localPoints.first())
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 24.dp),
                ) {
                    touches.takeLast(5).asReversed().forEach { touch ->
                        Text(touch.describe(), style = MonoStyle, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) { Text("Berhenti") }
}

private fun RecordedTouch.describe(): String = String.format(
    Locale.ROOT,
    "%-10s %4.0f, %4.0f  %4d ms  Δ %s",
    type.label,
    screenStart.x,
    screenStart.y,
    durationMs,
    errorPx?.let { String.format(Locale.ROOT, "%.1f px", it) } ?: "-",
)

private fun statusText(state: PlaybackState, connected: Boolean): String = when {
    !connected -> "Layanan aksesibilitas belum aktif. Aktifkan dulu lewat banner di beranda."
    state.status == Status.COUNTDOWN ->
        "Mulai dalam ${(state.countdownMs + 999) / 1000} dtk — pindah ke aplikasi lain sekarang."
    state.status == Status.PLAYING || state.status == Status.PAUSED -> buildString {
        append(if (state.status == Status.PAUSED) "Dijeda" else "Memutar")
        append(" ${state.macroName} · loop ${state.loop}")
        state.totalLoops?.let { append("/$it") }
        append(" · aksi ${state.actionIndex + 1}/${state.actionCount}")
    }
    else -> "Pilih macro contoh. Lingkaran abu-abu = target; titik biru = sentuhan yang diterima. " +
        "Volume turun 2× untuk berhenti darurat."
}
