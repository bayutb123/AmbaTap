package com.ambacoding.ambatap.engine.player

/** Ukuran layar fisik dalam piksel, sesuai orientasi saat ini. */
data class ScreenSize(val widthPx: Int, val heightPx: Int)

/** Titik dalam piksel layar absolut. */
data class PxPoint(val x: Float, val y: Float)

/** Satu jari: lintasan [points] dilalui dalam [durationMs], mulai [startMs] setelah gesture dimulai. */
data class StrokeSpec(
    val points: List<PxPoint>,
    val startMs: Long,
    val durationMs: Long,
)

/**
 * Gesture siap kirim dalam piksel. Sengaja bebas dari kelas framework Android
 * agar bisa diuji di JVM; konversi ke `GestureDescription` ada di layer service.
 */
data class GestureSpec(val strokes: List<StrokeSpec>) {
    val totalDurationMs: Long get() = strokes.maxOf { it.startMs + it.durationMs }
}
