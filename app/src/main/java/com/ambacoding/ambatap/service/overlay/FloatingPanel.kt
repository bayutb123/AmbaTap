package com.ambacoding.ambatap.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambacoding.ambatap.engine.player.PlaybackState
import com.ambacoding.ambatap.engine.player.PlaybackState.Status
import com.ambacoding.ambatap.engine.recorder.RecordingState
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.theme.Ink
import com.ambacoding.ambatap.ui.theme.MonoStyle
import com.ambacoding.ambatap.ui.theme.MutedDark
import com.ambacoding.ambatap.ui.theme.PlayBlue
import com.ambacoding.ambatap.ui.theme.PlayBlueDark
import com.ambacoding.ambatap.ui.theme.RecordOrange
import com.ambacoding.ambatap.ui.theme.RecordOrangeDark
import com.ambacoding.ambatap.ui.theme.SurfaceVariantDark

/** Aksi yang bisa dipicu dari panel melayang. */
class PanelActions(
    val onDrag: (Offset) -> Unit,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onStop: () -> Unit,
    val onOpenApp: () -> Unit,
    val onClose: () -> Unit,
    val onRecord: () -> Unit,
    val onFinishRecording: () -> Unit,
    val onPauseRecording: () -> Unit,
    val onResumeRecording: () -> Unit,
    val onCancelRecording: () -> Unit,
)

/** Panel gelap yang bisa digeser; bentuknya mengikuti status perekaman dan pemutaran. */
@Composable
fun FloatingPanel(
    state: PlaybackState,
    recording: RecordingState.Status,
    canPlay: Boolean,
    idleOpacity: Float,
    actions: PanelActions,
) {
    val currentOnDrag by rememberUpdatedState(actions.onDrag)
    val capturing = recording == RecordingState.Status.RECORDING || recording == RecordingState.Status.PAUSED
    // Saat merekam panel dibuat ringkas: margin & padding lebih kecil agar tidak menutupi aplikasi.
    val shape = RoundedCornerShape(if (capturing) 18.dp else 20.dp)
    val gap = if (capturing) 4.dp else 8.dp
    Box(
        modifier = Modifier
            .padding(gap)
            .alpha(if (state.isActive || capturing) 1f else idleOpacity)
            .shadow(if (capturing) 4.dp else 8.dp, shape)
            .background(Ink, shape)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentOnDrag(drag)
                }
            }
            .padding(gap),
    ) {
        when {
            capturing -> RecordingContent(paused = recording == RecordingState.Status.PAUSED, actions)
            state.status == Status.IDLE ->
                IdleContent(canPlay = canPlay, canRecord = recording == RecordingState.Status.IDLE, actions)
            state.status == Status.COUNTDOWN -> CountdownContent(state, actions.onStop)
            else -> PlayingContent(state, actions.onPause, actions.onResume, actions.onStop)
        }
    }
}

@Composable
private fun IdleContent(canPlay: Boolean, canRecord: Boolean, actions: PanelActions) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DragHandle()
        PanelIconButton(
            icon = AmbaIcons.Record,
            label = "Mulai merekam",
            onClick = actions.onRecord,
            container = RecordOrange,
            enabled = canRecord,
        )
        PanelIconButton(
            icon = AmbaIcons.Play,
            label = "Putar macro terakhir",
            onClick = actions.onPlay,
            container = PlayBlue,
            enabled = canPlay,
        )
        PanelIconButton(icon = AmbaIcons.OpenApp, label = "Buka AmbaTap", onClick = actions.onOpenApp)
        PanelIconButton(icon = AmbaIcons.Close, label = "Tutup panel", onClick = actions.onClose)
    }
}

/**
 * Kontrol rekaman yang ringkas (tombol 36dp, tanpa gagang; seluruh panel tetap bisa digeser).
 * Bisa diperkecil menjadi satu titik REC agar hampir tidak menutupi aplikasi.
 */
@Composable
private fun RecordingContent(paused: Boolean, actions: PanelActions) {
    var collapsed by remember { mutableStateOf(false) }
    if (collapsed) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CollapsedSize)
                .clip(CircleShape)
                .clickable(role = Role.Button) { collapsed = false }
                .semantics { contentDescription = "Tampilkan kontrol rekaman" },
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (paused) MutedDark else RecordOrangeDark, CircleShape),
            )
        }
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PanelIconButton(
            icon = AmbaIcons.Stop,
            label = "Selesai merekam",
            onClick = actions.onFinishRecording,
            container = RecordOrange,
            size = CompactButtonSize,
        )
        PanelIconButton(
            icon = if (paused) AmbaIcons.Record else AmbaIcons.Pause,
            label = if (paused) "Lanjut merekam" else "Jeda rekaman",
            onClick = if (paused) actions.onResumeRecording else actions.onPauseRecording,
            size = CompactButtonSize,
        )
        PanelIconButton(
            icon = AmbaIcons.Close,
            label = "Batalkan rekaman",
            onClick = actions.onCancelRecording,
            size = CompactButtonSize,
        )
        PanelIconButton(
            icon = AmbaIcons.Minimize,
            label = "Perkecil panel",
            onClick = { collapsed = true },
            container = Color.Transparent,
            size = CompactButtonSize,
        )
    }
}

private val CompactButtonSize = 36.dp
private val CollapsedSize = 32.dp

@Composable
private fun CountdownContent(state: PlaybackState, onStop: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DragHandle()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Mulai dalam ${state.countdownSeconds} detik" },
        ) {
            Text(
                state.countdownSeconds.toString(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        PanelIconButton(icon = AmbaIcons.Stop, label = "Batalkan", onClick = onStop, container = RecordOrange)
    }
}

@Composable
private fun PlayingContent(
    state: PlaybackState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val paused = state.status == Status.PAUSED
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .width(240.dp)
            .padding(start = 4.dp, top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (paused) MutedDark else PlayBlueDark, CircleShape),
            )
            Text(
                state.macroName.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(state.progressLabel(), style = MonoStyle, color = MutedDark)
        if (state.orientationMismatch) {
            Text("Orientasi layar berbeda dari saat direkam", color = RecordOrangeDark, fontSize = 12.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SurfaceVariantDark, RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.progressFraction)
                    .height(4.dp)
                    .background(PlayBlueDark, RoundedCornerShape(2.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelTextButton(
                icon = if (paused) AmbaIcons.Play else AmbaIcons.Pause,
                text = if (paused) "Lanjut" else "Jeda",
                onClick = if (paused) onResume else onPause,
                modifier = Modifier.weight(1f),
            )
            PanelTextButton(
                icon = AmbaIcons.Stop,
                text = "Berhenti",
                onClick = onStop,
                container = RecordOrange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DragHandle() {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(Color(0xFF6B6E76), RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun PanelIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color = SurfaceVariantDark,
    enabled: Boolean = true,
    size: Dp = 44.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

@Composable
private fun PanelTextButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = SurfaceVariantDark,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

private val PlaybackState.countdownSeconds: Long get() = (countdownMs + 999) / 1000

private val PlaybackState.progressFraction: Float
    get() = if (actionCount == 0) 0f else (actionIndex + 1f) / actionCount

private fun PlaybackState.progressLabel(): String = buildString {
    append("Loop $loop / ${totalLoops ?: "∞"}")
    append("   Aksi ${actionIndex + 1} / $actionCount")
}
