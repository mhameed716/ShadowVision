package com.shadow306.app.core.engine.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-CPU image helpers shared by all engines. No ORT, no RenderScript,
 * no native libs - works in Preview mode and on low-RAM devices.
 */
object BitmapUtils {

    suspend fun load(file: File, maxDim: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        val largest = max(opts.outWidth, opts.outHeight)
        while (largest / sample > maxDim) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts2)
    }

    suspend fun load(bytes: ByteArray, maxDim: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        val largest = max(opts.outWidth, opts.outHeight)
        while (largest / sample > maxDim) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)
    }

    /** Resize so the longest side == [maxDim], preserving aspect. */
    fun resizeMaxDim(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (max(w, h) <= maxDim) return src
        val scale = maxDim.toFloat() / max(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    /** Center-crop to [size] x [size]. */
    fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return if (cropped.width == size) cropped
        else Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    /** Simple box blur approximation for fallback inpainting feathering. */
    fun fastBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 1) return src
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val canvas = Canvas(out)
        // Downscale -> upscale trick gives a cheap blur
        val scale = (1f / radius).coerceIn(0.1f, 1f)
        val smallW = (w * scale).toInt().coerceAtLeast(1)
        val smallH = (h * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        canvas.drawBitmap(small, null, Rect(0, 0, w, h), paint)
        small.recycle()
        return out
    }

    /** Alpha blend [fg] over [bg] using [mask] (0..255). All bitmaps must match size. */
    fun blendWithMask(bg: Bitmap, fg: Bitmap, mask: Bitmap): Bitmap {
        val w = bg.width
        val h = bg.height
        require(fg.width == w && fg.height == h && mask.width == w && mask.height == h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val bgPixels = IntArray(w * h)
        val fgPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        bg.getPixels(bgPixels, 0, w, 0, 0, w, h)
        fg.getPixels(fgPixels, 0, w, 0, 0, w, h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val alpha = (maskPixels[i] shr 16 and 0xFF) // use red channel as mask
            val inv = 255 - alpha
            val bgC = bgPixels[i]
            val fgC = fgPixels[i]
            val r = ((fgC shr 16 and 0xFF) * alpha + (bgC shr 16 and 0xFF) * inv) / 255
            val g = ((fgC shr 8 and 0xFF) * alpha + (bgC shr 8 and 0xFF) * inv) / 255
            val b = ((fgC and 0xFF) * alpha + (bgC and 0xFF) * inv) / 255
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    suspend fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): File = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        file
    }

    fun toNormalizedFloatArray(bitmap: Bitmap): FloatArray {
        // NHWC -> NCHW float32 normalized to [-1,1] or [0,1] depending on model.
        // Here we produce [0,1] and engines can remap.
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(w * h * 3)
        for (i in pixels.indices) {
            val c = pixels[i]
            out[i * 3] = ((c shr 16 and 0xFF) / 255f)
            out[i * 3 + 1] = ((c shr 8 and 0xFF) / 255f)
            out[i * 3 + 2] = ((c and 0xFF) / 255f)
        }
        return out
    }
}
