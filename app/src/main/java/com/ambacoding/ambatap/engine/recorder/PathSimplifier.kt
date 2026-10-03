package com.ambacoding.ambatap.engine.recorder

import kotlin.math.hypot

/** Satu sampel sentuhan dalam piksel layar absolut dengan waktu `uptimeMillis`. */
data class TouchSample(val x: Float, val y: Float, val tMs: Long)

/** Ramer–Douglas–Peucker: buang titik yang menyimpang kurang dari [epsilonPx] dari garis lurus. */
object PathSimplifier {

    fun simplify(points: List<TouchSample>, epsilonPx: Float): List<TouchSample> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        mark(points, 0, points.lastIndex, epsilonPx, keep)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun mark(points: List<TouchSample>, start: Int, end: Int, epsilon: Float, keep: BooleanArray) {
        // Iteratif dengan stack agar swipe panjang tidak membuat rekursi dalam.
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(start to end)
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var maxDistance = 0f
            var index = -1
            for (i in from + 1 until to) {
                val d = distanceToSegment(points[i], points[from], points[to])
                if (d > maxDistance) {
                    maxDistance = d
                    index = i
                }
            }
            if (index != -1 && maxDistance > epsilon) {
                keep[index] = true
                stack.addLast(from to index)
                stack.addLast(index to to)
            }
        }
    }

    private fun distanceToSegment(p: TouchSample, a: TouchSample, b: TouchSample): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}
