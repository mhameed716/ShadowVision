package com.shadow306.app.core.util

import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/** Small filesystem helpers shared by the downloader, templates and exports. */
object FileIo {

    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    /**
     * Turns user supplied text (a template name, a model file name from a remote
     * manifest) into a path that can never escape its directory.
     */
    fun safeFileName(raw: String, fallback: String = "file"): String {
        val cleaned = UNSAFE.replace(raw.trim(), "_").trim('.', '_')
        return when {
            cleaned.isEmpty() || cleaned == "." || cleaned == ".." -> fallback
            cleaned.length > 96 -> cleaned.take(96)
            else -> cleaned
        }
    }

    /** Creates [dir] (and parents) and returns it. */
    fun ensureDir(dir: File): File {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            throw IllegalStateException("Unable to create directory: $dir")
        }
        return dir
    }

    /**
     * Moves [source] to [target], falling back to copy + delete when both paths
     * live on different filesystems (external SD card, USB OTG, …).
     */
    fun move(source: File, target: File) {
        if (target.exists()) target.delete()
        val renamed = source.renameTo(target)
        if (renamed) return
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        if (!source.delete()) {
            // Non fatal: the destination is already complete and verified.
            source.deleteOnExit()
        }
    }

    /** Free bytes available to the caller on the filesystem backing [dir]. */
    fun freeBytes(dir: File): Long = runCatching {
        ensureDir(dir)
        StatFs(dir.absolutePath).availableBytes
    }.getOrDefault(0L)

    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    /** `image/png`, `image/jpeg`, `video/mp4` … derived from the extension. */
    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
