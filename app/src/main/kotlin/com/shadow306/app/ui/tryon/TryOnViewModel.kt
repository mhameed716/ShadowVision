package com.shadow306.app.ui.tryon

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow306.app.core.engine.body.TryOnEngine
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.export.ExportManager
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TryOnViewModel(context: Context) : ViewModel() {

    private val engine = ServiceLocator.provideTryOnEngine(context)
    private val prefs = ServiceLocator.providePrefs(context)
    private val exportManager = ServiceLocator.provideExportManager(context)
    private val repository = ServiceLocator.provideModelRepository(context)

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    val packState = repository.state

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                fit = prefs.tryOnFitFlow.first(),
                matchShadow = prefs.tryOnShadowFlow.first()
            )
        }
    }

    fun setPersonBitmap(bitmap: Bitmap?) {
        _uiState.value = _uiState.value.copy(personBitmap = bitmap)
    }

    fun setGarmentBitmap(bitmap: Bitmap?) {
        _uiState.value = _uiState.value.copy(garmentBitmap = bitmap)
    }

    fun setFit(fit: Int) {
        _uiState.value = _uiState.value.copy(fit = fit)
        viewModelScope.launch { prefs.setTryOnFit(fit) }
    }

    fun setMatchShadow(match: Boolean) {
        _uiState.value = _uiState.value.copy(matchShadow = match)
        viewModelScope.launch { prefs.setTryOnShadow(match) }
    }

    fun generate() {
        val person = _uiState.value.personBitmap ?: return
        val garment = _uiState.value.garmentBitmap ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)

            try {
                val isFallback = packState.value.perModel.values.any {
                    it.spec.features.contains(com.shadow306.app.core.modeldownload.ModelSpec.Feature.TRY_ON) &&
                            it.status != com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED
                }
                val mode = if (isFallback) EngineMode.FALLBACK else EngineMode.AUTO

                val result = engine.tryOn(
                    person, garment,
                    TryOnEngine.Params(_uiState.value.fit, _uiState.value.matchShadow),
                    mode
                )

                _uiState.value = _uiState.value.copy(
                    resultBitmap = result.bitmap,
                    usedFallback = result.usedFallback,
                    isProcessing = false,
                    inferenceMs = result.inferenceMs
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = t.message ?: "Unknown error"
                )
            }
        }
    }

    fun saveResult(onResult: (ExportManager.ExportResult) -> Unit) {
        val bitmap = _uiState.value.resultBitmap ?: return
        viewModelScope.launch {
            val result = exportManager.saveImageToGallery(bitmap, "tryon_${System.currentTimeMillis()}")
            onResult(result)
        }
    }

    data class TryOnUiState(
        val personBitmap: Bitmap? = null,
        val garmentBitmap: Bitmap? = null,
        val fit: Int = 50,
        val matchShadow: Boolean = true,
        val resultBitmap: Bitmap? = null,
        val usedFallback: Boolean = false,
        val isProcessing: Boolean = false,
        val inferenceMs: Long = 0L,
        val error: String? = null
    )
}
