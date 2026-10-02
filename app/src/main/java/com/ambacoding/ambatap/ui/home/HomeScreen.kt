package com.ambacoding.ambatap.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.engine.player.PlaybackState
import com.ambacoding.ambatap.service.realScreenSize
import com.ambacoding.ambatap.service.screenRotation
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.components.formatClock
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import com.ambacoding.ambatap.ui.theme.MonoStyle
import kotlinx.coroutines.launch

/** Aksi yang tersedia di beranda; dikelompokkan agar tanda tangan composable tetap ringkas. */
class HomeActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenOnboarding: () -> Unit = {},
    val onOpenMacro: (Long) -> Unit = {},
    val onOpenPlayground: () -> Unit = {},
    val onTogglePanel: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onAutoClicker: () -> Unit = {},
    val onPlay: (Long) -> Unit = {},
    val onStop: () -> Unit = {},
    val onRename: (Long, String) -> Unit = { _, _ -> },
    val onDuplicate: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
)

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenMacro: (Long) -> Unit,
    onOpenPlayground: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.Minimize -> activity?.moveTaskToBack(true)
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is HomeEvent.Deleted -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "\"${event.macro.name}\" dihapus",
                        actionLabel = "Urungkan",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.macro)
                }
            }
        }
    }

    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = HomeActions(
            onOpenSettings = onOpenSettings,
            onOpenOnboarding = onOpenOnboarding,
            onOpenMacro = onOpenMacro,
            onOpenPlayground = onOpenPlayground,
            onTogglePanel = viewModel::togglePanel,
            onRecord = {
                viewModel.prepareRecording()
                activity?.moveTaskToBack(true)
            },
            onAutoClicker = {
                val size = context.realScreenSize()
                viewModel.startAutoClicker(ScreenInfo(size.widthPx, size.heightPx, context.screenRotation()))
            },
            onPlay = viewModel::play,
            onStop = viewModel::stop,
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
        ),
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
) {
    var renaming by remember { mutableStateOf<MacroItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(onOpenSettings = actions.onOpenSettings) }
            if (!uiState.isLoading) {
                item {
                    if (uiState.serviceConnected) {
                        ServiceActiveCard(panelShown = uiState.panelRequested, onTogglePanel = actions.onTogglePanel)
                    } else {
                        ServiceBanner(onActivate = actions.onOpenOnboarding)
                    }
                }
            }
            if (uiState.serviceConnected) {
                item { ActionTiles(onRecord = actions.onRecord, onAutoClicker = actions.onAutoClicker) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Macro tersimpan", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "  ${uiState.macros.size}",
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!uiState.isLoading && uiState.macros.isEmpty()) {
                item {
                    Text(
                        "Belum ada macro. Tekan Rekam macro untuk membuat yang pertama.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.macros, key = { it.id }) { macro ->
                val running = uiState.playback.isActive && uiState.playback.macroId == macro.id
                MacroCard(
                    macro = macro,
                    playback = if (running) uiState.playback else null,
                    canPlay = uiState.serviceConnected,
                    actions = actions,
                    onRename = { renaming = macro },
                )
            }
            item {
                TextButton(onClick = actions.onOpenPlayground, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text("Playground: uji akurasi tap & swipe")
                }
            }
        }
    }

    renaming?.let { macro ->
        RenameDialog(
            initial = macro.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                actions.onRename(macro.id, name)
                renaming = null
            },
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = buildAnnotatedString {
                append("Amba")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append("Tap") }
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(AmbaIcons.Settings, contentDescription = "Pengaturan")
        }
    }
}

@Composable
private fun ServiceBanner(onActivate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Layanan belum aktif", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aktifkan agar AmbaTap bisa merekam & memutar macro",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onActivate) { Text("Aktifkan") }
        }
    }
}

@Composable
private fun ServiceActiveCard(panelShown: Boolean, onTogglePanel: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Layanan aktif", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Siap merekam & memutar macro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onTogglePanel) {
                Text(if (panelShown) "Sembunyikan panel" else "Tampilkan panel")
            }
        }
    }
}

@Composable
private fun ActionTiles(onRecord: () -> Unit, onAutoClicker: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            title = "Rekam macro",
            subtitle = "Tap, swipe, long press",
            icon = AmbaIcons.Record,
            container = MaterialTheme.colorScheme.secondary,
            content = MaterialTheme.colorScheme.onSecondary,
            onClick = onRecord,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            title = "Auto clicker",
            subtitle = "Atur titik secara manual",
            icon = AmbaIcons.Target,
            container = MaterialTheme.colorScheme.inverseSurface,
            content = MaterialTheme.colorScheme.inverseOnSurface,
            onClick = onAutoClicker,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = modifier.height(120.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MacroCard(
    macro: MacroItem,
    playback: PlaybackState?,
    canPlay: Boolean,
    actions: HomeActions,
    onRename: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val running = playback != null
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedCard(
        onClick = { actions.onOpenMacro(macro.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (running) colors.primaryContainer.copy(alpha = 0.4f) else colors.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (running) colors.primaryContainer else colors.surfaceVariant,
                contentColor = if (running) colors.primary else colors.onSurface,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AmbaIcons.Record, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (running) runningLabel(playback) else
                        "${macro.actionCount} aksi · ${formatClock(macro.durationMs)} · ${macro.repeatLabel}",
                    style = MonoStyle,
                    color = if (running) colors.primary else colors.onSurfaceVariant,
                )
            }
            if (running) {
                FilledIconButton(
                    onClick = actions.onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.secondary,
                        contentColor = colors.onSecondary,
                    ),
                ) { Icon(AmbaIcons.Stop, contentDescription = "Hentikan ${macro.name}") }
            } else {
                FilledIconButton(onClick = { actions.onPlay(macro.id) }, enabled = canPlay) {
                    Icon(AmbaIcons.Play, contentDescription = "Putar ${macro.name}")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AmbaIcons.More, contentDescription = "Opsi lainnya", tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                        menuOpen = false
                        actions.onOpenMacro(macro.id)
                    })
                    DropdownMenuItem(text = { Text("Ganti nama") }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text("Duplikat") }, onClick = {
                        menuOpen = false
                        actions.onDuplicate(macro.id)
                    })
                    DropdownMenuItem(text = { Text("Hapus", color = colors.error) }, onClick = {
                        menuOpen = false
                        actions.onDelete(macro.id)
                    })
                }
            }
        }
    }
}

private fun runningLabel(state: PlaybackState): String = when (state.status) {
    PlaybackState.Status.COUNTDOWN -> "Mulai dalam ${(state.countdownMs + 999) / 1000} dtk"
    PlaybackState.Status.PAUSED -> "Dijeda · loop ${state.loop}/${state.totalLoops ?: "∞"}"
    else -> "Berjalan · loop ${state.loop}/${state.totalLoops ?: "∞"}"
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti nama") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nama macro") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    AmbaTapTheme {
        HomeContent(
            uiState = HomeUiState(
                macros = listOf(
                    MacroItem(1, "Login harian game", 8, 12_000, "1×"),
                    MacroItem(2, "Scroll feed otomatis", 3, 6_000, "50×"),
                ),
                isLoading = false,
                serviceConnected = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            actions = HomeActions(),
        )
    }
}
