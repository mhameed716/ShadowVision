package com.shadow306.app.core.modeldownload

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.shadow306.app.core.data.prefs.AppPreferences
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for model pack status.
 *
 * Responsibilities:
 * - load bundled manifest from assets/models/manifest.json
 * - load installed manifest from noBackupFilesDir/models/.manifest.json
 * - expose [PackDownloadState] as StateFlow
 * - check network / airgap / storage preconditions
 */
class ModelRepository(
    private val context: Context,
    private val prefs: AppPreferences,
    private val verifier: ModelVerifier
) {
    private val _state = MutableStateFlow(PackDownloadState())
    val state: StateFlow<PackDownloadState> = _state.asStateFlow()

    private var bundledManifest: Manifest? = null
    private var installedManifest: Manifest? = null

    suspend fun loadBundledManifest(): Manifest = withContext(Dispatchers.IO) {
        bundledManifest?.let { return@withContext it }
        val json = context.assets.open("models/manifest.json").bufferedReader().use { it.readText() }
        val manifest = Manifest.fromJson(json)
        bundledManifest = manifest
        // seed totalBytes into state
        _state.value = _state.value.copy(
            totalBytes = manifest.totalBytes,
            totalCount = manifest.models.size
        )
        manifest
    }

    suspend fun loadInstalledManifest(): Manifest? = withContext(Dispatchers.IO) {
        installedManifest?.let { return@withContext it }
        val file = ModelPaths.manifestFile(context)
        if (!file.exists()) return@withContext null
        runCatching {
            val m = Manifest.fromJson(file.readText())
            installedManifest = m
            m
        }.getOrNull()
    }

    suspend fun refreshVerification() = withContext(Dispatchers.IO) {
        val manifest = loadBundledManifest()
        val result = verifier.verify(manifest)
        val perModel = manifest.models.associate { spec ->
            val status = when {
                result.ok.any { it.id == spec.id } -> ModelDownloadState.Status.INSTALLED
                result.missing.any { it.id == spec.id } -> ModelDownloadState.Status.QUEUED
                result.corrupted.any { it.id == spec.id } -> ModelDownloadState.Status.FAILED
                else -> ModelDownloadState.Status.QUEUED
            }
            spec.id to ModelDownloadState(spec, status)
        }

        val installedBytes = result.ok.sumOf { it.sizeBytes }
        _state.value = _state.value.copy(
            totalBytes = manifest.totalBytes,
            downloadedBytes = installedBytes,
            installedCount = result.ok.size,
            totalCount = manifest.models.size,
            isComplete = result.allOk,
            perModel = perModel
        )
    }

    /** Whether we are allowed to open a socket right now. */
    suspend fun canUseNetwork(): Boolean {
        val airgapped = prefs.airgappedFlow.first()
        if (airgapped) return false
        if (!hasNetworkPermission()) return false
        return isNetworkAvailable()
    }

    suspend fun isWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    suspend fun shouldRequireWifi(): Boolean {
        val allowMetered = prefs.allowMeteredFlow.first()
        if (allowMetered) return false
        return !isWifi()
    }

    suspend fun hasEnoughSpace(manifest: Manifest? = null): Pair<Boolean, Pair<Long, Long>> =
        withContext(Dispatchers.IO) {
            val m = manifest ?: loadBundledManifest()
            val dir = ModelPaths.modelsDir(context)
            val free = FileIo.freeBytes(dir)
            val needed = m.totalBytes - (state.value.downloadedBytes)
            val ok = free > needed + (100L * 1024 * 1024) // 100 MB safety margin
            ok to (needed to free)
        }

    fun updateProgress(
        currentModelId: String?,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        etaMillis: Long
    ) {
        _state.value = _state.value.copy(
            currentModelId = currentModelId,
            currentModelName = currentModelId?.let { id -> _state.value.perModel[id]?.spec?.displayName },
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes.coerceAtLeast(_state.value.totalBytes),
            speedBps = speedBps,
            etaMillis = etaMillis
        )
    }

    fun updateModelState(id: String, status: ModelDownloadState.Status, downloaded: Long = 0L, error: DownloadError? = null) {
        val current = _state.value
        val existing = current.perModel[id]
        if (existing == null) return
        val updated = existing.copy(status = status, downloadedBytes = downloaded, error = error)
        _state.value = current.copy(perModel = current.perModel + (id to updated))
    }

    fun setError(error: DownloadError?) {
        _state.value = _state.value.copy(error = error)
    }

    fun setPaused(paused: Boolean) {
        _state.value = _state.value.copy(isPaused = paused)
    }

    fun setComplete(complete: Boolean) {
        _state.value = _state.value.copy(isComplete = complete, currentModelId = null, error = null)
    }

    private fun hasNetworkPermission(): Boolean {
        return context.checkCallingOrSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
