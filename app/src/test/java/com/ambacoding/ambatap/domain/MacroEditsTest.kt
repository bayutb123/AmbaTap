package com.ambacoding.ambatap.domain

import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.Stroke
import com.ambacoding.ambatap.domain.model.center
import com.ambacoding.ambatap.domain.model.durationMs
import com.ambacoding.ambatap.domain.model.pinchGesture
import com.ambacoding.ambatap.domain.model.reversed
import com.ambacoding.ambatap.domain.model.withCenter
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.domain.model.move
import com.ambacoding.ambatap.domain.model.withDelay
import com.ambacoding.ambatap.domain.model.withDuration
import com.ambacoding.ambatap.domain.model.withEndpoints
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroEditsTest {

    private val swipe = MacroAction.Swipe(
        points = listOf(TimedPoint(0.1f, 0.5f, 0), TimedPoint(0.3f, 0.6f, 100), TimedPoint(0.5f, 0.5f, 200)),
        durationMs = 200,
    )

    @Test
    fun `moving endpoints shifts middle points proportionally`() {
        val moved = swipe.withEndpoints(startX = 0.2f, startY = 0.5f, endX = 0.5f, endY = 0.7f)

        assertEquals(0.2f, moved.points[0].x, 1e-6f)
        assertEquals(0.35f, moved.points[1].x, 1e-6f)
        assertEquals(0.5f, moved.points[2].x, 1e-6f)
        assertEquals(0.5f, moved.points[0].y, 1e-6f)
        assertEquals(0.7f, moved.points[1].y, 1e-6f)
        assertEquals(0.7f, moved.points[2].y, 1e-6f)
    }

    @Test
    fun `changing swipe duration rescales point times`() {
        val slower = swipe.withDuration(400)

        assertEquals(listOf(0L, 200L, 400L), slower.points.map { it.tMs })
        assertEquals(400L, slower.durationMs)
    }

    @Test
    fun `delay is never negative`() {
        assertEquals(0L, MacroAction.Wait(100).withDelay(-5).delayBeforeMs)
    }

    @Test
    fun `move reorders and ignores out of range`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").move(0, 1))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").move(2, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").move(0, 5))
    }

    private val pinch = pinchGesture(0.5f, 0.5f, startSpan = 0.2f, endSpan = 0.6f, durationMs = 400)

    @Test
    fun `pinch fingers move apart around the center`() {
        val (left, right) = pinch.strokes
        assertEquals(listOf(0.4f, 0.2f), left.points.map { it.x }, 1e-5f)
        assertEquals(listOf(0.6f, 0.8f), right.points.map { it.x }, 1e-5f)
        assertEquals(listOf(0L, 400L), left.points.map { it.tMs })
        assertEquals(0.5f to 0.5f, pinch.center)
        assertEquals(400L, (pinch as MacroAction).durationMs)
    }

    @Test
    fun `moving a multi touch keeps its shape inside the screen`() {
        val moved = pinch.withCenter(0.3f, 0.2f)
        val left = moved.strokes[0].points
        assertEquals(0.2f, left[0].x, 1e-5f)
        assertEquals(0f, left[1].x, 1e-5f) // 0.0 setelah clamp
        assertEquals(0.2f, left[0].y, 1e-5f)
    }

    @Test
    fun `scaling a multi touch scales start delays and timings`() {
        val gesture = MacroAction.MultiTouch(
            listOf(
                Stroke(listOf(TimedPoint(0.1f, 0.1f, 0), TimedPoint(0.2f, 0.2f, 200))),
                Stroke(listOf(TimedPoint(0.5f, 0.5f, 0), TimedPoint(0.6f, 0.6f, 100)), startDelayMs = 100),
            ),
        )
        val slower = gesture.withDuration(400)
        assertEquals(400L, (slower as MacroAction).durationMs)
        assertEquals(200L, slower.strokes[1].startDelayMs)
        assertEquals(400L, slower.strokes[0].points.last().tMs)
    }

    @Test
    fun `reversing turns zoom in into zoom out`() {
        val reversed = pinch.reversed()
        assertEquals(listOf(0.2f, 0.4f), reversed.strokes[0].points.map { it.x }, 1e-5f)
        assertEquals(listOf(0L, 400L), reversed.strokes[0].points.map { it.tMs })
        assertEquals(pinch, reversed.reversed())
    }

    @Test
    fun `reversing mirrors staggered finger timing`() {
        val gesture = MacroAction.MultiTouch(
            listOf(
                Stroke(listOf(TimedPoint(0f, 0f, 0), TimedPoint(0f, 0f, 300))),
                Stroke(listOf(TimedPoint(1f, 1f, 0), TimedPoint(1f, 1f, 100)), startDelayMs = 50),
            ),
        )
        // Jari kedua berakhir di 150 dari total 300, jadi saat dibalik mulai di 150.
        assertEquals(150L, gesture.reversed().strokes[1].startDelayMs)
    }
}

private fun assertEquals(expected: List<Float>, actual: List<Float>, delta: Float) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, delta) }
}
