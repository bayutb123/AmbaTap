package com.ambacoding.ambatap.data.local

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.Stroke
import com.ambacoding.ambatap.domain.model.TimedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroMapperTest {

    private val macro = Macro(
        id = 7,
        name = "Farming stage 3-2",
        actions = listOf(
            MacroAction.Tap(x = 0.5f, y = 0.75f),
            MacroAction.LongPress(x = 0.8f, y = 0.85f, durationMs = 800, delayBeforeMs = 200),
            MacroAction.Swipe(
                points = listOf(TimedPoint(0.5f, 0.66f, 0), TimedPoint(0.5f, 0.25f, 420)),
                durationMs = 420,
                delayBeforeMs = 800,
            ),
            MacroAction.MultiTouch(
                strokes = listOf(
                    Stroke(listOf(TimedPoint(0.4f, 0.5f, 0), TimedPoint(0.3f, 0.5f, 300))),
                    Stroke(listOf(TimedPoint(0.6f, 0.5f, 0), TimedPoint(0.7f, 0.5f, 300))),
                ),
            ),
            MacroAction.Wait(durationMs = 1_500),
            MacroAction.GlobalAction(GlobalType.BACK, delayBeforeMs = 300),
            MacroAction.LaunchApp("com.example.game"),
            MacroAction.InputText("halo"),
        ),
        screen = ScreenInfo(widthPx = 1080, heightPx = 2400, rotation = 0),
        config = PlaybackConfig(repeat = RepeatMode.Infinite, speed = 1.5f, randomOffsetPx = 4),
        createdAt = 1_000,
        updatedAt = 2_000,
    )

    @Test
    fun `entity round trip keeps every action type`() {
        val entity = macro.toEntity()

        assertEquals(MACRO_SCHEMA_VERSION, entity.schemaVersion)
        assertEquals(macro, entity.toDomain())
    }

    @Test
    fun `actions are tagged with stable type names`() {
        val json = macro.toEntity().actionsJson

        listOf("tap", "long_press", "swipe", "multi_touch", "wait", "global", "launch_app", "input_text")
            .forEach { type -> assertTrue("missing $type", json.contains("\"type\":\"$type\"")) }
    }

    @Test
    fun `unknown fields from newer versions are ignored`() {
        val entity = macro.toEntity().copy(
            actionsJson = """[{"type":"tap","x":0.1,"y":0.2,"pressure":0.9}]""",
        )

        assertEquals(listOf(MacroAction.Tap(x = 0.1f, y = 0.2f)), entity.toDomain().actions)
    }
}
