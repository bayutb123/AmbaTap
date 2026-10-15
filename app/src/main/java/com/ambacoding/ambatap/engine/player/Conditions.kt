package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.TextMatch
import kotlin.math.abs
import kotlin.math.max

/** Pencocokan teks elemen layar untuk aksi `WaitForText`. */
object TextMatcher {
    /** Spasi di tepi diabaikan dan huruf besar/kecil tidak dibedakan; kata kunci kosong tidak cocok. */
    fun matches(candidate: CharSequence?, query: String, mode: TextMatch): Boolean {
        val needle = query.trim()
        if (candidate == null || needle.isEmpty()) return false
        val text = candidate.toString().trim()
        return when (mode) {
            TextMatch.CONTAINS -> text.contains(needle, ignoreCase = true)
            TextMatch.EXACT -> text.equals(needle, ignoreCase = true)
        }
    }
}

/** Perbandingan warna untuk aksi `WaitForColor`; alfa diabaikan. */
object ColorMatcher {
    /** Selisih terbesar di antara kanal merah, hijau, dan biru (0..255). */
    fun distance(a: Int, b: Int): Int = max(
        abs(channel(a, 16) - channel(b, 16)),
        max(abs(channel(a, 8) - channel(b, 8)), abs(channel(a, 0) - channel(b, 0))),
    )

    fun matches(actual: Int, expected: Int, tolerance: Int): Boolean =
        distance(actual, expected) <= tolerance.coerceIn(0, 255)

    /** `#RRGGBB` huruf besar. */
    fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    /** Menerima `RRGGBB` atau `#RRGGBB`; hasilnya beralfa penuh, `null` bila tidak valid. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)?.let { it or OPAQUE }
    }

    private fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF

    private const val OPAQUE = 0xFF shl 24
}
