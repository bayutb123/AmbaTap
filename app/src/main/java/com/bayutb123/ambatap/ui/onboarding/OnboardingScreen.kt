package com.bayutb123.ambatap.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val disclosurePoints = listOf(
    "Memutar ulang tap, swipe, dan long press yang Anda rekam",
    "Menampilkan panel kontrol di atas aplikasi lain",
    "Membaca tombol volume untuk berhenti darurat",
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Izinkan AmbaTap mengontrol layar",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "AmbaTap memakai Layanan Aksesibilitas Android untuk menjalankan macro. " +
                    "Semua macro tersimpan di perangkat dan tidak dikirim ke mana pun.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            disclosurePoints.forEach { point ->
                Text("•  $point", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Layanan aksesibilitas",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(connected)
                }
            }
            if (!connected) {
                Text(
                    "Di pengaturan, buka Aplikasi terinstal › AmbaTap lalu aktifkan. " +
                        "Bila tombolnya tidak bisa ditekan, buka Info aplikasi AmbaTap › ⋮ › " +
                        "Izinkan setelan terbatas, lalu coba lagi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (connected) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Selesai") }
            } else {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Buka pengaturan aksesibilitas") }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Nanti saja")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(connected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (connected) colors.primaryContainer else colors.secondaryContainer,
        contentColor = if (connected) colors.onPrimaryContainer else colors.onSecondaryContainer,
    ) {
        Text(
            if (connected) "Aktif" else "Belum aktif",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
