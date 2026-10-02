package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.Stroke
import com.ambacoding.ambatap.domain.model.TimedPoint
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureFactoryTest {

    private val screen = ScreenSize(widthPx = 1000, heightPx = 2000)

    @Test
    fun `tap is converted to screen pixels`() {
        val gesture = GestureFactory.create(MacroAction.Tap(0.25f, 0.5f, durationMs = 60), screen)!!

        assertEquals(
            listOf(StrokeSpec(listOf(PxPoint(250f, 1000f)), startMs = 0, durationMs = 60)),
            gesture.strokes,
        )
    }

    @Test
    fun `points outside the screen are clamped`() {
        val gesture = GestureFactory.create(MacroAction.Tap(1f, -0.2f), screen)!!

        assertEquals(PxPoint(999f, 0f), gesture.strokes.single().points.single())
    }

    @Test
    fun `swipe keeps every point and its duration`() {
        val swipe = MacroAction.Swipe(
            points = listOf(TimedPoint(0.1f, 0.8f, 0), TimedPoint(0.5f, 0.5f, 100), TimedPoint(0.9f, 0.2f, 300)),
            durationMs = 300,
        )

        val stroke = GestureFactory.create(swipe, screen)!!.strokes.single()

        assertEquals(listOf(PxPoint(100f, 1600f), PxPoint(500f, 1000f), PxPoint(900f, 400f)), stroke.points)
        assertEquals(300, stroke.durationMs)
    }

    @Test
    fun `speed scales durations and stroke start times`() {
        val pinch = MacroAction.MultiTouch(
            strokes = listOf(
                Stroke(listOf(TimedPoint(0.4f, 0.5f, 0), TimedPoint(0.3f, 0.5f, 400))),
                Stroke(listOf(TimedPoint(0.6f, 0.5f, 0), TimedPoint(0.7f, 0.5f, 400)), startDelayMs = 100),
            ),
        )

        val strokes = GestureFactory.create(pinch, screen, speed = 2f)!!.strokes

        assertEquals(listOf(0L, 50L), strokes.map { it.startMs })
        assertEquals(listOf(200L, 200L), strokes.map { it.durationMs })
    }

    @Test
    fun `duration never drops below the minimum`() {
        val gesture = GestureFactory.create(MacroAction.Tap(0.5f, 0.5f, durationMs = 1), screen, speed = 4f)!!

        assertEquals(GestureFactory.MIN_DURATION_MS, gesture.strokes.single().durationMs)
    }

    @Test
    fun `jitter moves a whole stroke within the given range`() {
        val random = Random(42)
        val swipe = MacroAction.Swipe(
            points = listOf(TimedPoint(0.5f, 0.5f, 0), TimedPoint(0.5f, 0.6f, 100)),
            durationMs = 100,
        )

        repeat(200) {
            val (start, end) = GestureFactory.create(swipe, screen, jitterPx = 5, random = random)!!
                .strokes.single().points
            assertTrue(start.x in 495f..505f && start.y in 995f..1005f)
            assertEquals(start.x, end.x)
            assertEquals(200f, end.y - start.y)
        }
    }

    @Test
    fun `non gesture actions produce no gesture`() {
        listOf(
            MacroAction.Wait(100),
            MacroAction.GlobalAction(GlobalType.HOME),
            MacroAction.LaunchApp("a.b"),
            MacroAction.InputText("x"),
            MacroAction.Swipe(points = emptyList(), durationMs = 100),
        ).forEach { assertNull(GestureFactory.create(it, screen)) }
    }
}
