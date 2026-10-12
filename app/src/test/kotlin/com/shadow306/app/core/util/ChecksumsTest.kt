package com.shadow306.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChecksumsTest {

    @Test
    fun `sha256 of known string`() {
        val input = "hello".toByteArray()
        val hash = Checksums.sha256(input)
        // Known SHA256 of "hello"
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
    }

    @Test
    fun `isSha256 validates format`() {
        assertTrue(Checksums.isSha256("a".repeat(64)))
        assertTrue(Checksums.isSha256("A".repeat(64)))
        assertTrue(Checksums.isSha256("0123456789abcdef".repeat(4)))
        assertFalse(Checksums.isSha256("abc"))
        assertFalse(Checksums.isSha256("g".repeat(64))) // g not hex
        assertFalse(Checksums.isSha256(""))
    }

    @Test
    fun `sha256 file`() {
        val temp = File.createTempFile("test", ".txt")
        temp.writeText("hello")
        val hash = Checksums.sha256(temp)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
        temp.delete()
    }
}
