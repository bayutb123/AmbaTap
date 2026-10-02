package com.ambacoding.ambatap.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.totalDurationMs
import com.ambacoding.ambatap.engine.recorder.RecordingState
import com.ambacoding.ambatap.ui.theme.MonoStyle

/** Pilihan jumlah ulang yang ditawarkan saat menyimpan; bisa diubah lagi di editor. */
val SaveRepeatOptions: List<Pair<String, RepeatMode>> = listOf(
    "1×" to RepeatMode.Count(1),
    "5×" to RepeatMode.Count(5),
    "10×" to RepeatMode.Count(10),
    "∞" to RepeatMode.Infinite,
)

@Composable
fun SaveSheet(
    state: RecordingState,
    name: String,
    onNameChange: (String) -> Unit,
    repeat: RepeatMode,
    onRepeatChange: (RepeatMode) -> Unit,
    onSave: () -> Unit,
    onTestPlay: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .background(colors.outlineVariant, RoundedCornerShape(2.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rekaman selesai", style = MaterialTheme.typography.titleLarge)
            Text(state.actions.breakdown(), style = MonoStyle, color = colors.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${state.actions.size}", "aksi", Modifier.weight(1f))
            StatTile(formatElapsed(state.actions.totalDurationMs()), "durasi", Modifier.weight(1f))
            val screen = state.screen
            StatTile(
                if (screen != null) "${screen.widthPx}×${screen.heightPx}" else "-",
                if (screen != null && screen.heightPx >= screen.widthPx) "potret" else "lanskap",
                Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nama macro") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ulangi", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                SaveRepeatOptions.forEach { (label, mode) ->
                    val selected = mode == repeat
                    TextButton(
                        onClick = { onRepeatChange(mode) },
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) colors.inverseSurface else colors.surfaceVariant,
                            contentColor = if (selected) colors.inverseOnSurface else colors.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Simpan macro") }
            Row {
                TextButton(onClick = onTestPlay, modifier = Modifier.weight(1f)) { Text("Uji putar") }
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSecondaryContainer),
                    modifier = Modifier.weight(1f),
                ) { Text("Buang") }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun List<MacroAction>.breakdown(): String = listOf(
    count { it is MacroAction.Tap } to "tap",
    count { it is MacroAction.Swipe } to "swipe",
    count { it is MacroAction.LongPress } to "long press",
).filter { it.first > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
