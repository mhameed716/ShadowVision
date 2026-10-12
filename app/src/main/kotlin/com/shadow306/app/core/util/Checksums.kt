package com.shadow306.app.core.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Streaming SHA-256 helpers used to verify downloaded model files.
 *
 * Files are hashed in 1 MiB blocks so a 400 MB model never lands in memory.
 */
object Checksums {

    private const val BLOCK = 1024 * 1024
    private val HEX = CharArray(128).apply {
        for (i in 0..9) this[i] = ('0'.code + i).toChar()
        for (i in 10..15) this[i] = ('a'.code + i - 10).toChar()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BLOCK)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    fun sha256(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** True when [expected] (hex, any case) matches the hash of [file]. */
    fun matches(file: File, expected: String): Boolean =
        expected.length == 64 && sha256(file).equals(expected.lowercase(), ignoreCase = true)

    fun isSha256(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
