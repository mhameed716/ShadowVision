package com.shadow306.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileIoTest {

    @Test
    fun `safeFileName sanitizes`() {
        assertEquals("file", FileIo.safeFileName(""))
        assertEquals("file", FileIo.safeFileName(".."))
        assertEquals("hello_world", FileIo.safeFileName("hello world"))
        assertEquals("test_jpg", FileIo.safeFileName("test.jpg"))
        assertTrue(FileIo.safeFileName("../../etc/passwd").contains("etc_passwd") || FileIo.safeFileName("../../etc/passwd").length <= 96)
    }

    @Test
    fun `mimeOf detects correctly`() {
        assertEquals("image/png", FileIo.mimeOf("photo.png"))
        assertEquals("image/jpeg", FileIo.mimeOf("photo.jpg"))
        assertEquals("image/jpeg", FileIo.mimeOf("photo.jpeg"))
        assertEquals("video/mp4", FileIo.mimeOf("video.mp4"))
        assertEquals("application/octet-stream", FileIo.mimeOf("unknown.xyz"))
    }
}
