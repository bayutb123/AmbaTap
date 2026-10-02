package com.ambacoding.ambatap.ui.settings

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.BuildConfig
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.components.startActivitySafely
import com.ambacoding.ambatap.ui.theme.MonoStyle
import kotlin.math.roundToInt

private const val PRIVACY_POLICY_URL = "https://github.com/bayutb123/AmbaTap/blob/main/docs/PRIVACY.md"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var batteryUnrestricted by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportAll) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 4.dp, top = 8.dp),
            ) {
                IconButton(onClick = onBack) { Icon(AmbaIcons.Back, contentDescription = "Kembali") }
                Text("Pengaturan", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Section("Kontrol") {
                    SettingRow(title = "Berhenti darurat") {
                        Text("Volume turun 2×", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    SettingRow(title = "Hitung mundur sebelum mulai") {
                        TextButton(onClick = { viewModel.setCountdownSeconds(settings.countdownSeconds - 1) }) {
                            Text("−")
                        }
                        Text("${settings.countdownSeconds} dtk", style = MonoStyle)
                        TextButton(onClick = { viewModel.setCountdownSeconds(settings.countdownSeconds + 1) }) {
                            Text("+")
                        }
                    }
                    HorizontalDivider()
                    SettingRow(
                        title = "Tampilkan indikator sentuhan",
                        subtitle = "Lingkaran di posisi tap saat memutar",
                    ) {
                        Switch(checked = settings.showTouchIndicator, onCheckedChange = viewModel::setShowTouchIndicator)
                    }
                }
            }
            item {
                Section("Panel melayang") {
                    // Simpan ke DataStore hanya saat slider dilepas, bukan tiap frame geser.
                    var opacity by remember(settings.panelIdleOpacity) {
                        mutableFloatStateOf(settings.panelIdleOpacity)
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Transparansi saat diam",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(opacity * 100).roundToInt()}%", style = MonoStyle)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            onValueChangeFinished = { viewModel.setPanelIdleOpacity(opacity) },
                            valueRange = 0.2f..1f,
                        )
                    }
                }
            }
            item {
                Section("Data") {
                    ClickableRow(
                        title = "Ekspor semua macro",
                        trailing = ".json",
                        onClick = { exportLauncher.launch("ambatap-macros.json") },
                    )
                    HorizontalDivider()
                    ClickableRow(
                        title = "Impor macro",
                        subtitle = "Macro dari file ditambahkan ke daftar",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                }
            }
            item {
                Section("Perangkat") {
                    ClickableRow(
                        title = "Optimasi baterai",
                        subtitle = if (batteryUnrestricted) {
                            "Tidak dibatasi"
                        } else {
                            "Dibatasi: matikan agar macro tidak dihentikan sistem"
                        },
                        badge = if (batteryUnrestricted) null else "Dibatasi",
                        onClick = {
                            context.startActivitySafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                    HorizontalDivider()
                    ClickableRow(
                        title = "Layanan aksesibilitas",
                        subtitle = "Buka pengaturan aksesibilitas Android",
                        onClick = { context.startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    )
                }
            }
            item {
                Section("Tentang") {
                    SettingRow(title = "Versi") {
                        Text(BuildConfig.VERSION_NAME, style = MonoStyle)
                    }
                    HorizontalDivider()
                    ClickableRow(
                        title = "Kebijakan privasi",
                        onClick = {
                            context.startActivitySafely(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                        },
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Font",
                        subtitle = "Space Grotesk, IBM Plex Sans, JetBrains Mono (SIL Open Font License 1.1)",
                    ) {}
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun ClickableRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: String? = null,
    badge: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.let { Text(it, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        badge?.let {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}
