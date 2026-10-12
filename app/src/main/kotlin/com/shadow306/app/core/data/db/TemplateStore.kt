package com.shadow306.app.core.data.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.shadow306.app.core.util.FileIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local template database - file-based, no Room/KSP needed.
 *
 * Each template is:
 *   files/templates/<uuid>/
 *     image.jpg          (original garment, copied from gallery)
 *     thumb.jpg          (256px thumbnail)
 *     meta.json          (name, category, createdAt, hash)
 *
 * An index file at files/templates/index.json keeps the list for fast startup.
 *
 * This avoids the heavy KSP + Room setup and works 100% offline.
 */
data class TemplateItem(
    val id: String,
    val name: String,
    val category: Category,
    val imageFile: File,
    val thumbFile: File,
    val createdAt: Long,
    val hash: String, // sha256 of original image for duplicate detection
    val builtin: Boolean = false
) {
    enum class Category {
        SHIRT, PANTS, DRESS, JACKET, ABAYA, OTHER
    }

    companion object {
        fun categoryFromString(raw: String): Category =
            runCatching { Category.valueOf(raw.uppercase()) }.getOrDefault(Category.OTHER)
    }
}

class TemplateStore(private val context: Context) {

    private val _templates = MutableStateFlow<List<TemplateItem>>(emptyList())
    val templatesFlow: Flow<List<TemplateItem>> = _templates.asStateFlow()

    private fun rootDir(): File = FileIo.ensureDir(File(context.filesDir, "templates"))

    suspend fun load() = withContext(Dispatchers.IO) {
        val root = rootDir()
        val indexFile = File(root, "index.json")
        val items = mutableListOf<TemplateItem>()

        // Load builtins from assets/templates/ (optional - may not exist in dev)
        loadBuiltins(root, items)

        if (indexFile.exists()) {
            runCatching {
                val json = indexFile.readText()
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val dir = File(root, id)
                    if (!dir.exists()) continue
                    val img = File(dir, "image.jpg")
                    val thumb = File(dir, "thumb.jpg")
                    if (!img.exists()) continue
                    items.add(
                        TemplateItem(
                            id = id,
                            name = o.optString("name", id),
                            category = TemplateItem.categoryFromString(o.optString("category", "OTHER")),
                            imageFile = img,
                            thumbFile = if (thumb.exists()) thumb else img,
                            createdAt = o.optLong("createdAt", 0L),
                            hash = o.optString("hash", ""),
                            builtin = o.optBoolean("builtin", false)
                        )
                    )
                }
            }
        } else {
            // First run - scan directories
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val meta = File(dir, "meta.json")
                val img = File(dir, "image.jpg")
                if (!meta.exists() || !img.exists()) return@forEach
                runCatching {
                    val o = JSONObject(meta.readText())
                    items.add(
                        TemplateItem(
                            id = dir.name,
                            name = o.optString("name", dir.name),
                            category = TemplateItem.categoryFromString(o.optString("category", "OTHER")),
                            imageFile = img,
                            thumbFile = File(dir, "thumb.jpg").takeIf { it.exists() } ?: img,
                            createdAt = o.optLong("createdAt", 0L),
                            hash = o.optString("hash", ""),
                            builtin = o.optBoolean("builtin", false)
                        )
                    )
                }
            }
        }

        _templates.value = items.sortedByDescending { it.createdAt }
    }

    suspend fun addFromFile(source: File, name: String, category: TemplateItem.Category): Result<TemplateItem> =
        withContext(Dispatchers.IO) {
            if (!source.exists()) return@withContext Result.failure(IllegalArgumentException("source not found"))

            // Hash for duplicate detection
            val hash = com.shadow306.app.core.util.Checksums.sha256(source)
            val existing = _templates.value.find { it.hash == hash }
            if (existing != null) {
                return@withContext Result.failure(IllegalStateException("duplicate:${existing.id}"))
            }

            // Decode to validate image
            val bmp = BitmapFactory.decodeFile(source.absolutePath)
                ?: return@withContext Result.failure(IllegalArgumentException("unsupported image"))

            val id = UUID.randomUUID().toString()
            val dir = FileIo.ensureDir(File(rootDir(), id))
            val destImage = File(dir, "image.jpg")
            val destThumb = File(dir, "thumb.jpg")

            // Copy original as JPEG 95%
            destImage.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Thumbnail 256px
            val thumb = Bitmap.createScaledBitmap(bmp, 256, (256f * bmp.height / bmp.width).toInt(), true)
            destThumb.outputStream().use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (thumb != bmp) thumb.recycle()
            bmp.recycle()

            val now = System.currentTimeMillis()
            val meta = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("category", category.name)
                put("createdAt", now)
                put("hash", hash)
                put("builtin", false)
            }
            File(dir, "meta.json").writeText(meta.toString())

            val item = TemplateItem(id, name, category, destImage, destThumb, now, hash, false)
            _templates.value = (listOf(item) + _templates.value).sortedByDescending { it.createdAt }
            persistIndex()
            Result.success(item)
        }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val root = rootDir()
        val dir = File(root, id)
        val item = _templates.value.find { it.id == id } ?: return@withContext false
        if (item.builtin) return@withContext false
        FileIo.deleteRecursively(dir)
        _templates.value = _templates.value.filterNot { it.id == id }
        persistIndex()
        true
    }

    suspend fun rename(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val root = rootDir()
        val dir = File(root, id)
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return@withContext false
        val meta = JSONObject(metaFile.readText())
        meta.put("name", newName)
        metaFile.writeText(meta.toString())
        _templates.value = _templates.value.map { if (it.id == id) it.copy(name = newName) else it }
        persistIndex()
        true
    }

    private fun persistIndex() {
        val root = rootDir()
        val index = File(root, "index.json")
        val arr = JSONArray()
        _templates.value.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category.name)
                put("createdAt", item.createdAt)
                put("hash", item.hash)
                put("builtin", item.builtin)
            })
        }
        index.writeText(arr.toString())
    }

    private fun loadBuiltins(root: File, out: MutableList<TemplateItem>) {
        // Builtins are optional assets/templates/*.jpg with a manifest
        runCatching {
            val assetList = context.assets.list("templates") ?: return@runCatching
            for (assetName in assetList) {
                if (!assetName.endsWith(".jpg") && !assetName.endsWith(".png")) continue
                val id = "builtin_${assetName.substringBeforeLast('.')}"
                val dir = File(root, id)
                if (!dir.exists()) {
                    FileIo.ensureDir(dir)
                    context.assets.open("templates/$assetName").use { input ->
                        File(dir, "image.jpg").outputStream().use { outStream ->
                            input.copyTo(outStream)
                        }
                    }
                    File(dir, "thumb.jpg").apply {
                        if (!exists()) File(dir, "image.jpg").copyTo(this)
                    }
                    val meta = JSONObject().apply {
                        put("id", id)
                        put("name", assetName.substringBeforeLast('.'))
                        put("category", "OTHER")
                        put("createdAt", 0L)
                        put("hash", "")
                        put("builtin", true)
                    }
                    File(dir, "meta.json").writeText(meta.toString())
                }
                val img = File(dir, "image.jpg")
                if (img.exists()) {
                    out.add(
                        TemplateItem(
                            id = id,
                            name = assetName.substringBeforeLast('.'),
                            category = TemplateItem.Category.OTHER,
                            imageFile = img,
                            thumbFile = File(dir, "thumb.jpg").takeIf { it.exists() } ?: img,
                            createdAt = 0L,
                            hash = "",
                            builtin = true
                        )
                    )
                }
            }
        }
    }
}
