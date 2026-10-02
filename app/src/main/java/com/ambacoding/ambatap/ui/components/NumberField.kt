package com.ambacoding.ambatap.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.ambacoding.ambatap.ui.theme.MonoStyle

/** Kolom angka bulat; nilai di luar [range] di-clamp, isian kosong tidak mengubah nilai. */
@Composable
fun NumberField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    range: LongRange = 0L..Long.MAX_VALUE,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(9)
            text = digits
            digits.toLongOrNull()?.let { onValueChange(it.coerceIn(range)) }
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        textStyle = MonoStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
