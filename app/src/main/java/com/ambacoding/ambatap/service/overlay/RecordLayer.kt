package com.ambacoding.ambatap.service.overlay

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.engine.recorder.RecordingState
import com.ambacoding.ambatap.ui.components.formatClock
import com.ambacoding.ambatap.ui.theme.Ink
import com.ambacoding.ambatap.ui.theme.MonoStyle
import com.ambacoding.ambatap.ui.theme.MutedDark
import com.ambacoding.ambatap.ui.theme.RecordOrange
import com.ambacoding.ambatap.ui.theme.RecordOrangeDark
import kotlinx.coroutines.delay

private const val VISIBLE_MARKERS = 3
private const val TOAST_MS = 2_500L

/**
 * Tampilan lapisan perekaman layar penuh: bingkai, indikator REC, jejak sentuhan
 * yang sedang berlangsung, penanda beberapa aksi terakhir, dan toast aksi terakhir.
 * Hanya menggambar; sentuhan ditangkap oleh [RecordingController].
 */
@Composable
fun RecordLayer(state: RecordingState, liveTrail: List<Offset>) {
    val paused = state.status == RecordingState.Status.PAUSED
    val accent = if (paused) MutedDark else RecordOrange

    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.uptimeMillis()
            delay(500)
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(3.dp, accent)
            .background(accent.copy(alpha = 0.04f)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val firstNumber = state.actions.size - VISIBLE_MARKERS + 1
            state.actions.takeLast(VISIBLE_MARKERS).forEachIndexed { i, action ->
                drawMarker(action, number = maxOf(firstNumber, 1) + i, textMeasurer)
            }
            if (liveTrail.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(liveTrail.first().x, liveTrail.first().y)
                    liveTrail.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, RecordOrange, style = Stroke(4.dp.toPx()))
                drawCircle(RecordOrange, 22.dp.toPx(), liveTrail.last(), alpha = 0.25f)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(Ink, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (paused) MutedDark else RecordOrangeDark, CircleShape),
            )
            Text(if (paused) "JEDA" else "REC", style = MonoStyle, color = Color.White, fontWeight = FontWeight.Medium)
            Text(formatClock(state.elapsedMs(now)), style = MonoStyle, color = Color.White)
            Text("· ${state.actions.size} aksi", style = MonoStyle, color = MutedDark)
        }

        LastActionToast(
            actions = state.actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun LastActionToast(actions: List<MacroAction>, modifier: Modifier) {
    var visibleFor by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(actions.size) {
        if (actions.isEmpty()) return@LaunchedEffect
        visibleFor = actions.size
        delay(TOAST_MS)
        visibleFor = null
    }
    val action = actions.lastOrNull()
    if (visibleFor == null || action == null) return

    // Pil kecil satu baris agar tidak menutupi bagian bawah aplikasi.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(Ink.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(action.label(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(action.detail(), style = MonoStyle, color = MutedDark)
    }
}

private fun DrawScope.drawMarker(action: MacroAction, number: Int, textMeasurer: TextMeasurer) {
    val color = RecordOrange
    fun px(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val center = when (action) {
        is MacroAction.Tap -> px(action.x, action.y)
        is MacroAction.LongPress -> px(action.x, action.y)
        is MacroAction.Swipe -> {
            val points = action.points.map { px(it.x, it.y) }
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path,
                color,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 14f))),
            )
            points.last()
        }
        else -> return
    }
    drawCircle(Color.White.copy(alpha = 0.75f), 18.dp.toPx(), center)
    drawCircle(color, 18.dp.toPx(), center, style = Stroke(2.dp.toPx()))
    val label = textMeasurer.measure(
        number.toString(),
        style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
    drawText(
        label,
        topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f),
    )
}

private fun MacroAction.label(): String = when (this) {
    is MacroAction.Tap -> "Tap"
    is MacroAction.LongPress -> "Long press"
    is MacroAction.Swipe -> "Swipe"
    else -> "Aksi"
}

private fun MacroAction.detail(): String = when (this) {
    is MacroAction.Tap -> "$durationMs ms"
    is MacroAction.LongPress -> "$durationMs ms"
    is MacroAction.Swipe -> "${points.size} titik · $durationMs ms"
    else -> ""
}
