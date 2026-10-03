package com.ambacoding.ambatap.engine.recorder

import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.engine.player.ScreenSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierTest {

    private val screen = ScreenSize(1000, 2000)

    private fun classify(vararg samples: TouchSample) = GestureClassifier.classify(
        samples.toList(),
        screen,
        touchSlopPx = 20f,
        longPressMs = 500,
        simplifyEpsilonPx = 2f,
    )

    @Test
    fun `short still touch is a tap at the down position`() {
        val action = classify(TouchSample(250f, 500f, 1_000), TouchSample(255f, 503f, 1_080))

        assertEquals(MacroAction.Tap(0.25f, 0.25f, durationMs = 80), action)
    }

    @Test
    fun `long still touch is a long press`() {
        val action = classify(TouchSample(500f, 1_000f, 0), TouchSample(510f, 1_000f, 700))

        assertEquals(MacroAction.LongPress(0.5f, 0.5f, durationMs = 700), action)
    }

    @Test
    fun `movement beyond the slop is a swipe with relative times`() {
        val action = classify(
            TouchSample(500f, 1_600f, 5_000),
            TouchSample(500f, 1_200f, 5_100),
            TouchSample(500f, 800f, 5_200),
        )

        assertEquals(
            MacroAction.Swipe(
                points = listOf(TimedPoint(0.5f, 0.8f, 0), TimedPoint(0.5f, 0.4f, 200)),
                durationMs = 200,
            ),
            action,
        )
    }

    @Test
    fun `slow drag is a swipe, not a long press`() {
        val action = classify(TouchSample(0f, 0f, 0), TouchSample(300f, 0f, 2_000))

        assertEquals(MacroAction.Swipe::class, action::class)
    }

    @Test
    fun `instant touch gets the minimum duration`() {
        val action = classify(TouchSample(100f, 100f, 42))

        assertEquals(1L, (action as MacroAction.Tap).durationMs)
    }
}
