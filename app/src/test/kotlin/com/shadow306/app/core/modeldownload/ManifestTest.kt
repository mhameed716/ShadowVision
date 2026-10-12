package com.shadow306.app.core.modeldownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {

    @Test
    fun `manifest parsing`() {
        val json = """
        {
          "schemaVersion": 1,
          "packVersion": 1,
          "createdAt": "2026-10-12T00:00:00Z",
          "totalBytes": 1000,
          "models": [
            {
              "id": "test_model",
              "fileName": "test.onnx",
              "displayName": "Test",
              "version": 1,
              "sizeBytes": 1000,
              "sha256": "${"a".repeat(64)}",
              "url": "https://example.com/test.onnx",
              "features": ["TRY_ON"],
              "required": true
            }
          ]
        }
        """.trimIndent()

        val manifest = Manifest.fromJson(json)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(1, manifest.models.size)
        assertEquals("test_model", manifest.models[0].id)
        assertEquals(1000L, manifest.models[0].sizeBytes)
    }

    @Test
    fun `forFeature filters correctly`() {
        val json = """
        {
          "schemaVersion": 1,
          "packVersion": 1,
          "createdAt": "2026-10-12T00:00:00Z",
          "totalBytes": 2000,
          "models": [
            {
              "id": "tryon",
              "fileName": "tryon.onnx",
              "sizeBytes": 1000,
              "sha256": "${"a".repeat(64)}",
              "url": "https://example.com/tryon.onnx",
              "features": ["TRY_ON"]
            },
            {
              "id": "faceswap",
              "fileName": "faceswap.onnx",
              "sizeBytes": 1000,
              "sha256": "${"b".repeat(64)}",
              "url": "https://example.com/faceswap.onnx",
              "features": ["FACE_SWAP"]
            }
          ]
        }
        """.trimIndent()

        val manifest = Manifest.fromJson(json)
        assertEquals(1, manifest.forFeature(ModelSpec.Feature.TRY_ON).size)
        assertEquals(1, manifest.forFeature(ModelSpec.Feature.FACE_SWAP).size)
        assertEquals(0, manifest.forFeature(ModelSpec.Feature.VIDEO_GEN).size)
    }
}
