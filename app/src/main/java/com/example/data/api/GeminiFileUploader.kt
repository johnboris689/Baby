package com.example.data.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uploads large media/documents to Gemini's Files API and returns a reusable file URI. */
object GeminiFileUploader {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    data class UploadedFile(val uri: String, val mimeType: String)

    suspend fun upload(
        context: Context,
        uri: Uri,
        apiKey: String,
        mimeType: String,
        displayName: String
    ): UploadedFile {
        val resolver = context.contentResolver
        val size = querySize(resolver, uri)
        val streamProvider = { resolver.openInputStream(uri) ?: throw IOException("Unable to open selected file") }
        return uploadStream(apiKey, mimeType, displayName, size, streamProvider)
    }

    suspend fun uploadFile(
        file: File,
        apiKey: String,
        mimeType: String,
        displayName: String = file.name
    ): UploadedFile {
        if (!file.exists()) throw IOException("Extracted attachment no longer exists: ${file.name}")
        return uploadStream(apiKey, mimeType, displayName, file.length()) { file.inputStream() }
    }

    private suspend fun uploadStream(
        apiKey: String,
        mimeType: String,
        displayName: String,
        size: Long,
        streamProvider: () -> java.io.InputStream
    ): UploadedFile {
        if (apiKey.isBlank()) throw IOException("Gemini API key is missing")
        val metadata = "{\"file\":{\"display_name\":${JSONObject.quote(displayName)}}}"

        val startRequest = Request.Builder()
            .url("${BASE_URL}upload/v1beta/files?key=${Uri.encode(apiKey)}")
            .header("x-goog-api-key", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .apply { if (size >= 0) header("X-Goog-Upload-Header-Content-Length", size.toString()) }
            .post(RequestBody.create("application/json".toMediaType(), metadata))
            .build()

        val uploadUrl = client.newCall(startRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gemini upload initialization failed: HTTP ${response.code}")
            response.header("X-Goog-Upload-URL")
                ?: response.header("x-goog-upload-url")
                ?: throw IOException("Gemini did not return an upload URL")
        }

        val body = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: okio.BufferedSink) {
                streamProvider().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
        }

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Content-Type", mimeType)
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(body)
            .build()

        val fileName = client.newCall(uploadRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Gemini upload failed: HTTP ${response.code}: $text")
            JSONObject(text).optJSONObject("file")?.optString("name")
                ?: throw IOException("Gemini upload returned no file name")
        }

        repeat(90) {
            val statusRequest = Request.Builder()
                .url("${BASE_URL}v1beta/$fileName?key=${Uri.encode(apiKey)}")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val statusJson = client.newCall(statusRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Gemini file status failed: HTTP ${response.code}")
                JSONObject(text)
            }

            val file = statusJson.optJSONObject("file") ?: statusJson
            val state = file.optJSONObject("state")?.optString("name") ?: file.optString("state")
            if (state.equals("ACTIVE", ignoreCase = true)) {
                val fileUri = file.optString("uri")
                if (fileUri.isNotBlank()) return UploadedFile(fileUri, file.optString("mimeType").ifBlank { mimeType })
            }
            if (state.equals("FAILED", ignoreCase = true)) throw IOException("Gemini could not process $displayName")
            delay(1000)
        }
        throw IOException("Gemini file processing timed out for $displayName")
    }

    private fun querySize(resolver: android.content.ContentResolver, uri: Uri): Long = try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use -1L
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
        } ?: -1L
    } catch (_: Exception) { -1L }
}
