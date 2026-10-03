package com.ambacoding.ambatap.service

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.ambacoding.ambatap.engine.player.GestureSpec

/**
 * Konversi ke `GestureDescription`, dengan batas sistem: jumlah jari maksimal
 * `getMaxStrokeCount()` dan total durasi maksimal `getMaxGestureDuration()`.
 */
fun GestureSpec.toGestureDescription(): GestureDescription {
    val maxDuration = GestureDescription.getMaxGestureDuration()
    val builder = GestureDescription.Builder()
    // TODO: pecah gesture lebih panjang dari maxDuration dengan StrokeDescription.continueStroke().
    strokes.take(GestureDescription.getMaxStrokeCount()).forEach { stroke ->
        val path = Path().apply {
            val first = stroke.points.first()
            moveTo(first.x, first.y)
            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val start = stroke.startMs.coerceIn(0, maxDuration - 1)
        val duration = stroke.durationMs.coerceIn(1, maxDuration - start)
        builder.addStroke(GestureDescription.StrokeDescription(path, start, duration))
    }
    return builder.build()
}
