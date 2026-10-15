package com.ambacoding.ambatap.domain.model

/** Salinan aksi dengan jeda sebelum yang baru. */
fun MacroAction.withDelay(delayMs: Long): MacroAction {
    val delay = delayMs.coerceAtLeast(0)
    return when (this) {
        is MacroAction.Tap -> copy(delayBeforeMs = delay)
        is MacroAction.LongPress -> copy(delayBeforeMs = delay)
        is MacroAction.Swipe -> copy(delayBeforeMs = delay)
        is MacroAction.MultiTouch -> copy(delayBeforeMs = delay)
        is MacroAction.Wait -> copy(delayBeforeMs = delay)
        is MacroAction.GlobalAction -> copy(delayBeforeMs = delay)
        is MacroAction.LaunchApp -> copy(delayBeforeMs = delay)
        is MacroAction.InputText -> copy(delayBeforeMs = delay)
        is MacroAction.WaitForText -> copy(delayBeforeMs = delay)
        is MacroAction.WaitForColor -> copy(delayBeforeMs = delay)
    }
}

/**
 * Memindahkan titik awal dan akhir swipe; titik di antaranya ikut bergeser secara
 * proporsional sehingga bentuk lintasan tetap terjaga.
 */
fun MacroAction.Swipe.withEndpoints(startX: Float, startY: Float, endX: Float, endY: Float): MacroAction.Swipe {
    if (points.isEmpty()) return this
    val first = points.first()
    val last = points.last()
    val dsx = startX - first.x
    val dsy = startY - first.y
    val dex = endX - last.x
    val dey = endY - last.y
    val lastIndex = points.lastIndex.coerceAtLeast(1)
    return copy(
        points = points.mapIndexed { i, p ->
            val t = i.toFloat() / lastIndex
            p.copy(
                x = (p.x + dsx * (1 - t) + dex * t).coerceIn(0f, 1f),
                y = (p.y + dsy * (1 - t) + dey * t).coerceIn(0f, 1f),
            )
        },
    )
}

/** Mengubah durasi swipe sambil menskalakan waktu tiap titik. */
fun MacroAction.Swipe.withDuration(durationMs: Long): MacroAction.Swipe {
    val newDuration = durationMs.coerceAtLeast(1)
    val factor = if (this.durationMs > 0) newDuration.toDouble() / this.durationMs else 1.0
    return copy(
        durationMs = newDuration,
        points = points.map { it.copy(tMs = (it.tMs * factor).toLong()) },
    )
}

/** Memindahkan elemen pada [from] ke [to]; indeks di luar batas diabaikan. */
fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Gesture dua jari horizontal di sekitar ([cx], [cy]); jarak jari dalam satuan lebar layar. */
fun pinchGesture(
    cx: Float,
    cy: Float,
    startSpan: Float,
    endSpan: Float,
    durationMs: Long,
    delayBeforeMs: Long = 0,
): MacroAction.MultiTouch {
    fun finger(sign: Int) = Stroke(
        points = listOf(
            TimedPoint((cx + sign * startSpan / 2).coerceIn(0f, 1f), cy, 0),
            TimedPoint((cx + sign * endSpan / 2).coerceIn(0f, 1f), cy, durationMs.coerceAtLeast(1)),
        ),
    )
    return MacroAction.MultiTouch(listOf(finger(-1), finger(1)), delayBeforeMs)
}

/** Titik tengah semua titik awal jari; dipakai sebagai pegangan untuk memindah gesture. */
val MacroAction.MultiTouch.center: Pair<Float, Float>?
    get() {
        val starts = strokes.mapNotNull { it.points.firstOrNull() }
        if (starts.isEmpty()) return null
        return starts.map { it.x }.average().toFloat() to starts.map { it.y }.average().toFloat()
    }

/** Memindah seluruh gesture agar [center] berada di ([x], [y]); titik tetap di dalam layar. */
fun MacroAction.MultiTouch.withCenter(x: Float, y: Float): MacroAction.MultiTouch {
    val (cx, cy) = center ?: return this
    val dx = x - cx
    val dy = y - cy
    return copy(
        strokes = strokes.map { s ->
            s.copy(points = s.points.map { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) })
        },
    )
}

/** Mengubah lama gesture; waktu mulai tiap jari dan titiknya diskalakan sebanding. */
fun MacroAction.MultiTouch.withDuration(durationMs: Long): MacroAction.MultiTouch {
    val current = this.durationMs
    val factor = if (current > 0) durationMs.coerceAtLeast(1).toDouble() / current else 1.0
    return copy(
        strokes = strokes.map { s ->
            s.copy(
                startDelayMs = (s.startDelayMs * factor).toLong(),
                points = s.points.map { it.copy(tMs = (it.tMs * factor).toLong()) },
            )
        },
    )
}

/** Memutar gesture mundur: cubit masuk menjadi cubit keluar dan sebaliknya. */
fun MacroAction.MultiTouch.reversed(): MacroAction.MultiTouch {
    val total = durationMs
    return copy(
        strokes = strokes.filter { it.points.isNotEmpty() }.map { s ->
            val first = s.points.first().tMs
            val last = s.points.last().tMs
            s.copy(
                startDelayMs = (total - s.startDelayMs - (last - first)).coerceAtLeast(0),
                points = s.points.reversed().map { it.copy(tMs = last - it.tMs) },
            )
        },
    )
}
