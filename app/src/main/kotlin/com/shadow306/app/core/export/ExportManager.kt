package com.shadow306.app.core.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shadow306.app.core.engine.imaging.BitmapUtils
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles saving results to private app storage and optionally to public
 * MediaStore (gallery) so user can see results outside the app.
 *
 * Private storage is always used first (fast, no permissions). Public export
 * is opt-in via "Save to gallery" button.
 */
class ExportManager(private val context: Context) {

    sealed class ExportResult {
        data class Success(val file: File, val publicUri: String? = null) : ExportResult()
        data class Failure(val error: Throwable) : ExportResult()
    }

    suspend fun saveImagePrivate(bitmap: Bitmap, prefix: String = "shadow"): File = withContext(Dispatchers.IO) {
        val dir = FileIo.ensureDir(File(context.filesDir, "exports"))
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        BitmapUtils.saveJpeg(bitmap, file, 92)
    }

    suspend fun saveImageToGallery(bitmap: Bitmap, displayName: String): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shadow306")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext ExportResult.Failure(IllegalStateException("Failed to create MediaStore entry"))

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                // Also save private copy
                val privateFile = saveImagePrivate(bitmap, displayName)
                ExportResult.Success(privateFile, uri.toString())
            } else {
                // Legacy path
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val shadowDir = FileIo.ensureDir(File(publicDir, "Shadow306"))
                val file = File(shadowDir, "$displayName.jpg")
                BitmapUtils.saveJpeg(bitmap, file, 95)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                val privateFile = saveImagePrivate(bitmap, displayName)
                ExportResult.Success(privateFile, file.absolutePath)
            }
        } catch (t: Throwable) {
            ExportResult.Failure(t)
        }
    }

    suspend fun saveVideoPrivate(source: File, prefix: String = "video"): File = withContext(Dispatchers.IO) {
        val dir = FileIo.ensureDir(File(context.filesDir, "exports"))
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.mp4")
        source.copyTo(dest, overwrite = true)
        dest
    }

    fun listExports(): List<File> {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun clearExports() {
        val dir = File(context.filesDir, "exports")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}
