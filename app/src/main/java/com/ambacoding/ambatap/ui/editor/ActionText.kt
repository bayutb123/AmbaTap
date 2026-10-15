package com.ambacoding.ambatap.ui.editor

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.engine.player.ColorMatcher
import kotlin.math.roundToInt

fun MacroAction.title(): String = when (this) {
    is MacroAction.Tap -> "Tap"
    is MacroAction.LongPress -> "Long press"
    is MacroAction.Swipe -> "Swipe"
    is MacroAction.MultiTouch -> "Multi-touch"
    is MacroAction.Wait -> "Tunggu"
    is MacroAction.GlobalAction -> action.title()
    is MacroAction.LaunchApp -> "Buka aplikasi"
    is MacroAction.InputText -> "Ketik teks"
    is MacroAction.WaitForText -> "Tunggu teks"
    is MacroAction.WaitForColor -> "Tunggu warna"
}

fun GlobalType.title(): String = when (this) {
    GlobalType.BACK -> "Kembali"
    GlobalType.HOME -> "Beranda"
    GlobalType.RECENTS -> "Terbaru"
    GlobalType.NOTIFICATIONS -> "Notifikasi"
}

/** Ringkasan satu baris; koordinat ditampilkan dalam piksel layar saat direkam. */
fun MacroAction.detail(screen: ScreenInfo): String {
    fun px(x: Float, y: Float) = "${(x * screen.widthPx).roundToInt()}, ${(y * screen.heightPx).roundToInt()}"
    return when (this) {
        is MacroAction.Tap -> px(x, y)
        is MacroAction.LongPress -> "${px(x, y)} · $durationMs ms"
        is MacroAction.Swipe -> "${points.size} titik · $durationMs ms"
        is MacroAction.MultiTouch -> "${strokes.size} jari"
        is MacroAction.Wait -> seconds(durationMs)
        is MacroAction.GlobalAction -> "aksi global"
        is MacroAction.LaunchApp -> packageName.ifEmpty { "belum diisi" }
        is MacroAction.InputText -> text.ifEmpty { "belum diisi" }
        is MacroAction.WaitForText -> if (text.isBlank()) {
            "belum diisi"
        } else {
            "\"$text\" ${if (present) "muncul" else "hilang"} · ${seconds(timeoutMs)}" + if (tapOnFound && present) " · tap" else ""
        }
        is MacroAction.WaitForColor -> {
            val color = if (color == 0) "warna belum diambil" else ColorMatcher.toHex(color)
            "${px(x, y)} · $color" + if (tapOnFound && present) " · tap" else ""
        }
    }
}

private fun seconds(ms: Long) = "%.1f dtk".format(ms / 1000.0)
