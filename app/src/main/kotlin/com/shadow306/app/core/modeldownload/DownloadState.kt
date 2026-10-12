package com.shadow306.app.core.modeldownload

/**
 * Aggregate download state exposed to UI via StateFlow.
 * Immutable - new instances are emitted on every progress tick.
 */
data class PackDownloadState(
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val installedCount: Int = 0,
    val totalCount: Int = 0,
    val currentModelId: String? = null,
    val currentModelName: String? = null,
    val speedBps: Long = 0L,
    val etaMillis: Long = -1L,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val error: DownloadError? = null,
    val perModel: Map<String, ModelDownloadState> = emptyMap()
) {
    val percent: Int get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    val isIdle: Boolean get() = currentModelId == null && !isComplete && error == null
}

data class ModelDownloadState(
    val spec: ModelSpec,
    val status: Status,
    val downloadedBytes: Long = 0L,
    val error: DownloadError? = null
) {
    enum class Status {
        QUEUED,
        DOWNLOADING,
        VERIFYING,
        INSTALLED,
        FAILED,
        PAUSED
    }

    val percent: Int get() = if (spec.sizeBytes <= 0) 0 else ((downloadedBytes * 100) / spec.sizeBytes).toInt().coerceIn(0, 100)
}

sealed class DownloadError {
    data class Network(val message: String, val httpCode: Int? = null) : DownloadError()
    data class ChecksumMismatch(val expected: String, val actual: String) : DownloadError()
    data class NoSpace(val needed: Long, val free: Long) : DownloadError()
    data class Airgapped(val message: String = "Air-gapped lock is on") : DownloadError()
    data class ManifestInvalid(val message: String) : DownloadError()
    data class Io(val message: String) : DownloadError()
}
