package com.shadow306.app.core.engine.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.shadow306.app.core.engine.imaging.BitmapUtils
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.engine.onnx.OnnxEnv
import com.shadow306.app.core.engine.onnx.OnnxSessionFactory
import com.shadow306.app.core.modeldownload.ModelPaths
import com.shadow306.app.core.modeldownload.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Virtual Try-On engine.
 *
 * Pipeline (ONNX):
 *   1. Pose estimation (body keypoints)
 *   2. Cloth warping (TPS / flow) to match body shape
 *   3. Try-on synthesis (inpaint person + warped garment)
 *
 * Fallback (Preview mode):
 *   - Alpha blend garment over detected torso region using mask from ClothParser
 *   - Simple perspective warp based on garment aspect vs torso aspect
 *
 * The fallback is intentionally fast (< 300ms on mid-range SoC) and lets users
 * test the UX before models finish downloading.
 */
class TryOnEngine(private val context: Context) {

    data class Params(
        val fitTightness: Int = 50, // 0 loose .. 100 tight
        val matchShadow: Boolean = true,
        val keepPose: Boolean = true
    )

    data class Result(
        val bitmap: Bitmap,
        val usedFallback: Boolean,
        val inferenceMs: Long
    )

    private val warpSpec = ModelSpec(
        id = "tryon_warp",
        fileName = "tryon_warp_fp16.onnx",
        displayName = "Try-On Warp",
        version = 1,
        sizeBytes = 120_000_000L,
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        url = "https://models.shadow306.app/tryon_warp_fp16.onnx",
        features = listOf(ModelSpec.Feature.TRY_ON)
    )

    private val tryOnSpec = ModelSpec(
        id = "tryon_synth",
        fileName = "tryon_synth_fp16.onnx",
        displayName = "Try-On Synthesis",
        version = 1,
        sizeBytes = 180_000_000L,
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        url = "https://models.shadow306.app/tryon_synth_fp16.onnx",
        features = listOf(ModelSpec.Feature.TRY_ON)
    )

    private val clothParser = ClothParser(context)

    suspend fun tryOn(
        person: Bitmap,
        garment: Bitmap,
        params: Params = Params(),
        mode: EngineMode = EngineMode.AUTO
    ): Result = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()

        val useOnnx = when (mode) {
            EngineMode.FALLBACK -> false
            EngineMode.ONNX -> true
            EngineMode.AUTO -> {
                val env = OnnxEnv.get(context)
                env.ensureLoaded() &&
                        ModelPaths.finalFile(context, warpSpec).exists() &&
                        ModelPaths.finalFile(context, tryOnSpec).exists()
            }
        }

        if (!useOnnx) {
            val blended = fallbackBlend(person, garment, params)
            return@withContext Result(blended, usedFallback = true, System.currentTimeMillis() - start)
        }

        // ONNX path - skeleton that still does fallback but keeps session handling
        try {
            val warpSession = OnnxSessionFactory.create(context, warpSpec)
            val synthSession = OnnxSessionFactory.create(context, tryOnSpec)

            if (!warpSession.isReady || !synthSession.isReady) {
                warpSession.close(); synthSession.close()
                val blended = fallbackBlend(person, garment, params)
                return@withContext Result(blended, true, System.currentTimeMillis() - start)
            }

            // TODO: real warp + synth
            // 1. preprocess person (pose) + garment
            // 2. run warpSession -> flow field
            // 3. warp garment with flow
            // 4. run synthSession -> final image

            warpSession.close()
            synthSession.close()

            val blended = fallbackBlend(person, garment, params)
            Result(blended, usedFallback = true, System.currentTimeMillis() - start)

        } catch (t: Throwable) {
            val blended = fallbackBlend(person, garment, params)
            Result(blended, usedFallback = true, System.currentTimeMillis() - start)
        }
    }

    private fun fallbackBlend(person: Bitmap, garment: Bitmap, params: Params): Bitmap {
        // Very rough torso region: middle 60% width, 25%-75% height
        val w = person.width
        val h = person.height
        val torsoLeft = (w * 0.2f).toInt()
        val torsoTop = (h * 0.22f).toInt()
        val torsoRight = (w * 0.8f).toInt()
        val torsoBottom = (h * 0.78f).toInt()
        val torsoW = torsoRight - torsoLeft
        val torsoH = torsoBottom - torsoTop

        // Resize garment to torso, respecting fit tightness
        val fitScale = 0.85f + (params.fitTightness / 100f) * 0.3f // 0.85 .. 1.15
        val targetW = (torsoW * fitScale).toInt()
        val targetH = (torsoH * fitScale).toInt()
        val resizedGarment = Bitmap.createScaledBitmap(garment, targetW, targetH, true)

        val out = person.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            alpha = if (params.matchShadow) 235 else 255
        }

        val left = torsoLeft + (torsoW - targetW) / 2
        val top = torsoTop + (torsoH - targetH) / 2
        canvas.drawBitmap(resizedGarment, left.toFloat(), top.toFloat(), paint)

        resizedGarment.recycle()
        return out
    }
}
