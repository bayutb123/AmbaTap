package com.ambacoding.ambatap.engine.recorder

import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.engine.recorder.RecordingState.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MacroRecorderTest {

    private val screen = ScreenInfo(1080, 2400, 0)
    private val tap = MacroAction.Tap(0.5f, 0.5f, durationMs = 50)

    @Test
    fun `delays are measured from the end of the previous action`() {
        val recorder = MacroRecorder()
        recorder.start(screen, nowMs = 1_000)

        recorder.append(tap, startMs = 3_000, endMs = 3_050)
        recorder.append(tap, startMs = 3_400, endMs = 3_450)

        assertEquals(listOf(0L, 350L), recorder.state.value.actions.map { it.delayBeforeMs })
    }

    @Test
    fun `paused time is excluded from delays and elapsed time`() {
        val recorder = MacroRecorder()
        recorder.start(screen, nowMs = 0)
        recorder.append(tap, startMs = 1_000, endMs = 1_050)

        recorder.pause(nowMs = 1_500)
        recorder.resume(nowMs = 11_500)
        recorder.append(tap, startMs = 12_000, endMs = 12_050)

        assertEquals(950L, recorder.state.value.actions[1].delayBeforeMs)
        assertEquals(2_500L, recorder.state.value.elapsedMs(nowMs = 12_500))
    }

    @Test
    fun `actions are ignored when not recording`() {
        val recorder = MacroRecorder()

        recorder.append(tap, startMs = 0, endMs = 50)

        assertEquals(RecordingState(), recorder.state.value)
    }

    @Test
    fun `finish moves to review and builds a draft`() {
        val recorder = MacroRecorder()
        recorder.start(screen, nowMs = 0)
        recorder.append(tap, startMs = 100, endMs = 150)

        recorder.finish(nowMs = 2_000)
        val draft = recorder.buildDraft("Rekaman", PlaybackConfig(), createdAt = 99)

        assertEquals(Status.REVIEW, recorder.state.value.status)
        assertEquals(2_000L, recorder.state.value.elapsedMs(nowMs = 9_999))
        assertEquals(listOf(tap), draft?.actions)
        assertEquals(screen, draft?.screen)
    }

    @Test
    fun `finishing without actions cancels`() {
        val recorder = MacroRecorder()
        recorder.start(screen, nowMs = 0)

        recorder.finish(nowMs = 500)

        assertEquals(RecordingState(), recorder.state.value)
        assertNull(recorder.buildDraft("x", PlaybackConfig(), 0))
    }

    @Test
    fun `cannot start while another recording is in progress`() {
        val recorder = MacroRecorder()
        recorder.start(screen, nowMs = 0)

        assertFalse(recorder.start(screen, nowMs = 10))
    }
}
