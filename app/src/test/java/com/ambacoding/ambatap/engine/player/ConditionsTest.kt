package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.TextMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionsTest {

    @Test
    fun `contains ignores case and surrounding spaces`() {
        assertTrue(TextMatcher.matches("  Lanjutkan ", "LANJUT", TextMatch.CONTAINS))
        assertFalse(TextMatcher.matches("Batal", "lanjut", TextMatch.CONTAINS))
    }

    @Test
    fun `exact requires the whole text`() {
        assertTrue(TextMatcher.matches("OK ", "ok", TextMatch.EXACT))
        assertFalse(TextMatcher.matches("OKE", "ok", TextMatch.EXACT))
    }

    @Test
    fun `blank queries and missing text never match`() {
        assertFalse(TextMatcher.matches("apa saja", "  ", TextMatch.CONTAINS))
        assertFalse(TextMatcher.matches(null, "ok", TextMatch.CONTAINS))
    }

    @Test
    fun `color distance is the largest channel difference and ignores alpha`() {
        assertEquals(0, ColorMatcher.distance(0xFF112233.toInt(), 0x00112233))
        assertEquals(30, ColorMatcher.distance(0xFF102030.toInt(), 0xFF1E2A12.toInt()))
        assertTrue(ColorMatcher.matches(0xFF102030.toInt(), 0xFF1E2A12.toInt(), tolerance = 30))
        assertFalse(ColorMatcher.matches(0xFF102030.toInt(), 0xFF1E2A12.toInt(), tolerance = 29))
    }

    @Test
    fun `hex round trip`() {
        assertEquals("#22C55E", ColorMatcher.toHex(0xFF22C55E.toInt()))
        assertEquals(0xFF22C55E.toInt(), ColorMatcher.parseHex("#22c55e"))
        assertEquals(0xFF22C55E.toInt(), ColorMatcher.parseHex("22C55E"))
        assertNull(ColorMatcher.parseHex("12345"))
        assertNull(ColorMatcher.parseHex("GGGGGG"))
    }
}
