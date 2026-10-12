package com.shadow306.app.core.engine.imaging

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CPU fallback for garment removal / inpainting when the ONNX inpaint model
 * is not installed yet. The algorithm is deliberately simple:
 *
 * 1. Build a skin color model from non-masked border pixels
 * 2. Fill masked area with average skin color + noise
 * 3. Feather edges with a small blur
 *
 * Quality is approximated, but it lets the UI work in Preview mode.
 */
object InpaintingUtils {

    /**
     * @param image source bitmap (ARGB_8888)
     * @param mask  same size, white = area to inpaint, black = keep
     */
    fun inpaintFallback(image: Bitmap, mask: Bitmap): Bitmap {
        val w = image.width
        val h = image.height
        require(mask.width == w && mask.height == h)

        val srcPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        image.getPixels(srcPixels, 0, w, 0, 0, w, h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        // Estimate skin color from unmasked border near mask
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val isMasked = (maskPixels[idx] and 0xFF) > 128
                if (isMasked) continue
                // Only sample pixels within 20px of a masked pixel
                var nearMask = false
                loop@ for (dy in -20..20 step 5) {
                    for (dx in -20..20 step 5) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val nIdx = ny * w + nx
                        if ((maskPixels[nIdx] and 0xFF) > 128) { nearMask = true; break@loop }
                    }
                }
                if (!nearMask) continue
                val c = srcPixels[idx]
                rSum += (c shr 16 and 0xFF)
                gSum += (c shr 8 and 0xFF)
                bSum += (c and 0xFF)
                count++
            }
        }

        val avgR = if (count > 0) (rSum / count).toInt() else 200
        val avgG = if (count > 0) (gSum / count).toInt() else 180
        val avgB = if (count > 0) (bSum / count).toInt() else 160

        val outPixels = srcPixels.clone()
        val rnd = java.util.Random(42)

        for (i in srcPixels.indices) {
            val isMasked = (maskPixels[i] and 0xFF) > 128
            if (!isMasked) continue
            // Add subtle variation to avoid flat fill
            val nr = (avgR + rnd.nextInt(30) - 15).coerceIn(0, 255)
            val ng = (avgG + rnd.nextInt(30) - 15).coerceIn(0, 255)
            val nb = (avgB + rnd.nextInt(30) - 15).coerceIn(0, 255)
            outPixels[i] = Color.rgb(nr, ng, nb)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)

        // Feather
        return BitmapUtils.fastBlur(out, 3)
    }

    /**
     * Creates a soft mask from a hard binary mask (0/255) by blurring.
     */
    fun softenMask(mask: Bitmap, radius: Int = 8): Bitmap {
        return BitmapUtils.fastBlur(mask, radius)
    }

    /**
     * Simple garment color segmentation fallback: finds pixels whose color
     * distance from background is high. Very rough, only for Preview mode.
     */
    fun segmentGarmentFallback(image: Bitmap): Bitmap {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)

        // Assume corners are background
        val bgSamples = mutableListOf<Int>()
        val corners = listOf(0, w - 1, (h - 1) * w, h * w - 1, w / 2, (h - 1) * w + w / 2)
        for (idx in corners) if (idx in pixels.indices) bgSamples.add(pixels[idx])

        var bgR = 0; var bgG = 0; var bgB = 0
        for (c in bgSamples) {
            bgR += c shr 16 and 0xFF
            bgG += c shr 8 and 0xFF
            bgB += c and 0xFF
        }
        bgR /= bgSamples.size.coerceAtLeast(1)
        bgG /= bgSamples.size.coerceAtLeast(1)
        bgB /= bgSamples.size.coerceAtLeast(1)

        val maskPixels = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            val dist = sqrt(
                ((r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)).toDouble()
            )
            // Heuristic: garment is not background and not skin (skin is more red)
            val isGarment = dist > 40 && !(r > g + 15 && r > b + 15 && r in 100..240)
            maskPixels[i] = if (isGarment) Color.WHITE else Color.BLACK
        }

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }
}
