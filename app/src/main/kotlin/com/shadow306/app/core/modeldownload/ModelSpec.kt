package com.shadow306.app.core.modeldownload

import org.json.JSONObject

/**
 * Single ONNX model descriptor. Serialized in assets/models/manifest.json
 * and also mirrored in models/manifest.json at repo root for tooling.
 *
 * The manifest is the single source of truth for the DownloadManager:
 * it contains download URL, expected size, sha256 and which feature needs it.
 */
data class ModelSpec(
    val id: String,                 // stable id, e.g. "tryon_warp"
    val fileName: String,           // e.g. "tryon_warp_fp16.onnx"
    val displayName: String,        // human name for UI
    val version: Int,               // bump to trigger re-download
    val sizeBytes: Long,            // uncompressed expected size
    val sha256: String,             // lower-case hex, 64 chars
    val url: String,                // https url (primary)
    val mirrors: List<String> = emptyList(), // fallback cdn urls
    val required: Boolean = true,   // false -> optional enhancement
    val features: List<Feature>,    // which screens need this model
    val minRamMb: Int = 2048,       // skip on low ram devices if optional
    val abiFilter: List<String> = emptyList(), // empty = any abi
) {
    enum class Feature {
        TRY_ON,
        FACE_SWAP,
        VIDEO_GEN,
        CLOTH_PARSE,
        INPAINT,
        TRANSLATE
    }

    companion object {
        fun fromJson(o: JSONObject): ModelSpec {
            fun optStringArray(key: String): List<String> {
                if (!o.has(key)) return emptyList()
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it) }
            }
            return ModelSpec(
                id = o.getString("id"),
                fileName = o.getString("fileName"),
                displayName = o.optString("displayName", o.getString("id")),
                version = o.optInt("version", 1),
                sizeBytes = o.optLong("sizeBytes", 0L),
                sha256 = o.getString("sha256").lowercase(),
                url = o.getString("url"),
                mirrors = optStringArray("mirrors"),
                required = o.optBoolean("required", true),
                features = optStringArray("features").mapNotNull { raw ->
                    runCatching { Feature.valueOf(raw.uppercase()) }.getOrNull()
                },
                minRamMb = o.optInt("minRamMb", 2048),
                abiFilter = optStringArray("abiFilter")
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("displayName", displayName)
        put("version", version)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
        put("url", url)
        if (mirrors.isNotEmpty()) put("mirrors", mirrors)
        put("required", required)
        put("features", features.map { it.name })
        put("minRamMb", minRamMb)
        if (abiFilter.isNotEmpty()) put("abiFilter", abiFilter)
    }
}

data class Manifest(
    val schemaVersion: Int,
    val packVersion: Int,
    val createdAt: String, // ISO-8601
    val totalBytes: Long,
    val models: List<ModelSpec>,
    val notes: String? = null
) {
    companion object {
        fun fromJson(json: String): Manifest {
            val root = JSONObject(json)
            val arr = root.getJSONArray("models")
            val models = (0 until arr.length()).map { ModelSpec.fromJson(arr.getJSONObject(it)) }
            return Manifest(
                schemaVersion = root.optInt("schemaVersion", 1),
                packVersion = root.optInt("packVersion", 1),
                createdAt = root.optString("createdAt", ""),
                totalBytes = root.optLong("totalBytes", models.sumOf { it.sizeBytes }),
                models = models,
                notes = root.optString("notes", null)
            )
        }
    }

    fun toJsonString(pretty: Boolean = true): String {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        root.put("packVersion", packVersion)
        root.put("createdAt", createdAt)
        root.put("totalBytes", totalBytes)
        if (notes != null) root.put("notes", notes)
        val arr = org.json.JSONArray()
        models.forEach { arr.put(it.toJson()) }
        root.put("models", arr)
        return if (pretty) root.toString(2) else root.toString()
    }

    /** Models required for [feature]. */
    fun forFeature(feature: ModelSpec.Feature): List<ModelSpec> =
        models.filter { feature in it.features }

    /** All required models. */
    fun requiredModels(): List<ModelSpec> = models.filter { it.required }
}
