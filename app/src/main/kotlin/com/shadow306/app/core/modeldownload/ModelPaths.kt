package com.shadow306.app.core.modeldownload

import android.content.Context
import com.shadow306.app.core.util.FileIo
import java.io.File

/**
 * All on-device locations for models. Models live in noBackupFilesDir so they
 * are never auto-backed up to Google Drive.
 *
 * Layout:
 *   noBackupFilesDir/models/
 *     tryon_warp_fp16.onnx
 *     tryon_warp_fp16.onnx.tmp          <- partial download
 *     tryon_warp_fp16.onnx.sha256       <- cached checksum (optional)
 *     .manifest.json                    <- installed manifest copy
 *     .version                          <- installed pack version
 */
object ModelPaths {

    fun modelsDir(context: Context): File =
        FileIo.ensureDir(File(context.noBackupFilesDir, "models"))

    fun tempFile(context: Context, spec: ModelSpec): File =
        File(modelsDir(context), "${spec.fileName}.tmp")

    fun finalFile(context: Context, spec: ModelSpec): File =
        File(modelsDir(context), spec.fileName)

    fun manifestFile(context: Context): File =
        File(modelsDir(context), ".manifest.json")

    fun versionFile(context: Context): File =
        File(modelsDir(context), ".version")

    /** Scans for side-loaded models in Downloads/Shadow306 and external files dir. */
    fun sideloadCandidates(context: Context): List<File> {
        val list = mutableListOf<File>()
        // App external files (user can adb push here)
        context.getExternalFilesDir(null)?.let { ext ->
            list.add(File(ext, "Shadow306/models"))
            list.add(File(ext, "models"))
        }
        // Public Download folder candidate - only if we can resolve it
        runCatching {
            val downloads = File("/storage/emulated/0/Download/Shadow306/models")
            if (downloads.exists()) list.add(downloads)
        }
        return list.filter { it.exists() && it.isDirectory }
    }

    /** Total bytes used by installed models. */
    fun usedBytes(context: Context): Long =
        FileIo.dirSize(modelsDir(context))
}
