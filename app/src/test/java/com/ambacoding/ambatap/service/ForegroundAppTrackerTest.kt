package com.ambacoding.ambatap.service

import com.ambacoding.ambatap.service.trigger.ForegroundAppTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppTrackerTest {

    @Test
    fun `reports each switch to a different app once`() {
        val tracker = ForegroundAppTracker()
        assertEquals("game", tracker.onForeground("game"))
        assertNull(tracker.onForeground("game"))
        assertEquals("chat", tracker.onForeground("chat"))
        assertEquals("game", tracker.onForeground("game"))
    }

    @Test
    fun `returning from an ignored app counts as opening again`() {
        val tracker = ForegroundAppTracker(ignored = setOf("ambatap"))
        assertEquals("game", tracker.onForeground("game"))
        assertNull(tracker.onForeground("ambatap"))
        assertEquals("game", tracker.onForeground("game"))
    }

    @Test
    fun `blank package names are ignored`() {
        val tracker = ForegroundAppTracker()
        assertEquals("game", tracker.onForeground("game"))
        assertNull(tracker.onForeground(""))
        assertNull(tracker.onForeground("game"))
    }
}
