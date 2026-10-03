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
