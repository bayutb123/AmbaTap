package com.ambacoding.ambatap.engine.recorder.getevent

import com.ambacoding.ambatap.engine.player.PxPoint
import com.ambacoding.ambatap.engine.player.ScreenSize
import com.ambacoding.ambatap.engine.recorder.TouchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeteventTest {

    private val deviceInfo = """
        add device 1: /dev/input/event4
          name:     "gpio-keys"
          events:
            KEY (0001): KEY_VOLUMEDOWN        KEY_VOLUMEUP
          input props:
            <none>
        add device 2: /dev/input/event2
          name:     "fts_ts"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        ABS_MT_TOUCH_MAJOR    : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_X     : value 0, min 0, max 1079, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y     : value 0, min 0, max 2399, fuzz 0, flat 0, resolution 0
                        ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
    """.trimIndent()

    private val device = TouchDevice("/dev/input/event2", "fts_ts", 0, 1079, 0, 2399, direct = true)

    @Test
    fun `finds the touchscreen and its raw range`() {
        val devices = TouchDeviceParser.parse(deviceInfo)

        assertEquals(listOf(device), devices)
        assertEquals(device, TouchDeviceParser.pickTouchscreen(devices))
    }

    @Test
    fun `reads unlabeled axis codes too`() {
        val unlabeled = """
            add device 1: /dev/input/event1
              name:     "touch"
              events:
                ABS (0003): 0035  : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
        """.trimIndent()

        assertEquals(
            listOf(TouchDevice("/dev/input/event1", "touch", 0, 4095, 0, 4095, direct = false)),
            TouchDeviceParser.parse(unlabeled),
        )
    }

    private fun ev(time: String, type: String, code: String, value: String) =
        "[ $time] /dev/input/event2: $type $code $value"

    @Test
    fun `decodes protocol B slots into frames`() {
        val decoder = MultiTouchDecoder()
        val lines = listOf(
            ev("100.000000", "EV_ABS", "ABS_MT_TRACKING_ID", "0000002a"),
            ev("100.000000", "EV_ABS", "ABS_MT_POSITION_X", "00000064"),
            ev("100.000000", "EV_ABS", "ABS_MT_POSITION_Y", "000000c8"),
            ev("100.000000", "EV_KEY", "BTN_TOUCH", "DOWN"),
            ev("100.000000", "EV_SYN", "SYN_REPORT", "00000000"),
            ev("100.016500", "EV_ABS", "ABS_MT_SLOT", "00000001"),
            ev("100.016500", "EV_ABS", "ABS_MT_TRACKING_ID", "0000002b"),
            ev("100.016500", "EV_ABS", "ABS_MT_POSITION_X", "00000190"),
            ev("100.016500", "EV_ABS", "ABS_MT_POSITION_Y", "000000c8"),
            ev("100.016500", "EV_SYN", "SYN_REPORT", "00000000"),
            ev("100.033000", "EV_ABS", "ABS_MT_SLOT", "00000000"),
            ev("100.033000", "EV_ABS", "ABS_MT_TRACKING_ID", "ffffffff"),
            ev("100.033000", "EV_SYN", "SYN_REPORT", "00000000"),
        )

        val frames = lines.mapNotNull(decoder::feed)

        assertEquals(
            listOf(
                TouchFrame(100_000, listOf(Contact(42, 100, 200))),
                TouchFrame(100_016, listOf(Contact(42, 100, 200), Contact(43, 400, 200))),
                TouchFrame(100_033, listOf(Contact(43, 400, 200))),
            ),
            frames,
        )
    }

    @Test
    fun `ignores noise lines`() {
        val decoder = MultiTouchDecoder()
        assertNull(decoder.feed("add device 1: /dev/input/event2"))
        assertNull(decoder.feed("could not get driver version for /dev/input/mice, Not a typewriter"))
    }

    @Test
    fun `maps raw coordinates for every rotation`() {
        val mapper = RawTouchMapper(device)
        val portrait = ScreenSize(1080, 2400)
        val landscape = ScreenSize(2400, 1080)

        assertEquals(PxPoint(100f, 200f), mapper.map(100, 200, portrait, rotation = 0))
        // Diputar 90°: sumbu Y mentah menjadi X layar, X mentah terbalik menjadi Y.
        assertEquals(PxPoint(200f, 980f), mapper.map(100, 200, landscape, rotation = 1))
        assertEquals(PxPoint(980f, 2200f), mapper.map(100, 200, portrait, rotation = 2))
        assertEquals(PxPoint(2200f, 100f), mapper.map(100, 200, landscape, rotation = 3))
    }

    @Test
    fun `scales raw ranges that differ from the screen`() {
        val mapper = RawTouchMapper(TouchDevice("/dev/input/event1", "touch", 0, 4095, 0, 4095, direct = true))

        assertEquals(PxPoint(540f, 1200f), mapper.map(2048, 2048, ScreenSize(1080, 2400), rotation = 0))
    }

    @Test
    fun `builds one session per touch with lift samples`() {
        val builder = TouchSessionBuilder { PxPoint(it.rawX.toFloat(), it.rawY.toFloat()) }

        assertNull(builder.onFrame(TouchFrame(0, listOf(Contact(1, 10, 10)))))
        assertNull(builder.onFrame(TouchFrame(10, listOf(Contact(1, 20, 10), Contact(2, 50, 50)))))
        assertNull(builder.onFrame(TouchFrame(20, listOf(Contact(2, 60, 50)))))
        val session = builder.onFrame(TouchFrame(30, emptyList()))

        assertEquals(
            listOf(
                listOf(TouchSample(10f, 10f, 0), TouchSample(20f, 10f, 10), TouchSample(20f, 10f, 20)),
                listOf(TouchSample(50f, 50f, 10), TouchSample(60f, 50f, 20), TouchSample(60f, 50f, 30)),
            ),
            session,
        )
        // Frame kosong berikutnya tidak membuat sesi baru.
        assertNull(builder.onFrame(TouchFrame(40, emptyList())))
    }
}
