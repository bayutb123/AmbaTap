package com.bayutb123.ambatap.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bayutb123.ambatap.ui.components.AmbaIcons

// TODO(Fase 5): timeline aksi, edit nilai, dan pengaturan pemutaran.
@Composable
fun EditorScreen(macroId: Long, onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(AmbaIcons.Back, contentDescription = "Kembali")
                }
                Text("Macro #$macroId", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
