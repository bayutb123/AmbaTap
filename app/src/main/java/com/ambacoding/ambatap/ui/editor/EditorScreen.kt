package com.ambacoding.ambatap.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.domain.model.totalDurationMs
import com.ambacoding.ambatap.domain.model.withDelay
import com.ambacoding.ambatap.domain.model.withDuration
import com.ambacoding.ambatap.domain.model.withEndpoints
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.components.NumberField
import com.ambacoding.ambatap.ui.components.SegmentedRow
import com.ambacoding.ambatap.ui.components.formatClock
import com.ambacoding.ambatap.ui.theme.MonoStyle
import kotlin.math.abs
import kotlin.math.roundToLong

private val SpeedOptions = listOf(0.5f, 1f, 1.5f, 2f, 4f)

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Minimize -> activity?.moveTaskToBack(true)
                is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }

    BackHandler(enabled = state.dirty) { confirmDiscard = true }
    val back = { if (state.dirty) confirmDiscard = true else onBack() }

    val macro = state.macro
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp),
            ) {
                IconButton(onClick = back) { Icon(AmbaIcons.Back, contentDescription = "Kembali") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        macro?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (macro != null) {
                        Text(
                            "${macro.actions.size} aksi · ${formatClock(macro.actions.totalDurationMs())} · " +
                                "${macro.screen.widthPx}×${macro.screen.heightPx}",
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = viewModel::save, enabled = state.dirty) { Text("Simpan") }
            }
        },
        bottomBar = {
            if (macro != null) {
                BottomBar(onAdd = viewModel::addAction, onPlay = viewModel::play)
            }
        },
    ) { innerPadding ->
        if (macro == null) return@Scaffold
        var expanded by remember { mutableStateOf<Int?>(null) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                OutlinedTextField(
                    value = macro.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nama macro") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            item { PlaybackSection(macro.config, onChange = viewModel::setConfig) }
            item {
                Text(
                    "Aksi  ${macro.actions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            itemsIndexed(macro.actions) { index, action ->
                ActionRow(
                    index = index,
                    action = action,
                    screen = macro.screen,
                    expanded = expanded == index,
                    onToggle = { expanded = if (expanded == index) null else index },
                    onChange = { viewModel.updateAction(index, it) },
                    onMove = { delta ->
                        viewModel.moveAction(index, delta)
                        expanded = (index + delta).coerceIn(0, macro.actions.lastIndex)
                    },
                    onDelete = {
                        viewModel.deleteAction(index)
                        expanded = null
                    },
                    isFirst = index == 0,
                    isLast = index == macro.actions.lastIndex,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan yang belum disimpan akan hilang.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discardChanges()
                    onBack()
                }) { Text("Buang") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Lanjut edit") } },
        )
    }
}

@Composable
private fun PlaybackSection(config: PlaybackConfig, onChange: (PlaybackConfig) -> Unit) {
    val repeat = config.repeat
    val repeatIndex = when {
        repeat is RepeatMode.Count && repeat.times <= 1 -> 0
        repeat is RepeatMode.Count -> 1
        repeat is RepeatMode.Infinite -> 2
        else -> 3
    }
    OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Text("Pemutaran", style = MaterialTheme.typography.titleMedium)

            Label("Ulangi")
            SegmentedRow(
                options = listOf("1×", "N×", "∞", "Durasi"),
                selected = repeatIndex,
                onSelect = { index ->
                    val newRepeat = when (index) {
                        0 -> RepeatMode.Count(1)
                        1 -> RepeatMode.Count((repeat as? RepeatMode.Count)?.times?.takeIf { it > 1 } ?: 5)
                        2 -> RepeatMode.Infinite
                        else -> (repeat as? RepeatMode.Duration) ?: RepeatMode.Duration(60_000)
                    }
                    onChange(config.copy(repeat = newRepeat))
                },
            )
            when (repeat) {
                is RepeatMode.Count -> if (repeat.times > 1) {
                    NumberField(
                        value = repeat.times.toLong(),
                        onValueChange = { onChange(config.copy(repeat = RepeatMode.Count(it.toInt()))) },
                        label = "Jumlah ulang",
                        suffix = "×",
                        range = 2L..100_000L,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is RepeatMode.Duration -> NumberField(
                    value = repeat.millis / 1000,
                    onValueChange = { onChange(config.copy(repeat = RepeatMode.Duration(it * 1000))) },
                    label = "Lama berjalan",
                    suffix = "dtk",
                    range = 1L..86_400L,
                    modifier = Modifier.fillMaxWidth(),
                )
                RepeatMode.Infinite -> Unit
            }

            Label("Kecepatan")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeedOptions.forEach { speed ->
                    FilterChip(
                        selected = abs(config.speed - speed) < 0.01f,
                        onClick = { onChange(config.copy(speed = speed)) },
                        label = { Text("${speed.toString().removeSuffix(".0")}×", style = MonoStyle) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = config.delayBetweenLoopsMs,
                    onValueChange = { onChange(config.copy(delayBetweenLoopsMs = it)) },
                    label = "Jeda loop",
                    suffix = "ms",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = config.randomOffsetPx.toLong(),
                    onValueChange = { onChange(config.copy(randomOffsetPx = it.toInt())) },
                    label = "Acak posisi",
                    suffix = "px",
                    range = 0L..200L,
                    modifier = Modifier.weight(1f),
                )
            }
            NumberField(
                value = config.randomDelayMs,
                onValueChange = { onChange(config.copy(randomDelayMs = it)) },
                label = "Acak jeda (±)",
                suffix = "ms",
                range = 0L..10_000L,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActionRow(
    index: Int,
    action: MacroAction,
    screen: ScreenInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (MacroAction) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        border = if (expanded) BorderStroke(1.5.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("%02d".format(index + 1), style = MonoStyle, color = colors.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(action.title(), style = MaterialTheme.typography.titleMedium)
                    Text(action.detail(screen), style = MonoStyle, color = colors.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceVariant) {
                    Text(
                        "+${action.delayBeforeMs} ms",
                        style = MonoStyle,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                ActionEditor(action, screen, onChange)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMove(-1) }, enabled = !isFirst) { Text("Naik") }
                    TextButton(onClick = { onMove(1) }, enabled = !isLast) { Text("Turun") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDelete) { Text("Hapus", color = colors.error) }
                }
            }
        }
    }
}

@Composable
private fun ActionEditor(action: MacroAction, screen: ScreenInfo, onChange: (MacroAction) -> Unit) {
    val w = screen.widthPx
    val h = screen.heightPx
    fun toPx(n: Float, size: Int) = (n * size).roundToLong()
    fun fromPx(px: Long, size: Int) = (px.toFloat() / size).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (action) {
            is MacroAction.Tap -> {
                PointFields("Posisi", toPx(action.x, w), toPx(action.y, h), w, h) { x, y ->
                    onChange(action.copy(x = fromPx(x, w), y = fromPx(y, h)))
                }
                DurationField(action.durationMs) { onChange(action.copy(durationMs = it)) }
            }
            is MacroAction.LongPress -> {
                PointFields("Posisi", toPx(action.x, w), toPx(action.y, h), w, h) { x, y ->
                    onChange(action.copy(x = fromPx(x, w), y = fromPx(y, h)))
                }
                DurationField(action.durationMs) { onChange(action.copy(durationMs = it)) }
            }
            is MacroAction.Swipe -> if (action.points.isNotEmpty()) {
                val start = action.points.first()
                val end = action.points.last()
                PointFields("Mulai", toPx(start.x, w), toPx(start.y, h), w, h) { x, y ->
                    onChange(action.withEndpoints(fromPx(x, w), fromPx(y, h), end.x, end.y))
                }
                PointFields("Akhir", toPx(end.x, w), toPx(end.y, h), w, h) { x, y ->
                    onChange(action.withEndpoints(start.x, start.y, fromPx(x, w), fromPx(y, h)))
                }
                DurationField(action.durationMs) { onChange(action.withDuration(it)) }
            }
            is MacroAction.Wait -> DurationField(action.durationMs) { onChange(action.copy(durationMs = it)) }
            is MacroAction.GlobalAction -> {
                val types = GlobalType.entries
                SegmentedRow(
                    options = types.map { it.title() },
                    selected = types.indexOf(action.action),
                    onSelect = { onChange(action.copy(action = types[it])) },
                )
            }
            is MacroAction.LaunchApp -> OutlinedTextField(
                value = action.packageName,
                onValueChange = { onChange(action.copy(packageName = it.trim())) },
                label = { Text("Nama paket, mis. com.android.chrome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            is MacroAction.InputText -> OutlinedTextField(
                value = action.text,
                onValueChange = { onChange(action.copy(text = it)) },
                label = { Text("Teks untuk kolom yang sedang fokus") },
                modifier = Modifier.fillMaxWidth(),
            )
            is MacroAction.MultiTouch -> Text(
                "Gesture multi-touch belum bisa diedit; hanya jedanya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NumberField(
            value = action.delayBeforeMs,
            onValueChange = { onChange(action.withDelay(it)) },
            label = "Jeda sebelum",
            suffix = "ms",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PointFields(label: String, x: Long, y: Long, width: Int, height: Int, onChange: (Long, Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            value = x,
            onValueChange = { onChange(it, y) },
            label = "$label X",
            suffix = "px",
            range = 0L until width.toLong(),
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = y,
            onValueChange = { onChange(x, it) },
            label = "$label Y",
            suffix = "px",
            range = 0L until height.toLong(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DurationField(value: Long, onChange: (Long) -> Unit) {
    NumberField(
        value = value,
        onValueChange = onChange,
        label = "Durasi",
        suffix = "ms",
        range = 1L..600_000L,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BottomBar(onAdd: (MacroAction) -> Unit, onPlay: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("+ Tambah aksi") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    NewActions.forEach { (label, create) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menuOpen = false
                            onAdd(create())
                        })
                    }
                }
            }
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(AmbaIcons.Play, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Putar")
            }
        }
    }
}

private val NewActions: List<Pair<String, () -> MacroAction>> = listOf(
    "Tap" to { MacroAction.Tap(0.5f, 0.5f, delayBeforeMs = 300) },
    "Long press" to { MacroAction.LongPress(0.5f, 0.5f, durationMs = 800, delayBeforeMs = 300) },
    "Swipe" to {
        MacroAction.Swipe(
            points = listOf(TimedPoint(0.5f, 0.7f, 0), TimedPoint(0.5f, 0.3f, 300)),
            durationMs = 300,
            delayBeforeMs = 300,
        )
    },
    "Tunggu" to { MacroAction.Wait(durationMs = 1_000) },
    "Kembali" to { MacroAction.GlobalAction(GlobalType.BACK, delayBeforeMs = 300) },
    "Beranda" to { MacroAction.GlobalAction(GlobalType.HOME, delayBeforeMs = 300) },
    "Buka aplikasi" to { MacroAction.LaunchApp("", delayBeforeMs = 300) },
    "Ketik teks" to { MacroAction.InputText("", delayBeforeMs = 300) },
)
