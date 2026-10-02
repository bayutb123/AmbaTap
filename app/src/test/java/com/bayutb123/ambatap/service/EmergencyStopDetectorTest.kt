package com.bayutb123.ambatap.service

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyStopDetectorTest {

    @Test
    fun `two presses within the window trigger`() {
        val detector = EmergencyStopDetector(windowMs = 600)

        assertEquals(listOf(false, true), listOf(detector.onPress(1_000), detector.onPress(1_500)))
    }

    @Test
    fun `presses too far apart do not trigger`() {
        val detector = EmergencyStopDetector(windowMs = 600)

        assertEquals(listOf(false, false), listOf(detector.onPress(1_000), detector.onPress(1_700)))
    }

    @Test
    fun `a third press starts a new pair`() {
        val detector = EmergencyStopDetector(windowMs = 600)

        val results = listOf(1_000L, 1_200L, 1_400L, 1_500L).map(detector::onPress)

        assertEquals(listOf(false, true, false, true), results)
    }
}
