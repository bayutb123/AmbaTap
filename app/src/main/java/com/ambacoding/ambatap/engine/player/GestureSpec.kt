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

    /**
     * `true` bila lintasan salah satu jari melewati persegi panjang [left, right) × [top, bottom).
     * Ruas antar titik ikut dicek dengan sampling agar swipe yang melintas tetap terdeteksi.
     */
    fun intersects(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        fun inside(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
        return strokes.any { stroke ->
            stroke.points.any { inside(it.x, it.y) } ||
                stroke.points.zipWithNext().any { (a, b) ->
                    (1 until SEGMENT_SAMPLES).any { i ->
                        val t = i.toFloat() / SEGMENT_SAMPLES
                        inside(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    }
                }
        }
    }

    private companion object {
        const val SEGMENT_SAMPLES = 32
    }
}
