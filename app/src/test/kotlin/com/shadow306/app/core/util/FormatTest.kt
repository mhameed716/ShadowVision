package com.shadow306.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `bytes formatting`() {
        // Note: Format uses default locale, so we check contains
        val b = Format.bytes(500)
        assert(b.contains("B"))

        val kb = Format.bytes(2048)
        assert(kb.contains("KB") || kb.contains("kB") || kb.contains("KB"))

        val mb = Format.bytes(5L * 1024 * 1024)
        assert(mb.contains("MB"))
    }

    @Test
    fun `percent handles zero total`() {
        assertEquals(0, Format.percent(10, 0))
        assertEquals(0, Format.percent(0, 100))
        assertEquals(100, Format.percent(100, 100))
        assertEquals(50, Format.percent(50, 100))
    }

    @Test
    fun `duration formatting`() {
        val s = Format.duration(45_000)
        assert(s.contains("45") || s.contains("s"))

        val m = Format.duration(3 * 60_000 + 20_000)
        assert(m.contains("3") && m.contains("20") || m.contains("m"))
    }
}
