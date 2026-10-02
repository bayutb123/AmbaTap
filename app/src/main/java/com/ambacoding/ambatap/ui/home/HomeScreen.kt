package com.ambacoding.ambatap.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.ui.components.AmbaIcons
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import com.ambacoding.ambatap.ui.theme.MonoStyle

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
    HomeContent(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onOpenOnboarding = onOpenOnboarding,
        onOpenMacro = onOpenMacro,
        onOpenPlayground = onOpenPlayground,
        onTogglePanel = viewModel::togglePanel,
        onRecord = {
            viewModel.prepareRecording()
            activity?.moveTaskToBack(true)
        },
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenMacro: (Long) -> Unit,
    onOpenPlayground: () -> Unit,
    onTogglePanel: () -> Unit,
    onRecord: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
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
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                                append("Tap")
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(AmbaIcons.Settings, contentDescription = "Pengaturan")
                    }
                }
            }
            if (!uiState.isLoading) {
                item {
                    if (uiState.serviceConnected) {
                        ServiceActiveCard(panelShown = uiState.panelRequested, onTogglePanel = onTogglePanel)
                    } else {
                        ServiceBanner(onActivate = onOpenOnboarding)
                    }
                }
            }
            if (uiState.serviceConnected) {
                item {
                    RecordCard(onRecord = onRecord)
                }
            }
            item {
                PlaygroundCard(onClick = onOpenPlayground)
            }
            item {
                Text(
                    text = "Macro tersimpan",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!uiState.isLoading && uiState.macros.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada macro. Rekaman pertama Anda akan muncul di sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.macros, key = { it.id }) { macro ->
                MacroRow(macro = macro, onClick = { onOpenMacro(macro.id) })
            }
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Layanan aktif", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Siap memutar macro",
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
private fun RecordCard(onRecord: () -> Unit) {
    Card(
        onClick = onRecord,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Rekam macro", style = MaterialTheme.typography.titleMedium)
            Text(
                "Buka aplikasi tujuan, lalu tekan tombol merah di panel",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PlaygroundCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Playground", style = MaterialTheme.typography.titleMedium)
            Text(
                "Uji akurasi tap & swipe dengan macro contoh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroRow(macro: MacroItem, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(macro.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${macro.actionCount} aksi",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    AmbaTapTheme {
        HomeContent(
            uiState = HomeUiState(
                macros = listOf(
                    MacroItem(1, "Login harian game", 8),
                    MacroItem(2, "Scroll feed otomatis", 3),
                ),
                isLoading = false,
            ),
            onOpenSettings = {},
            onOpenOnboarding = {},
            onOpenMacro = {},
            onOpenPlayground = {},
            onTogglePanel = {},
            onRecord = {},
        )
    }
}
