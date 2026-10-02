package com.ambacoding.ambatap.service.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.withDelay
import com.ambacoding.ambatap.domain.model.withDuration
import com.ambacoding.ambatap.domain.model.withEndpoints
import com.ambacoding.ambatap.engine.player.ScreenSize
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.components.SegmentedRow
import com.ambacoding.ambatap.ui.components.label
import com.ambacoding.ambatap.ui.theme.Ink
import com.ambacoding.ambatap.ui.theme.MonoStyle
import com.ambacoding.ambatap.ui.theme.MutedDark
import com.ambacoding.ambatap.ui.theme.PlayBlue
import com.ambacoding.ambatap.ui.theme.RecordOrange
import kotlin.math.roundToInt

class PickerActions(
    val onSelect: (Int?) -> Unit,
    val onChange: (Int, MacroAction) -> Unit,
    val onDelete: (Int) -> Unit,
    val onAddTap: () -> Unit,
    val onAddSwipe: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onTestPlay: () -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * Overlay layar penuh untuk menaruh dan menggeser titik tap, long press, dan swipe.
 * Aksi lain (tunggu, aksi global, dll.) tetap di urutannya tetapi tidak digambar.
 */
@Composable
fun PointPicker(
    name: String,
    actions: List<MacroAction>,
    repeat: RepeatMode,
    selected: Int?,
    screen: ScreenSize,
    callbacks: PickerActions,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val positional = actions.count { it.isPositional }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.12f))
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) { detectTapGestures { callbacks.onSelect(null) } },
    ) {
        val layout = coords
        if (layout != null) {
            fun local(nx: Float, ny: Float) = layout.screenToLocal(Offset(nx * screen.widthPx, ny * screen.heightPx))
            fun dxN(px: Float) = px / screen.widthPx
            fun dyN(px: Float) = px / screen.heightPx

            Canvas(Modifier.fillMaxSize()) {
                actions.filterIsInstance<MacroAction.Swipe>().forEach { swipe ->
                    if (swipe.points.size < 2) return@forEach
                    val path = Path().apply {
                        val p0 = local(swipe.points.first().x, swipe.points.first().y)
                        moveTo(p0.x, p0.y)
                        swipe.points.drop(1).forEach { p -> local(p.x, p.y).let { lineTo(it.x, it.y) } }
                    }
                    drawPath(path, PlayBlue, style = Stroke(4.dp.toPx()))
                }
            }

            actions.forEachIndexed { index, action ->
                val number = "${index + 1}"
                when (action) {
                    is MacroAction.Tap -> Marker(
                        center = local(action.x, action.y),
                        label = number,
                        color = PlayBlue,
                        selected = selected == index,
                        onSelect = { callbacks.onSelect(index) },
                        onDrag = { d ->
                            callbacks.onChange(index, action.copy(x = (action.x + dxN(d.x)).clamp(), y = (action.y + dyN(d.y)).clamp()))
                        },
                    )
                    is MacroAction.LongPress -> Marker(
                        center = local(action.x, action.y),
                        label = number,
                        color = RecordOrange,
                        selected = selected == index,
                        onSelect = { callbacks.onSelect(index) },
                        onDrag = { d ->
                            callbacks.onChange(index, action.copy(x = (action.x + dxN(d.x)).clamp(), y = (action.y + dyN(d.y)).clamp()))
                        },
                    )
                    is MacroAction.Swipe -> if (action.points.isNotEmpty()) {
                        val start = action.points.first()
                        val end = action.points.last()
                        Marker(
                            center = local(start.x, start.y),
                            label = number,
                            color = PlayBlue,
                            selected = selected == index,
                            onSelect = { callbacks.onSelect(index) },
                            onDrag = { d ->
                                callbacks.onChange(
                                    index,
                                    action.withEndpoints((start.x + dxN(d.x)).clamp(), (start.y + dyN(d.y)).clamp(), end.x, end.y),
                                )
                            },
                        )
                        Marker(
                            center = local(end.x, end.y),
                            label = "›",
                            color = PlayBlue,
                            selected = selected == index,
                            small = true,
                            onSelect = { callbacks.onSelect(index) },
                            onDrag = { d ->
                                callbacks.onChange(
                                    index,
                                    action.withEndpoints(start.x, start.y, (end.x + dxN(d.x)).clamp(), (end.y + dyN(d.y)).clamp()),
                                )
                            },
                        )
                    }
                    else -> Unit
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Ink, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Text("$positional titik · ulang ${repeat.label()}", style = MonoStyle, color = MutedDark)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Ink)
                    .clickable(role = Role.Button, onClick = callbacks.onClose)
                    .semantics { contentDescription = "Tutup tanpa menyimpan" },
            ) {
                Icon(AmbaIcons.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            val index = selected
            if (index != null && index in actions.indices) {
                SelectedPanel(index, actions[index], screen, callbacks)
            }
            Toolbar(repeat = repeat, canRun = actions.isNotEmpty(), callbacks = callbacks)
        }
    }
}

private val MacroAction.isPositional: Boolean
    get() = this is MacroAction.Tap || this is MacroAction.LongPress || this is MacroAction.Swipe

private fun Float.clamp() = coerceIn(0f, 1f)

@Composable
private fun Marker(
    center: Offset,
    label: String,
    color: Color,
    selected: Boolean,
    onSelect: () -> Unit,
    onDrag: (Offset) -> Unit,
    small: Boolean = false,
) {
    val size = if (small) 36.dp else 48.dp
    val half = with(LocalDensity.current) { size.toPx() / 2 }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .then(if (selected) Modifier.border(4.dp, Color.White, CircleShape) else Modifier)
            .background(color.copy(alpha = if (selected) 0.35f else 0.18f), CircleShape)
            .border(3.dp, color, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { currentOnSelect() }) { change, drag ->
                    change.consume()
                    currentOnDrag(drag)
                }
            }
            .pointerInput(Unit) { detectTapGestures { currentOnSelect() } }
            .semantics { contentDescription = "Titik $label" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .background(color, CircleShape),
        ) {
            Text(label, color = Color.White, style = MonoStyle, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SelectedPanel(index: Int, action: MacroAction, screen: ScreenSize, callbacks: PickerActions) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (action is MacroAction.Swipe) "Swipe ${index + 1}" else "Titik ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val (x, y) = when (action) {
                is MacroAction.Tap -> action.x to action.y
                is MacroAction.LongPress -> action.x to action.y
                is MacroAction.Swipe -> action.points.first().x to action.points.first().y
                else -> 0f to 0f
            }
            Text(
                "${(x * screen.widthPx).roundToInt()}, ${(y * screen.heightPx).roundToInt()}",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (action) {
            is MacroAction.Tap, is MacroAction.LongPress -> {
                val isLong = action is MacroAction.LongPress
                SegmentedRow(
                    options = listOf("Tap", "Tahan"),
                    selected = if (isLong) 1 else 0,
                    onSelect = { choice ->
                        val converted = when {
                            choice == 1 && action is MacroAction.Tap ->
                                MacroAction.LongPress(action.x, action.y, durationMs = 800, delayBeforeMs = action.delayBeforeMs)
                            choice == 0 && action is MacroAction.LongPress ->
                                MacroAction.Tap(action.x, action.y, delayBeforeMs = action.delayBeforeMs)
                            else -> action
                        }
                        callbacks.onChange(index, converted)
                    },
                )
                if (action is MacroAction.LongPress) {
                    Stepper("Lama tahan", action.durationMs, step = 100, min = 300) {
                        callbacks.onChange(index, action.copy(durationMs = it))
                    }
                }
            }
            is MacroAction.Swipe -> Stepper("Durasi swipe", action.durationMs, step = 50, min = 50) {
                callbacks.onChange(index, action.withDuration(it))
            }
            else -> Unit
        }
        Stepper("Jeda sebelum", action.delayBeforeMs, step = 50, min = 0) {
            callbacks.onChange(index, action.withDelay(it))
        }
        TextButton(onClick = { callbacks.onDelete(index) }) {
            Text("Hapus titik", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Stepper(label: String, value: Long, step: Long, min: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−", fontSize = 18.sp) }
        Text("$value ms", style = MonoStyle, modifier = Modifier.width(76.dp), maxLines = 1)
        TextButton(onClick = { onChange(value + step) }) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun Toolbar(repeat: RepeatMode, canRun: Boolean, callbacks: PickerActions) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink, RoundedCornerShape(20.dp))
            .padding(8.dp),
    ) {
        ToolButton(AmbaIcons.Plus, "Titik", onClick = callbacks.onAddTap, modifier = Modifier.weight(1f))
        ToolButton(AmbaIcons.Swipe, "Swipe", onClick = callbacks.onAddSwipe, modifier = Modifier.weight(1f))
        ToolButton(AmbaIcons.Repeat, "Ulang ${repeat.label()}", onClick = callbacks.onCycleRepeat, modifier = Modifier.weight(1f))
        ToolButton(
            AmbaIcons.Play,
            "Uji",
            onClick = callbacks.onTestPlay,
            enabled = canRun,
            container = PlayBlue,
            modifier = Modifier.weight(1f),
        )
        ToolButton(AmbaIcons.Check, "Simpan", onClick = callbacks.onSave, enabled = canRun, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Color.Transparent,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        val tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}
