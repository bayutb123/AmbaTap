package com.ambacoding.ambatap.engine.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSpecTest {

    private fun gesture(vararg points: Pair<Float, Float>) = GestureSpec(
        listOf(StrokeSpec(points.map { (x, y) -> PxPoint(x, y) }, startMs = 0, durationMs = 100)),
    )

    @Test
    fun `tap inside the rectangle intersects`() {
        assertTrue(gesture(150f to 250f).intersects(100, 200, 200, 300))
    }

    @Test
    fun `tap outside or on the exclusive edge does not intersect`() {
        assertFalse(gesture(50f to 250f).intersects(100, 200, 200, 300))
        assertFalse(gesture(200f to 250f).intersects(100, 200, 200, 300))
    }

    @Test
    fun `swipe crossing the rectangle intersects even with no point inside`() {
        assertTrue(gesture(0f to 250f, 400f to 250f).intersects(100, 200, 200, 300))
    }

    @Test
    fun `swipe passing beside the rectangle does not intersect`() {
        assertFalse(gesture(0f to 350f, 400f to 350f).intersects(100, 200, 200, 300))
    }
}
