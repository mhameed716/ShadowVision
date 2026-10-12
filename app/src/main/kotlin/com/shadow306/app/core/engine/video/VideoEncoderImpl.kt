package com.shadow306.app.core.engine.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.shadow306.app.core.engine.video.VideoEngine.Params.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Real MP4 encoder using MediaCodec + MediaMuxer.
 *
 * This is a production-ready skeleton that converts Bitmaps to YUV420 and
 * feeds them to AVC encoder. It handles:
 * - Color format negotiation (COLOR_FormatYUV420Flexible)
 * - Presentation timestamps
 * - Muxer start/stop lifecycle
 * - Error recovery
 *
 * The fallback placeholder in VideoEngine.encode is replaced by this when
 * frames are available. If MediaCodec fails, it falls back to placeholder file.
 */
class VideoEncoderImpl(private val context: Context) {

    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_USEC = 10000L
    }

    suspend fun encode(
        frames: List<Bitmap>,
        outFile: File,
        fps: Int,
        quality: Quality,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext false

        val width = frames.first().width
        val height = frames.first().height

        // Ensure even dimensions (required by AVC)
        val encWidth = if (width % 2 == 0) width else width - 1
        val encHeight = if (height % 2 == 0) height else height - 1

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false

        try {
            outFile.parentFile?.mkdirs()

            val bitrate = when (quality) {
                Quality.LOW -> 2_000_000
                Quality.MEDIUM -> 5_000_000
                Quality.HIGH -> 8_000_000
            }

            val format = MediaFormat.createVideoFormat(MIME_TYPE, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Use high profile if available
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                }
            }

            codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var frameIndex = 0L
            var totalFrames = frames.size

            for ((idx, bitmap) in frames.withIndex()) {
                // Convert bitmap to YUV - simplified: we use RGB to YUV conversion
                // Real impl would use RenderScript or libyuv for speed
                val yuv = bitmapToYuv420(bitmap, encWidth, encHeight)

                val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputBufferId >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                    inputBuffer.clear()
                    inputBuffer.put(yuv)
                    val pts = frameIndex * 1_000_000L / fps
                    codec.queueInputBuffer(inputBufferId, 0, yuv.size, pts, 0)
                    frameIndex++
                }

                // Drain encoder
                var encoderOutputAvailable = true
                while (encoderOutputAvailable) {
                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                    when {
                        outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> encoderOutputAvailable = false
                        outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw RuntimeException("Format changed twice")
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBufferId >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outputBufferId) ?: continue
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                if (!muxerStarted) throw RuntimeException("Muxer not started")
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputBufferId, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                encoderOutputAvailable = false
                            }
                        }
                    }
                }

                onProgress(((idx + 1) * 100 / totalFrames).coerceIn(0, 100))
            }

            // Signal end of stream
            val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferId >= 0) {
                codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // Drain remaining
            var encoderOutputAvailable = true
            while (encoderOutputAvailable) {
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                when {
                    outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> encoderOutputAvailable = false
                    outputBufferId >= 0 -> {
                        val encodedData = codec.getOutputBuffer(outputBufferId)!!
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderOutputAvailable = false
                        }
                    }
                }
            }

            true
        } catch (t: Throwable) {
            // Fallback to placeholder file so UI still works
            try {
                outFile.writeText("SHADOW306_VIDEO_PLACEHOLDER fallback due to ${t.message}\n")
            } catch (_: Throwable) {}
            false
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        // Simple RGB to YUV420 conversion - not optimized, but works
        // For production, use RenderScript or native libyuv
        val argb = IntArray(width * height)
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        scaled.getPixels(argb, 0, width, 0, 0, width, height)
        if (scaled != bitmap) scaled.recycle()

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val rgb = argb[j * width + i]
                val r = (rgb shr 16 and 0xFF)
                val g = (rgb shr 8 and 0xFF)
                val b = (rgb and 0xFF)

                // RGB to YUV
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
