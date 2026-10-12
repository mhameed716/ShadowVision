package com.shadow306.app.core.modeldownload

import android.content.Context
import com.shadow306.app.core.util.Checksums
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Verifies installed models against the manifest (size + sha256).
 * Used on first launch, on settings "Verify models", and after any download.
 */
class ModelVerifier(private val context: Context) {

    data class VerificationResult(
        val ok: List<ModelSpec>,
        val missing: List<ModelSpec>,
        val corrupted: List<ModelSpec>,
        val extraFiles: List<File>
    ) {
        val allOk: Boolean get() = missing.isEmpty() && corrupted.isEmpty()
        val installedCount: Int get() = ok.size
    }

    suspend fun verify(manifest: Manifest): VerificationResult = withContext(Dispatchers.IO) {
        val dir = ModelPaths.modelsDir(context)
        val existingFiles = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".tmp") } ?: emptyList()
        val byName = existingFiles.associateBy { it.name }

        val ok = mutableListOf<ModelSpec>()
        val missing = mutableListOf<ModelSpec>()
        val corrupted = mutableListOf<ModelSpec>()

        for (spec in manifest.models) {
            val file = ModelPaths.finalFile(context, spec)
            if (!file.exists()) {
                missing.add(spec)
                continue
            }
            // Fast size check first
            if (spec.sizeBytes > 0 && file.length() != spec.sizeBytes) {
                // Allow small delta for quantized repacks? No - strict.
                // But we still hash to be sure.
            }
            val matches = runCatching { Checksums.matches(file, spec.sha256) }.getOrDefault(false)
            if (matches) ok.add(spec) else corrupted.add(spec)
        }

        val expectedNames = manifest.models.map { it.fileName }.toSet()
        val extra = existingFiles.filter { it.name !in expectedNames }

        VerificationResult(ok, missing, corrupted, extra)
    }

    suspend fun verifySingle(spec: ModelSpec): Boolean = withContext(Dispatchers.IO) {
        val file = ModelPaths.finalFile(context, spec)
        if (!file.exists()) return@withContext false
        runCatching { Checksums.matches(file, spec.sha256) }.getOrDefault(false)
    }

    suspend fun deleteCorrupted(manifest: Manifest) = withContext(Dispatchers.IO) {
        val res = verify(manifest)
        res.corrupted.forEach { spec ->
            ModelPaths.finalFile(context, spec).delete()
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val dir = ModelPaths.modelsDir(context)
        dir.listFiles()?.forEach { file ->
            if (!file.name.startsWith(".")) FileIo.deleteRecursively(file)
        }
        ModelPaths.manifestFile(context).delete()
        ModelPaths.versionFile(context).delete()
    }
}
