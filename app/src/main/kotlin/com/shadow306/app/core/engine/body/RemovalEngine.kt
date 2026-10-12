package com.shadow306.app.core.engine.body

import android.content.Context
import android.graphics.Bitmap
import com.shadow306.app.core.engine.imaging.BitmapUtils
import com.shadow306.app.core.engine.imaging.InpaintingUtils
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.engine.onnx.OnnxEnv
import com.shadow306.app.core.engine.onnx.OnnxSessionFactory
import com.shadow306.app.core.modeldownload.ModelPaths
import com.shadow306.app.core.modeldownload.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Garment removal & inpainting engine (Interface #5).
 *
 * ONNX pipeline:
 *   1. Cloth parsing (pixel-perfect garment mask)
 *   2. Inpainting model fills masked region with skin/body texture
 *
 * Fallback:
 *   - ClothParser fallback segmentation
 *   - InpaintingUtils.inpaintFallback (average skin + blur)
 */
class RemovalEngine(private val context: Context) {

    data class Params(
        val sensitivity: Int = 50, // cloth parsing threshold
        val skinMatch: Boolean = true,
        val smoothness: Int = 50
    )

    data class Result(
        val bitmap: Bitmap,
        val mask: Bitmap,
        val usedFallback: Boolean,
        val inferenceMs: Long
    )

    private val inpaintSpec = ModelSpec(
        id = "inpaint_body",
        fileName = "inpaint_body_fp16.onnx",
        displayName = "Body Inpaint",
        version = 1,
        sizeBytes = 150_000_000L,
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        url = "https://models.shadow306.app/inpaint_body_fp16.onnx",
        features = listOf(ModelSpec.Feature.INPAINT, ModelSpec.Feature.CLOTH_PARSE)
    )

    private val clothParser = ClothParser(context)

    suspend fun remove(
        image: Bitmap,
        manualMask: Bitmap? = null,
        params: Params = Params(),
        mode: EngineMode = EngineMode.AUTO
    ): Result = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()

        val mask = manualMask ?: clothParser.parse(image, mode)

        val useOnnx = when (mode) {
            EngineMode.FALLBACK -> false
            EngineMode.ONNX -> true
            EngineMode.AUTO -> {
                val env = OnnxEnv.get(context)
                env.ensureLoaded() && ModelPaths.finalFile(context, inpaintSpec).exists()
            }
        }

        if (!useOnnx) {
            val inpainted = InpaintingUtils.inpaintFallback(image, mask)
            return@withContext Result(inpainted, mask, true, System.currentTimeMillis() - start)
        }

        try {
            val session = OnnxSessionFactory.create(context, inpaintSpec)
            if (!session.isReady) {
                session.close()
                val inpainted = InpaintingUtils.inpaintFallback(image, mask)
                return@withContext Result(inpainted, mask, true, System.currentTimeMillis() - start)
            }
            // TODO: real inpaint
            session.close()
            val inpainted = InpaintingUtils.inpaintFallback(image, mask)
            Result(inpainted, mask, true, System.currentTimeMillis() - start)
        } catch (t: Throwable) {
            val inpainted = InpaintingUtils.inpaintFallback(image, mask)
            Result(inpainted, mask, true, System.currentTimeMillis() - start)
        }
    }
}
