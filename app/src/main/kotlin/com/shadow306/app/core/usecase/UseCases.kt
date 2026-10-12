package com.shadow306.app.core.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.shadow306.app.core.data.db.TemplateStore
import com.shadow306.app.core.data.prefs.AppPreferences
import com.shadow306.app.core.engine.body.RemovalEngine
import com.shadow306.app.core.engine.body.TryOnEngine
import com.shadow306.app.core.engine.face.FaceEngine
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.engine.video.VideoEngine
import com.shadow306.app.core.modeldownload.ModelRepository
import com.shadow306.app.core.modeldownload.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Use cases - thin orchestration layer between ViewModels and engines.
 * Keeps ViewModels small and testable.
 */

class GetEngineStatusUseCase(
    private val repository: ModelRepository,
    private val prefs: AppPreferences
) {
    data class EngineStatus(
        val mode: EngineMode,
        val isReady: Boolean,
        val installedCount: Int,
        val totalCount: Int,
        val requiredFeatures: Map<ModelSpec.Feature, Boolean>
    )

    suspend fun invoke(): EngineStatus {
        val state = repository.state.value
        val modeStr = prefs.engineModeFlow.first()
        val mode = when (modeStr) {
            "onnx" -> EngineMode.ONNX
            "fallback" -> EngineMode.FALLBACK
            else -> EngineMode.AUTO
        }

        val manifest = runCatching { repository.loadBundledManifest() }.getOrNull()
        val featureMap = if (manifest != null) {
            ModelSpec.Feature.values().associateWith { feature ->
                val needed = manifest.forFeature(feature)
                needed.all { spec -> state.perModel[spec.id]?.status == com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED }
            }
        } else emptyMap()

        return EngineStatus(
            mode = mode,
            isReady = state.isComplete,
            installedCount = state.installedCount,
            totalCount = state.totalCount,
            requiredFeatures = featureMap
        )
    }
}

class TryOnUseCase(
    private val engine: TryOnEngine
) {
    suspend fun invoke(
        person: Bitmap,
        garment: Bitmap,
        fit: Int,
        matchShadow: Boolean,
        mode: EngineMode
    ): TryOnEngine.Result {
        return engine.tryOn(
            person, garment,
            TryOnEngine.Params(fitTightness = fit, matchShadow = matchShadow),
            mode
        )
    }
}

class FaceSwapUseCase(
    private val engine: FaceEngine
) {
    suspend fun invoke(
        target: Bitmap,
        source: Bitmap,
        colorMatch: Boolean,
        feather: Int,
        mode: EngineMode
    ): FaceEngine.Result {
        return engine.swap(
            target, source,
            FaceEngine.Params(colorMatch = colorMatch, feather = feather),
            mode
        )
    }
}

class VideoGenUseCase(
    private val engine: VideoEngine
) {
    fun invoke(
        source: Bitmap,
        prompt: String,
        durationSec: Int,
        fps: Int,
        motion: Int,
        mode: EngineMode
    ): Flow<VideoEngine.Progress> {
        return engine.generate(
            source,
            VideoEngine.Params(
                prompt = prompt,
                durationSec = durationSec,
                fps = fps,
                motionStrength = motion
            ),
            mode
        )
    }
}

class RemovalUseCase(
    private val engine: RemovalEngine
) {
    suspend fun invoke(
        image: Bitmap,
        mask: Bitmap?,
        sensitivity: Int,
        skinMatch: Boolean,
        mode: EngineMode
    ): RemovalEngine.Result {
        return engine.remove(
            image, mask,
            RemovalEngine.Params(sensitivity = sensitivity, skinMatch = skinMatch),
            mode
        )
    }
}

class TemplateUseCase(
    private val store: TemplateStore
) {
    suspend fun load() = store.load()
    fun flow() = store.templatesFlow
    suspend fun add(uri: Uri, context: Context, name: String, category: String): Result<com.shadow306.app.core.data.db.TemplateItem> {
        // Copy uri to temp file first
        val temp = java.io.File(context.cacheDir, "import_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { out -> input.copyTo(out) }
        }
        val cat = com.shadow306.app.core.data.db.TemplateItem.categoryFromString(category)
        return store.addFromFile(temp, name, cat)
    }
    suspend fun delete(id: String) = store.delete(id)
}
