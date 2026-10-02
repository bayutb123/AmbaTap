package com.ambacoding.ambatap.engine.recorder

import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.engine.player.ScreenSize
import kotlin.math.hypot

/** Mengubah satu sentuhan (turun → angkat) menjadi aksi bertitik ternormalisasi, tanpa jeda. */
object GestureClassifier {

    /**
     * @param touchSlopPx gerakan maksimal yang masih dianggap diam (tap/long press).
     * @param longPressMs durasi minimal agar sentuhan diam dianggap long press.
     * @param simplifyEpsilonPx toleransi penyederhanaan jalur swipe.
     */
    fun classify(
        samples: List<TouchSample>,
        screen: ScreenSize,
        touchSlopPx: Float,
        longPressMs: Long,
        simplifyEpsilonPx: Float,
    ): MacroAction {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        val first = samples.first()
        val durationMs = (samples.last().tMs - first.tMs).coerceAtLeast(MIN_DURATION_MS)
        val travelled = samples.maxOf { hypot(it.x - first.x, it.y - first.y) }

        fun nx(x: Float) = (x / screen.widthPx).coerceIn(0f, 1f)
        fun ny(y: Float) = (y / screen.heightPx).coerceIn(0f, 1f)

        return when {
            travelled > touchSlopPx -> MacroAction.Swipe(
                points = PathSimplifier.simplify(samples, simplifyEpsilonPx)
                    .map { TimedPoint(nx(it.x), ny(it.y), it.tMs - first.tMs) },
                durationMs = durationMs,
            )
            durationMs >= longPressMs -> MacroAction.LongPress(nx(first.x), ny(first.y), durationMs)
            else -> MacroAction.Tap(nx(first.x), ny(first.y), durationMs)
        }
    }

    private const val MIN_DURATION_MS = 1L
}
