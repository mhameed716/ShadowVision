package com.shadow306.app.core.modeldownload

import android.content.Context
import com.shadow306.app.core.util.Checksums
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Detects a manually copied Shadow306 model folder (e.g. from USB / adb push)
 * and imports it after verifying checksums.
 *
 * This is critical for offline-first distribution: a shop can preload models
 * on an SD card and users can import without ever touching the network.
 */
class SideloadScanner(private val context: Context) {

    data class SideloadPack(
        val rootDir: File,
        val manifest: Manifest,
        val models: List<File>,
        val totalBytes: Long
    )

    suspend fun scan(): List<SideloadPack> = withContext(Dispatchers.IO) {
        val packs = mutableListOf<SideloadPack>()
        for (candidate in ModelPaths.sideloadCandidates(context)) {
            runCatching {
                val manifestFile = File(candidate, "manifest.json")
                    .takeIf { it.exists() }
                    ?: File(candidate.parentFile, "manifest.json").takeIf { it.exists() }
                    ?: return@runCatching

                val manifest = Manifest.fromJson(manifestFile.readText())
                val files = candidate.listFiles()?.filter { it.isFile && it.extension == "onnx" } ?: emptyList()
                if (files.isEmpty()) return@runCatching

                // Quick sanity: at least 50% of expected files present
                val names = files.map { it.name }.toSet()
                val matched = manifest.models.count { it.fileName in names }
                if (matched < manifest.models.size / 2) return@runCatching

                packs.add(
                    SideloadPack(
                        rootDir = candidate,
                        manifest = manifest,
                        models = files,
                        totalBytes = files.sumOf { it.length() }
                    )
                )
            }
        }
        packs
    }

    /**
     * Imports [pack] into internal noBackup storage, verifying each file.
     * Returns how many files were successfully imported.
     */
    suspend fun importPack(pack: SideloadPack, onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            var imported = 0
            val destDir = ModelPaths.modelsDir(context)
            FileIo.ensureDir(destDir)

            for ((idx, spec) in pack.manifest.models.withIndex()) {
                val src = pack.models.find { it.name == spec.fileName }
                    ?: File(pack.rootDir, spec.fileName).takeIf { it.exists() }
                    ?: continue

                // Verify before copy - avoid polluting internal storage
                val ok = runCatching { Checksums.matches(src, spec.sha256) }.getOrDefault(false)
                if (!ok) continue

                val dst = ModelPaths.finalFile(context, spec)
                // Copy in 1 MiB blocks
                src.inputStream().use { input ->
                    dst.outputStream().use { output ->
                        val buf = ByteArray(1024 * 1024)
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                        }
                    }
                }
                imported++
                onProgress(imported, pack.manifest.models.size)
            }

            // Persist manifest copy
            if (imported > 0) {
                ModelPaths.manifestFile(context).writeText(pack.manifest.toJsonString())
                ModelPaths.versionFile(context).writeText(pack.manifest.packVersion.toString())
            }
            imported
        }
}
