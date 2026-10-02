package com.ambacoding.ambatap.data.transfer

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.TimedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTransferTest {

    private val macro = Macro(
        id = 42,
        name = "Farming",
        actions = listOf(
            MacroAction.Tap(0.5f, 0.5f),
            MacroAction.Swipe(listOf(TimedPoint(0.1f, 0.9f, 0), TimedPoint(0.1f, 0.2f, 300)), 300, 150),
            MacroAction.GlobalAction(GlobalType.BACK),
        ),
        screen = ScreenInfo(1080, 2400, 0),
        config = PlaybackConfig(repeat = RepeatMode.Count(5), speed = 2f),
        createdAt = 1_000,
        updatedAt = 2_000,
    )

    @Test
    fun `round trip keeps content and drops the local id`() {
        val decoded = MacroTransfer.decode(MacroTransfer.encode(listOf(macro)), nowMs = 9_000).single()

        assertEquals(macro.copy(id = 0, updatedAt = 9_000), decoded)
    }

    @Test
    fun `file is tagged with format and version`() {
        val content = MacroTransfer.encode(listOf(macro))

        assertTrue(content.contains("\"format\": \"ambatap-macros\""))
        assertTrue(content.contains("\"version\": 1"))
    }

    @Test
    fun `missing creation time falls back to now`() {
        val content = """
            {"format":"ambatap-macros","version":1,"macros":[
              {"name":"x","screen":{"widthPx":1,"heightPx":2,"rotation":0},"actions":[{"type":"wait","durationMs":5}]}
            ]}
        """.trimIndent()

        assertEquals(7L, MacroTransfer.decode(content, nowMs = 7).single().createdAt)
    }

    @Test
    fun `rejects foreign json and newer versions`() {
        assertThrows(InvalidMacroFileException::class.java) { MacroTransfer.decode("{\"hello\":1}", 0) }
        assertThrows(InvalidMacroFileException::class.java) { MacroTransfer.decode("not json", 0) }
        assertThrows(InvalidMacroFileException::class.java) {
            MacroTransfer.decode("{\"format\":\"other\",\"version\":1,\"macros\":[]}", 0)
        }
        assertThrows(InvalidMacroFileException::class.java) {
            MacroTransfer.decode("{\"format\":\"ambatap-macros\",\"version\":99,\"macros\":[]}", 0)
        }
    }
}
