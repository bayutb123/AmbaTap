package com.ambacoding.ambatap.ui.components

import com.ambacoding.ambatap.domain.model.RepeatMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Durasi sebagai `m:ss`, mis. 65_000 → "1:05". */
fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}

fun RepeatMode.label(): String = when (this) {
    is RepeatMode.Count -> "$times×"
    RepeatMode.Infinite -> "∞"
    is RepeatMode.Duration -> formatClock(millis)
}

/** Nama bawaan seperti "Rekaman 2 Okt, 14.20". */
fun defaultMacroName(prefix: String, nowMs: Long = System.currentTimeMillis()): String =
    "$prefix " + SimpleDateFormat("d MMM, HH.mm", Locale.forLanguageTag("id-ID")).format(Date(nowMs))
