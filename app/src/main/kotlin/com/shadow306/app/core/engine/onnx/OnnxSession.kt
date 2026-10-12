package com.shadow306.app.core.engine.onnx

import android.content.Context
import com.shadow306.app.core.modeldownload.ModelPaths
import com.shadow306.app.core.modeldownload.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Thin abstraction over an ORT session. The implementation is split into
 * two branches:
 *
 * - RealOnnxSession (when ai.onnxruntime is present + model file exists)
 * - NoopSession (fallback that returns null and forces CPU path)
 *
 * Engines never touch ORT directly - they call [run] and handle null.
 */
interface OnnxSession : Closeable {
    val spec: ModelSpec
    val isReady: Boolean
    suspend fun run(inputs: Map<String, Any>): Map<String, Any>?
}

class NoopSession(override val spec: ModelSpec) : OnnxSession {
    override val isReady: Boolean = false
    override suspend fun run(inputs: Map<String, Any>): Map<String, Any>? = null
    override fun close() {}
}

/**
 * Factory that creates sessions. Keeps the heavy reflection in one place.
 */
object OnnxSessionFactory {

    suspend fun create(context: Context, spec: ModelSpec): OnnxSession = withContext(Dispatchers.IO) {
        val file = ModelPaths.finalFile(context, spec)
        if (!file.exists()) return@withContext NoopSession(spec)

        val env = OnnxEnv.get(context)
        if (!env.ensureLoaded()) return@withContext NoopSession(spec)

        // Try to create a real session via reflection so the project still compiles
        // without the native .so in this sandbox.
        try {
            RealOnnxSession(context, spec)
        } catch (t: Throwable) {
            NoopSession(spec)
        }
    }
}

/**
 * Real session backed by ORT. Loaded via reflection to keep the Gradle build
 * green even when the native libs are not present in CI.
 *
 * The class will only be instantiated on a real device where
 * com.microsoft.onnxruntime:onnxruntime-android is bundled.
 */
private class RealOnnxSession(
    private val context: Context,
    override val spec: ModelSpec
) : OnnxSession {

    @Volatile private var sessionObj: Any? = null
    override val isReady: Boolean get() = sessionObj != null

    init {
        val file = ModelPaths.finalFile(context, spec)
        val env = OnnxEnv.get(context).rawEnv() ?: throw IllegalStateException("ORT env not loaded")
        try {
            val envClass = env.javaClass
            val optsClass = Class.forName("ai.onnxruntime.OrtSession\$SessionOptions")
            val opts = optsClass.getDeclaredConstructor().newInstance()

            // setOptimizationLevel(ALL)
            runCatching {
                val levelClass = Class.forName("ai.onnxruntime.OrtSession\$SessionOptions\$OptLevel")
                val all = levelClass.getField("ALL").get(null)
                val setOpt = optsClass.getMethod("setOptimizationLevel", levelClass)
                setOpt.invoke(opts, all)
            }

            val createMethod = envClass.getMethod(
                "createSession",
                String::class.java,
                optsClass
            )
            sessionObj = createMethod.invoke(env, file.absolutePath, opts)
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to create ORT session for ${spec.fileName}", t)
        }
    }

    override suspend fun run(inputs: Map<String, Any>): Map<String, Any>? = withContext(Dispatchers.IO) {
        val sess = sessionObj ?: return@withContext null
        // Full tensor conversion is done by each engine's preprocessor.
        // Here we just invoke the session via reflection and return raw outputs.
        try {
            val runMethod = sess.javaClass.methods.firstOrNull { it.name == "run" } ?: return@withContext null
            // Real impl would convert inputs to OnnxTensor here.
            // For the skeleton we return null to trigger fallback.
            null
        } catch (t: Throwable) {
            null
        }
    }

    override fun close() {
        try {
            (sessionObj as? AutoCloseable)?.close()
        } catch (_: Throwable) {}
        sessionObj = null
    }
}
