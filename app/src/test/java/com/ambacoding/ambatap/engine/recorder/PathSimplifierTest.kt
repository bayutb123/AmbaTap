package com.ambacoding.ambatap.engine.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

class PathSimplifierTest {

    @Test
    fun `straight line collapses to its endpoints`() {
        val line = (0..20).map { TouchSample(it * 10f, it * 5f, it * 16L) }

        assertEquals(listOf(line.first(), line.last()), PathSimplifier.simplify(line, epsilonPx = 1f))
    }

    @Test
    fun `corner of an L-shaped path is kept`() {
        val down = (0..10).map { TouchSample(0f, it * 10f, it * 10L) }
        val right = (1..10).map { TouchSample(it * 10f, 100f, 100L + it * 10L) }

        val simplified = PathSimplifier.simplify(down + right, epsilonPx = 1f)

        assertEquals(listOf(down.first(), down.last(), right.last()), simplified)
    }

    @Test
    fun `small jitter below epsilon is removed`() {
        val wobbly = (0..10).map { TouchSample(it * 10f, if (it % 2 == 0) 0f else 1f, it * 10L) }

        assertEquals(2, PathSimplifier.simplify(wobbly, epsilonPx = 2f).size)
    }

    @Test
    fun `short paths are returned unchanged`() {
        val two = listOf(TouchSample(0f, 0f, 0), TouchSample(5f, 5f, 10))

        assertEquals(two, PathSimplifier.simplify(two, epsilonPx = 100f))
    }
}
