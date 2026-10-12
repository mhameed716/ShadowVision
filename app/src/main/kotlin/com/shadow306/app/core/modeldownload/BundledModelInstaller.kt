package com.shadow306.app.core.modeldownload

import android.content.Context
import com.shadow306.app.core.util.Checksums
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ينسخ النماذج المدمجة في الـ APK من assets/models/ إلى التخزين الداخلي
 * بحيث يعمل التطبيق 100% بدون إنترنت من أول تشغيل بدون أي تحميل.
 *
 * هذا هو الحل لطلب المستخدم: "حمل النماذج وادمجها الآن"
 *
 * كيف يعمل:
 * 1. عند أول تشغيل، يفحص إذا كانت هناك نماذج في assets/models/ (مدمجة في APK)
 * 2. إذا وجدها، ينسخها إلى noBackupFilesDir/models/
 * 3. يتحقق من SHA256
 * 4. يضع علامة setupDone = true
 *
 * المزايا:
 * - لا حاجة لتحميل بعد التثبيت
 * - يعمل في المناطق بدون إنترنت
 * - مثالي للتوزيع المباشر (خارج Play Store)
 *
 * العيوب:
 * - حجم APK كبير (~1.1GB)
 * - غير مناسب لـ Play Store (الحد 150MB)
 * - للـ Play Store استخدم النوع العادي (تحميل عند أول فتح)
 */
class BundledModelInstaller(private val context: Context) {

    data class InstallResult(
        val copied: Int,
        val skipped: Int,
        val failed: Int,
        val totalBytes: Long
    )

    suspend fun isBundled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val assets = context.assets.list("models") ?: return@withContext false
            // يجب أن يحتوي على ملفات onnx + manifest.json
            assets.any { it.endsWith(".onnx") }
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun getBundledModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            context.assets.list("models")?.filter { it.endsWith(".onnx") } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun installIfNeeded(): InstallResult? = withContext(Dispatchers.IO) {
        if (!isBundled()) return@withContext null

        val destDir = ModelPaths.modelsDir(context)
        FileIo.ensureDir(destDir)

        // تحميل manifest المدمج
        val bundledManifest = try {
            val json = context.assets.open("models/manifest.json").bufferedReader().use { it.readText() }
            Manifest.fromJson(json)
        } catch (t: Throwable) {
            return@withContext InstallResult(0, 0, 1, 0L)
        }

        var copied = 0
        var skipped = 0
        var failed = 0
        var totalBytes = 0L

        for (spec in bundledManifest.models) {
            val destFile = ModelPaths.finalFile(context, spec)

            // إذا كان الملف موجوداً وصحيحاً، تخطاه
            if (destFile.exists()) {
                val ok = runCatching { Checksums.matches(destFile, spec.sha256) }.getOrDefault(false)
                if (ok) {
                    skipped++
                    totalBytes += destFile.length()
                    continue
                } else {
                    destFile.delete()
                }
            }

            // انسخ من assets
            try {
                context.assets.open("models/${spec.fileName}").use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                // تحقق من SHA256 بعد النسخ
                val verified = runCatching { Checksums.matches(destFile, spec.sha256) }.getOrDefault(false)
                if (verified || spec.sha256.startsWith("a".repeat(10))) {
                    // إذا كان placeholder hash (aaaa...) نقبل أي ملف
                    copied++
                    totalBytes += destFile.length()
                } else {
                    destFile.delete()
                    failed++
                }
            } catch (t: Throwable) {
                failed++
                destFile.delete()
            }
        }

        // احفظ نسخة من manifest في التخزين الداخلي
        if (copied > 0 || skipped > 0) {
            try {
                ModelPaths.manifestFile(context).writeText(bundledManifest.toJsonString())
                ModelPaths.versionFile(context).writeText(bundledManifest.packVersion.toString())
            } catch (_: Throwable) {}
        }

        InstallResult(copied, skipped, failed, totalBytes)
    }

    suspend fun hasAllModels(manifest: Manifest): Boolean = withContext(Dispatchers.IO) {
        for (spec in manifest.models) {
            val file = ModelPaths.finalFile(context, spec)
            if (!file.exists()) return@withContext false
            // إذا كان placeholder hash، نقبل وجود الملف فقط
            if (spec.sha256.startsWith("a".repeat(10))) continue
            val ok = runCatching { Checksums.matches(file, spec.sha256) }.getOrDefault(false)
            if (!ok) return@withContext false
        }
        true
    }
}
