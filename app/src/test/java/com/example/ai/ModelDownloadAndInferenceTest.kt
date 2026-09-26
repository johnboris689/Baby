package com.example.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadAndInferenceTest {

    @Test
    fun testHardwareAwareModelSelectionForHostDevice() {
        // Current device profile: 3.7 GB RAM (3788 MB), 1.4 GB free (1433 MB), 22.7 GB storage
        val hostProfile = HardwareProfile(
            totalRamMb = 3788L,
            availableRamMb = 1433L,
            cpuCores = 8,
            supportedAbis = listOf("arm64-v8a"),
            freeStorageMb = 23244L,
            androidVersion = 35,
            recommendedTier = HardwareTier.MID_RANGE
        )

        // 1. Verify recommendation is Baby Compact (Qwen 0.5B), NOT Baby Standard (which needs 3500MB)
        val recommended = ModelCatalog.getRecommendedModel(hostProfile)
        assertEquals("qwen2.5-0.5b", recommended.id)
        assertEquals(ModelCatalog.QWEN_0_5B.id, recommended.id)

        // 2. Verify Baby Standard gives HIGH MEMORY advisory
        val standardCompat = ModelCatalog.getCompatibility(ModelCatalog.LLAMA_3_2_1B, hostProfile)
        assertEquals(DeviceCompatibility.COMPATIBLE_HIGH_MEMORY, standardCompat)

        // 3. Verify Baby Compact gives RECOMMENDED
        val compactCompat = ModelCatalog.getCompatibility(ModelCatalog.QWEN_0_5B, hostProfile)
        assertEquals(DeviceCompatibility.RECOMMENDED, compactCompat)

        // 4. Verify Baby Pro gives INSUFFICIENT_RAM
        val proCompat = ModelCatalog.getCompatibility(ModelCatalog.QWEN_1_5B, hostProfile)
        assertEquals(DeviceCompatibility.INSUFFICIENT_RAM, proCompat)
    }

    @Test
    fun testModelDownloadUrlsAndRealGgufHeaders() {
        // Verify Llama 3.2 1B (Baby Standard) URL returns HTTP 200/206 and has valid GGUF magic bytes
        val standard = ModelCatalog.LLAMA_3_2_1B
        val url = URL(standard.downloadUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Baby-Android/1.0")
            setRequestProperty("Range", "bytes=0-1023")
            connectTimeout = 15000
            readTimeout = 15000
        }

        val code = conn.responseCode
        assertTrue("Download URL should return 200 or 206, got $code", code in listOf(200, 206))

        val headerBytes = ByteArray(1024)
        conn.inputStream.use { it.read(headerBytes) }
        val magic = String(headerBytes.copyOfRange(0, 4), Charsets.US_ASCII)
        assertEquals("GGUF", magic)
        conn.disconnect()
    }

    @Test
    fun testGgufParserAndBinaryIntegrity() {
        // Download first 4096 bytes of Qwen 0.5B to test local GGUF header parser
        val compact = ModelCatalog.QWEN_0_5B
        val conn = (URL(compact.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Baby-Android/1.0")
            setRequestProperty("Range", "bytes=0-4095")
            connectTimeout = 15000
            readTimeout = 15000
        }
        val tempSlice = File.createTempFile("test_model", ".gguf").apply { deleteOnExit() }
        conn.inputStream.use { input ->
            FileOutputStream(tempSlice).use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()

        RandomAccessFile(tempSlice, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            assertEquals("GGUF", String(magic, Charsets.US_ASCII))
            val version = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                val b = ByteArray(4)
                raf.readFully(b)
                put(b)
                flip()
            }.int
            assertTrue("GGUF version must be 2 or 3, got $version", version in 2..3)
        }
    }

    @Test
    fun testOfflineInferenceWithoutExternalApis() {
        // Verify on-device cognitive brain produces self-contained reasoning without internet
        val response = EmbeddedLocalBrain.processQuery(
            prompt = "What is 42 * 7?",
            documentText = "",
            isGgufModelActive = true
        )
        assertNotNull(response)
        assertTrue(response.contains("294"))

        val codingResponse = EmbeddedLocalBrain.processQuery(
            prompt = "Write a kotlin function to reverse a list",
            documentText = "",
            isGgufModelActive = true
        )
        assertNotNull(codingResponse)
        assertTrue(codingResponse.contains("fun") || codingResponse.contains("reverse"))
    }
}
