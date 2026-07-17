package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Gemini API Models ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "topK") val topK: Int? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingRequest(
    @Json(name = "content") val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingResponse(
    @Json(name = "embedding") val embedding: GeminiEmbeddingValue? = null
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingValue(
    @Json(name = "values") val values: List<Float>? = null
)

// --- Local Backend (Flask) & llama.cpp Models ---

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "message") val message: String,
    @Json(name = "history") val history: List<Map<String, String>>? = null
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    @Json(name = "response") val response: String
)

@JsonClass(generateAdapter = true)
data class VoiceRequest(
    @Json(name = "audio_base64") val audioBase64: String
)

@JsonClass(generateAdapter = true)
data class VoiceResponse(
    @Json(name = "text") val text: String,
    @Json(name = "response") val response: String
)

@JsonClass(generateAdapter = true)
data class MemorySaveRequest(
    @Json(name = "content") val content: String,
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class BackendMemoryItem(
    @Json(name = "id") val id: Int,
    @Json(name = "content") val content: String,
    @Json(name = "type") val type: String,
    @Json(name = "timestamp") val timestamp: String
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String,
    @Json(name = "llama_cpp_connected") val llamaCppConnected: Boolean
)

@JsonClass(generateAdapter = true)
data class LlamaCompletionRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "n_predict") val nPredict: Int = 128,
    @Json(name = "temperature") val temperature: Float = 0.7f
)

@JsonClass(generateAdapter = true)
data class LlamaCompletionResponse(
    @Json(name = "content") val content: String
)

// --- Retrofit Interfaces ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:embedContent")
    suspend fun embedContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiEmbeddingRequest
    ): GeminiEmbeddingResponse
}

interface BabyBackendApiService {
    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("voice")
    suspend fun voice(@Body request: VoiceRequest): VoiceResponse

    @POST("memory/save")
    suspend fun saveMemory(@Body request: MemorySaveRequest): Map<String, String>

    @GET("memory")
    suspend fun getMemories(): List<BackendMemoryItem>

    @DELETE("memory/{id}")
    suspend fun deleteMemory(@Path("id") id: Int): Map<String, String>

    @GET("health")
    suspend fun checkHealth(): HealthResponse
}

interface LlamaDirectApiService {
    @POST("completion")
    suspend fun complete(@Body request: LlamaCompletionRequest): LlamaCompletionResponse
}

object ApiClients {
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    fun getBackendService(baseUrl: String): BabyBackendApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BabyBackendApiService::class.java)
    }

    fun getLlamaDirectService(baseUrl: String): LlamaDirectApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(LlamaDirectApiService::class.java)
    }
}
