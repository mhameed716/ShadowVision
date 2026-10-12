package com.shadow306.app.core.engine.body

import android.content.Context
import android.graphics.Bitmap
import com.shadow306.app.core.engine.imaging.BitmapUtils
import com.shadow306.app.core.engine.imaging.InpaintingUtils
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.engine.onnx.OnnxEnv
import com.shadow306.app.core.engine.onnx.OnnxSessionFactory
import com.shadow306.app.core.modeldownload.ModelSpec
import com.shadow306.app.core.modeldownload.ModelPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cloth parsing (garment segmentation) engine.
 *
 * - When ONNX cloth parsing model is present: runs segmentation to get per-pixel
 *   garment mask (shirt, pants, dress, etc.)
 * - Fallback: color heuristic + edge detection in [InpaintingUtils]
 */
class ClothParser(private val context: Context) {

    private val spec = ModelSpec(
        id = "cloth_parse",
        fileName = "cloth_parse_fp16.onnx",
        displayName = "Cloth Parsing",
        version = 1,
        sizeBytes = 45_000_000L,
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        url = "https://models.shadow306.app/cloth_parse_fp16.onnx",
        features = listOf(ModelSpec.Feature.CLOTH_PARSE)
    )

    suspend fun parse(image: Bitmap, mode: EngineMode = EngineMode.AUTO): Bitmap = withContext(Dispatchers.Default) {
        val resized = BitmapUtils.resizeMaxDim(image, 512)

        val useOnnx = when (mode) {
            EngineMode.FALLBACK -> false
            EngineMode.ONNX -> true
            EngineMode.AUTO -> {
                val env = OnnxEnv.get(context)
                env.ensureLoaded() && ModelPaths.finalFile(context, spec).exists()
            }
        }

        if (!useOnnx) {
            return@withContext InpaintingUtils.segmentGarmentFallback(resized)
        }

        // ONNX path - try real session, fallback on failure
        try {
            val session = OnnxSessionFactory.create(context, spec)
            if (!session.isReady) {
                return@withContext InpaintingUtils.segmentGarmentFallback(resized)
            }
            // TODO: real preprocessing: resize to 512x512, normalize, run, argmax
            // For skeleton, we still use fallback but keep session lifecycle correct
            session.close()
            InpaintingUtils.segmentGarmentFallback(resized)
        } catch (t: Throwable) {
            InpaintingUtils.segmentGarmentFallback(resized)
        }
    }
}
