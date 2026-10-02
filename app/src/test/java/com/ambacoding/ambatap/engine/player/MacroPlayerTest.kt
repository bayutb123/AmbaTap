package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.engine.player.PlaybackState.Status
import com.ambacoding.ambatap.service.ServiceBridge
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroPlayerTest {

    private class FakeController(private val scheduler: TestCoroutineScheduler) : InputController {
        /** (waktu virtual, kejadian) */
        val log = mutableListOf<Pair<Long, Any>>()

        override val screenSize = ScreenSize(1000, 2000)

        override suspend fun dispatch(gesture: GestureSpec): Boolean {
            log += scheduler.currentTime to gesture
            delay(gesture.totalDurationMs)
            return true
        }

        override fun performGlobal(type: GlobalType): Boolean {
            log += scheduler.currentTime to type
            return true
        }

        override fun launchApp(packageName: String): Boolean {
            log += scheduler.currentTime to packageName
            return true
        }

        override fun inputText(text: String): Boolean {
            log += scheduler.currentTime to text
            return true
        }

        val times get() = log.map { it.first }
    }

    private fun TestScope.setUp(connected: Boolean = true): Pair<MacroPlayer, FakeController> {
        val bridge = ServiceBridge()
        val controller = FakeController(testScheduler)
        if (connected) bridge.attach(controller)
        // Scope test biasa (bukan backgroundScope) agar advanceUntilIdle ikut menjalankan player.
        val player = MacroPlayer(bridge, this, Random(0), testScheduler.timeSource)
        return player to controller
    }

    private fun macro(vararg actions: MacroAction, config: PlaybackConfig = PlaybackConfig()) = Macro(
        name = "test",
        actions = actions.toList(),
        screen = ScreenInfo(1000, 2000, 0),
        config = config,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `plays actions in order honoring delays and gesture durations`() = runTest {
        val (player, controller) = setUp()

        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50),
                MacroAction.Tap(0.1f, 0.1f, durationMs = 50, delayBeforeMs = 300),
                MacroAction.GlobalAction(GlobalType.BACK, delayBeforeMs = 100),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(0L, 350L, 500L), controller.times)
        val firstTap = controller.log.first().second as GestureSpec
        assertEquals(PxPoint(500f, 1000f), firstTap.strokes.single().points.single())
        assertEquals(GlobalType.BACK, controller.log.last().second)
        assertEquals("test", player.lastMacro.value?.name)
        assertEquals(PlaybackState(), player.state.value)
    }

    @Test
    fun `repeats the given number of times with a gap between loops`() = runTest {
        val (player, controller) = setUp()

        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50),
                config = PlaybackConfig(repeat = RepeatMode.Count(3), delayBetweenLoopsMs = 200),
            ),
        )
        runCurrent()
        assertEquals(3, player.state.value.totalLoops)
        advanceUntilIdle()

        assertEquals(listOf(0L, 250L, 500L), controller.times)
    }

    @Test
    fun `speed scales delays, waits and gestures`() = runTest {
        val (player, controller) = setUp()

        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 100),
                MacroAction.Wait(durationMs = 400),
                MacroAction.Tap(0.5f, 0.5f, durationMs = 100, delayBeforeMs = 200),
                config = PlaybackConfig(speed = 2f),
            ),
        )
        advanceUntilIdle()

        // 50 (tap) + 200 (wait) + 100 (delay) = 350
        assertEquals(listOf(0L, 350L), controller.times)
    }

    @Test
    fun `pause freezes the remaining delay until resume`() = runTest {
        val (player, controller) = setUp()
        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50),
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50, delayBeforeMs = 1_000),
            ),
        )

        advanceTimeBy(500)
        player.pause()
        advanceTimeBy(5_000)
        assertEquals(Status.PAUSED, player.state.value.status)
        assertEquals(listOf(0L), controller.times)

        player.resume()
        advanceUntilIdle()

        // 450 ms dari jeda 1000 ms sudah berjalan sebelum pause, sisanya 550 ms setelah resume.
        assertEquals(listOf(0L, 6_050L), controller.times)
    }

    @Test
    fun `stop ends an infinite macro`() = runTest {
        val (player, controller) = setUp()
        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50, delayBeforeMs = 100),
                config = PlaybackConfig(repeat = RepeatMode.Infinite),
            ),
        )

        advanceTimeBy(1_000)
        player.stop()
        val count = controller.log.size
        advanceTimeBy(1_000)

        assertEquals(count, controller.log.size)
        assertEquals(PlaybackState(), player.state.value)
    }

    @Test
    fun `duration mode stops once the time is up`() = runTest {
        val (player, controller) = setUp()

        player.play(
            macro(
                MacroAction.Tap(0.5f, 0.5f, durationMs = 50, delayBeforeMs = 200),
                config = PlaybackConfig(repeat = RepeatMode.Duration(1_000)),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(200L, 450L, 700L, 950L), controller.times)
    }

    @Test
    fun `countdown delays the first action`() = runTest {
        val (player, controller) = setUp()

        player.play(macro(MacroAction.Tap(0.5f, 0.5f)), startDelayMs = 3_000)
        advanceTimeBy(1_500)
        assertEquals(Status.COUNTDOWN, player.state.value.status)
        assertEquals(2_000, player.state.value.countdownMs)
        advanceUntilIdle()

        assertEquals(listOf(3_000L), controller.times)
    }

    @Test
    fun `playing a new macro replaces the running one`() = runTest {
        val (player, controller) = setUp()
        player.play(
            macro(
                MacroAction.GlobalAction(GlobalType.HOME, delayBeforeMs = 100),
                config = PlaybackConfig(repeat = RepeatMode.Infinite),
            ),
        )
        advanceTimeBy(250)

        player.play(macro(MacroAction.GlobalAction(GlobalType.BACK)))
        advanceUntilIdle()

        assertEquals(listOf(GlobalType.HOME, GlobalType.HOME, GlobalType.BACK), controller.log.map { it.second })
        assertEquals(PlaybackState(), player.state.value)
    }

    @Test
    fun `reports an error when the service is not connected`() = runTest {
        val (player, _) = setUp(connected = false)

        player.play(macro(MacroAction.Tap(0.5f, 0.5f)))

        assertEquals(PlaybackState(error = PlaybackError.SERVICE_NOT_CONNECTED), player.state.value)
        assertEquals(null, player.lastMacro.value)
    }

    @Test
    fun `reports an error for an empty macro`() = runTest {
        val (player, _) = setUp()

        player.play(macro())

        assertEquals(PlaybackError.EMPTY_MACRO, player.state.value.error)
    }
}
