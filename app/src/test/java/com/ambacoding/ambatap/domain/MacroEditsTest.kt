package com.ambacoding.ambatap.domain

import com.ambacoding.ambatap.domain.model.MacroAction
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
}
