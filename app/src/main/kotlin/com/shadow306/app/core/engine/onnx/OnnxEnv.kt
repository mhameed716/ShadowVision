package com.shadow306.app.core.engine.onnx

import android.content.Context
import com.shadow306.app.core.modeldownload.ModelPaths
import com.shadow306.app.core.modeldownload.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lazy singleton wrapper around ai.onnxruntime.OrtEnvironment.
 *
 * The real ORT Mobile library (com.microsoft.onnxruntime:onnxruntime-android)
 * is loaded only when a model file actually exists. If the models are not
 * installed yet, [isAvailable] returns false and every engine falls back to
 * the CPU approximation in core/engine.
 *
 * This design guarantees the app never crashes on devices without the models,
 * and still passes Play Store review (no hidden native crashes).
 */
class OnnxEnv private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: OnnxEnv? = null

        fun get(context: Context): OnnxEnv =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnnxEnv(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val mutex = Mutex()
    private var ortEnv: Any? = null // ai.onnxruntime.OrtEnvironment (reflection to avoid hard dep at compile)
    private var loaded = false

    /** True when ORT native libs could be loaded. */
    val isAvailable: Boolean get() = loaded

    /** All model files currently on disk. */
    suspend fun availableModels(): List<File> = withContext(Dispatchers.IO) {
        val dir = ModelPaths.modelsDir(context)
        dir.listFiles()?.filter { it.extension == "onnx" && it.length() > 0 } ?: emptyList()
    }

    suspend fun isModelReady(spec: ModelSpec): Boolean = withContext(Dispatchers.IO) {
        val f = ModelPaths.finalFile(context, spec)
        f.exists() && f.length() > 0
    }

    /**
     * Tries to load ORT via reflection. Returns true on success.
     * Safe to call multiple times.
     */
    suspend fun ensureLoaded(): Boolean = mutex.withLock {
        if (loaded) return@withLock true
        return@withLock withContext(Dispatchers.IO) {
            try {
                val envClass = Class.forName("ai.onnxruntime.OrtEnvironment")
                val getEnv = envClass.getMethod("getEnvironment")
                ortEnv = getEnv.invoke(null)
                loaded = true
                true
            } catch (t: Throwable) {
                // ORT not bundled or native lib missing - fallback mode
                loaded = false
                false
            }
        }
    }

    suspend fun close() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                ortEnv?.let { env ->
                    val closeMethod = env.javaClass.getMethod("close")
                    closeMethod.invoke(env)
                }
            } catch (_: Throwable) {}
            ortEnv = null
            loaded = false
        }
    }

    /** For engines that want the raw OrtEnvironment instance. */
    fun rawEnv(): Any? = ortEnv
}

enum class EngineMode {
    AUTO,   // use ONNX if models present, else fallback
    ONNX,   // force ONNX - fail if missing
    FALLBACK // force CPU approximation
}
