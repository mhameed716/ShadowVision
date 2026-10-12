package com.shadow306.app.core.modeldownload

import android.content.Context
import com.shadow306.app.core.util.Checksums
import com.shadow306.app.core.util.FileIo
import com.shadow306.app.core.util.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resumable HTTP downloader with:
 * - Range requests
 * - mirror fallback
 * - sha256 verification
 * - free-space guard
 * - air-gap guard
 * - exponential backoff
 *
 * Does NOT depend on WorkManager - it is called from ModelDownloadService
 * (foreground) and can also be unit-tested via JVM by injecting a Context mock.
 */
class ModelDownloader(
    private val context: Context,
    private val repository: ModelRepository,
    private val prefs: com.shadow306.app.core.data.prefs.AppPreferences
) {
    private var job: Job? = null

    private val retry = RetryPolicy(
        maxAttempts = 5,
        baseDelayMillis = 1_200L,
        maxDelayMillis = 20_000L
    )

    suspend fun downloadAll(manifest: Manifest) = withContext(Dispatchers.IO) {
        // Guard: air-gapped?
        if (prefs.airgappedFlow.first()) {
            repository.setError(DownloadError.Airgapped())
            return@withContext
        }

        val dir = ModelPaths.modelsDir(context)
        FileIo.ensureDir(dir)

        val totalBytes = manifest.totalBytes
        var cumulativeBytes = repository.state.value.downloadedBytes

        // Ensure per-model map seeded
        repository.refreshVerification()

        for (spec in manifest.models) {
            // Skip already installed and verified
            val finalFile = ModelPaths.finalFile(context, spec)
            if (finalFile.exists()) {
                val ok = runCatching { Checksums.matches(finalFile, spec.sha256) }.getOrDefault(false)
                if (ok) {
                    repository.updateModelState(spec.id, ModelDownloadState.Status.INSTALLED, spec.sizeBytes)
                    continue
                } else {
                    finalFile.delete()
                }
            }

            // Check space before each file
            val free = FileIo.freeBytes(dir)
            if (free < spec.sizeBytes + (50L * 1024 * 1024)) {
                repository.setError(DownloadError.NoSpace(spec.sizeBytes, free))
                return@withContext
            }

            // Wi-Fi guard
            if (repository.shouldRequireWifi()) {
                repository.setError(DownloadError.Network("Waiting for Wi-Fi"))
                // Don't fail hard - service will observe network changes
                // For now pause and return
                repository.setPaused(true)
                return@withContext
            }

            repository.updateModelState(spec.id, ModelDownloadState.Status.DOWNLOADING, 0L)
            repository.updateProgress(spec.id, cumulativeBytes, totalBytes, 0L, -1L)

            try {
                downloadSingle(spec, totalBytes, cumulativeBytes)
                // Verify
                repository.updateModelState(spec.id, ModelDownloadState.Status.VERIFYING)
                val verified = Checksums.matches(finalFile, spec.sha256)
                if (!verified) {
                    finalFile.delete()
                    throw IllegalStateException("checksum mismatch for ${spec.fileName}")
                }
                repository.updateModelState(spec.id, ModelDownloadState.Status.INSTALLED, spec.sizeBytes)
                cumulativeBytes += spec.sizeBytes
                repository.updateProgress(null, cumulativeBytes, totalBytes, 0L, -1L)

            } catch (e: CancellationException) {
                repository.setPaused(true)
                repository.updateModelState(spec.id, ModelDownloadState.Status.PAUSED)
                throw e
            } catch (t: Throwable) {
                val err = when (t) {
                    is java.io.IOException -> DownloadError.Network(t.message ?: "network error")
                    else -> DownloadError.Io(t.message ?: "io error")
                }
                repository.updateModelState(spec.id, ModelDownloadState.Status.FAILED, error = err)
                repository.setError(err)
                // Continue? For required models we stop, for optional we continue
                if (spec.required) return@withContext
            }
        }

        // Persist manifest copy
        ModelPaths.manifestFile(context).writeText(manifest.toJsonString())
        ModelPaths.versionFile(context).writeText(manifest.packVersion.toString())
        repository.setComplete(true)
        repository.refreshVerification()
    }

    private suspend fun downloadSingle(spec: ModelSpec, totalBytes: Long, cumulativeOffset: Long) =
        withContext(Dispatchers.IO) {
            val urls = listOf(spec.url) + spec.mirrors
            var lastException: Throwable? = null

            for (url in urls) {
                try {
                    retry.run { attempt ->
                        performHttpDownload(spec, url, totalBytes, cumulativeOffset)
                    }
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    lastException = t
                    // Try next mirror
                    continue
                }
            }
            throw lastException ?: java.io.IOException("All mirrors failed for ${spec.id}")
        }

    private suspend fun performHttpDownload(
        spec: ModelSpec,
        urlString: String,
        totalPackBytes: Long,
        cumulativeOffset: Long
    ) = withContext(Dispatchers.IO) {
        val tmpFile = ModelPaths.tempFile(context, spec)
        val finalFile = ModelPaths.finalFile(context, spec)

        var existing = if (tmpFile.exists()) tmpFile.length() else 0L
        // If tmp is larger than expected, restart
        if (existing > spec.sizeBytes && spec.sizeBytes > 0) {
            tmpFile.delete()
            existing = 0L
        }

        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Shadow306/${spec.version} (Android)")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }

        try {
            conn.connect()
            val code = conn.responseCode
            val isPartial = code == HttpURLConnection.HTTP_PARTIAL
            val isOk = code == HttpURLConnection.HTTP_OK

            if (!isOk && !isPartial) {
                throw java.io.IOException("HTTP $code for ${spec.id}")
            }

            val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val totalForThisFile = if (isPartial) existing + contentLength else contentLength.takeIf { it > 0 } ?: spec.sizeBytes

            var downloadedForThisFile = existing
            var lastTick = System.currentTimeMillis()
            var bytesSinceTick = 0L
            var speedBps = 0L

            val input: InputStream = conn.inputStream
            FileOutputStream(tmpFile, existing > 0 && isPartial).use { out ->
                val buf = ByteArray(256 * 1024)
                while (isActive) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    downloadedForThisFile += read
                    bytesSinceTick += read

                    val now = System.currentTimeMillis()
                    if (now - lastTick >= 500) {
                        val elapsed = (now - lastTick).coerceAtLeast(1)
                        speedBps = (bytesSinceTick * 1000) / elapsed
                        val overallDownloaded = cumulativeOffset + downloadedForThisFile
                        val remainingBytes = totalPackBytes - overallDownloaded
                        val eta = if (speedBps > 0) (remainingBytes * 1000) / speedBps else -1L

                        repository.updateProgress(spec.id, overallDownloaded, totalPackBytes, speedBps, eta)
                        repository.updateModelState(spec.id, ModelDownloadState.Status.DOWNLOADING, downloadedForThisFile)

                        lastTick = now
                        bytesSinceTick = 0L
                    }
                }
                out.fd.sync()
            }

            // Move tmp -> final
            FileIo.move(tmpFile, finalFile)

        } finally {
            conn.disconnect()
        }
    }
}
