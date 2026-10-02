package com.bayutb123.ambatap.engine.player

import com.bayutb123.ambatap.domain.model.MacroAction
import com.bayutb123.ambatap.domain.model.TimedPoint
import kotlin.math.roundToLong
import kotlin.random.Random

/** Mengubah aksi bertitik ternormalisasi menjadi [GestureSpec] dalam piksel layar saat ini. */
object GestureFactory {

    /**
     * @param speed pengali kecepatan; 2f membuat gesture dua kali lebih cepat.
     * @param jitterPx geser acak tiap jari sejauh maksimal ± nilai ini (0 = tanpa acak).
     * @return `null` untuk aksi yang bukan gesture (Wait, GlobalAction, dll.).
     */
    fun create(
        action: MacroAction,
        screen: ScreenSize,
        speed: Float = 1f,
        jitterPx: Int = 0,
        random: Random = Random.Default,
    ): GestureSpec? {
        fun stroke(points: List<TimedPoint>, startMs: Long, durationMs: Long): StrokeSpec {
            val dx = jitter(jitterPx, random)
            val dy = jitter(jitterPx, random)
            return StrokeSpec(
                points = points.map { screen.toPx(it.x, it.y, dx, dy) },
                startMs = scale(startMs, speed, min = 0),
                durationMs = scale(durationMs, speed, min = MIN_DURATION_MS),
            )
        }

        return when (action) {
            is MacroAction.Tap -> GestureSpec(
                listOf(stroke(listOf(TimedPoint(action.x, action.y, 0)), 0, action.durationMs)),
            )
            is MacroAction.LongPress -> GestureSpec(
                listOf(stroke(listOf(TimedPoint(action.x, action.y, 0)), 0, action.durationMs)),
            )
            is MacroAction.Swipe -> {
                if (action.points.isEmpty()) return null
                GestureSpec(listOf(stroke(action.points, 0, action.durationMs)))
            }
            is MacroAction.MultiTouch -> {
                val strokes = action.strokes
                    .filter { it.points.isNotEmpty() }
                    .map { s -> stroke(s.points, s.startDelayMs, s.points.last().tMs - s.points.first().tMs) }
                if (strokes.isEmpty()) null else GestureSpec(strokes)
            }
            is MacroAction.Wait,
            is MacroAction.GlobalAction,
            is MacroAction.LaunchApp,
            is MacroAction.InputText,
            -> null
        }
    }

    /** Durasi minimal yang diterima `StrokeDescription`. */
    const val MIN_DURATION_MS = 1L

    fun scale(ms: Long, speed: Float, min: Long = 0): Long =
        (ms / speed.coerceAtLeast(MIN_SPEED)).roundToLong().coerceAtLeast(min)

    private const val MIN_SPEED = 0.1f

    private fun jitter(maxPx: Int, random: Random): Float =
        if (maxPx <= 0) 0f else random.nextInt(-maxPx, maxPx + 1).toFloat()

    /** Koordinat di luar layar membuat `dispatchGesture` gagal, jadi selalu di-clamp. */
    private fun ScreenSize.toPx(nx: Float, ny: Float, dx: Float, dy: Float) = PxPoint(
        x = (nx * widthPx + dx).coerceIn(0f, (widthPx - 1).toFloat()),
        y = (ny * heightPx + dy).coerceIn(0f, (heightPx - 1).toFloat()),
    )
}
