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
            isReady = state.isReady,
            installedCount = state.installedCount,
            totalCount = state.totalCount,
            requiredFeatures = featureMap
        )
    }
}

class TryOnUseCase(
    private val engine: TryOnEngine
) {
    suspend fun invoke(person: Bitmap, garment: Bitmap, mode: EngineMode = EngineMode.AUTO): Bitmap {
        return engine.tryOn(person, garment, mode)
    }
}

class FaceSwapUseCase(
    private val engine: FaceEngine
) {
    suspend fun invoke(source: Bitmap, target: Bitmap, mode: EngineMode = EngineMode.AUTO): Bitmap {
        return engine.swap(source, target, mode)
    }
}

class RemovalUseCase(
    private val engine: RemovalEngine
) {
    suspend fun invoke(image: Bitmap, mode: EngineMode = EngineMode.AUTO): Bitmap {
        return engine.remove(image, mode)
    }
}

class VideoGenUseCase(
    private val engine: VideoEngine
) {
    suspend fun invoke(image: Bitmap, prompt: String, mode: EngineMode = EngineMode.AUTO): String {
        return engine.generate(image, prompt, mode)
    }
}

class TemplateUseCase(
    private val store: TemplateStore
) {
    fun getTemplates(): Flow<List<com.shadow306.app.core.data.db.TemplateEntity>> = store.templatesFlow
    suspend fun addTemplate(name: String, uri: Uri, context: Context) = store.addTemplate(name, uri, context)
    suspend fun deleteTemplate(id: Long) = store.deleteTemplate(id)
}
