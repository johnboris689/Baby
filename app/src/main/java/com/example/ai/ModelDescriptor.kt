package com.example.ai

/**
 * Metadata descriptor for an open-source, on-device language model.
 */
data class ModelDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val filename: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val quantization: String,
    val downloadUrl: String,
    val tier: HardwareTier,
    val contextTokens: Int,
    val minRamMb: Int,
    val parameterCount: String
)

/**
 * Curated catalog of open-source language models verified for Android on-device execution.
 */
object ModelCatalog {

    val SMOL_LM_135M = ModelDescriptor(
        id = "smollm2-135m",
        name = "Baby Nano (SmolLM2 135M)",
        description = "Ultra-lightweight model designed for low-memory Android phones. Fast token generation with minimal battery usage.",
        filename = "smollm2-135m-instruct-q4_k_m.gguf",
        sizeBytes = 98_000_000L,
        sizeFormatted = "~98 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
        tier = HardwareTier.LOW_END,
        contextTokens = 2048,
        minRamMb = 1024,
        parameterCount = "135 Million"
    )

    val QWEN_0_5B = ModelDescriptor(
        id = "qwen2.5-0.5b",
        name = "Baby Compact (Qwen 2.5 0.5B)",
        description = "Recommended default. High conversational quality, excellent instruction following, and broad knowledge in a compact 350MB package.",
        filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sizeBytes = 398_000_000L,
        sizeFormatted = "~398 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        tier = HardwareTier.LOW_END,
        contextTokens = 2048,
        minRamMb = 2048,
        parameterCount = "500 Million"
    )

    val LLAMA_3_2_1B = ModelDescriptor(
        id = "llama-3.2-1b",
        name = "Baby Standard (Llama 3.2 1B)",
        description = "Powerful reasoning and conversational depth from Meta's Llama 3.2 family. Ideal for mid-range and modern phones.",
        filename = "llama-3.2-1b-instruct-q4_k_m.gguf",
        sizeBytes = 780_000_000L,
        sizeFormatted = "~780 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        tier = HardwareTier.MID_RANGE,
        contextTokens = 4096,
        minRamMb = 3500,
        parameterCount = "1.23 Billion"
    )

    val QWEN_1_5B = ModelDescriptor(
        id = "qwen2.5-1.5b",
        name = "Baby Pro (Qwen 2.5 1.5B)",
        description = "Top-tier on-device reasoning and nuanced dialogue for high-spec phones with 6GB+ RAM.",
        filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 986_000_000L,
        sizeFormatted = "~986 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        tier = HardwareTier.HIGH_END,
        contextTokens = 4096,
        minRamMb = 5000,
        parameterCount = "1.54 Billion"
    )

    val allModels: List<ModelDescriptor> = listOf(
        QWEN_0_5B,
        SMOL_LM_135M,
        LLAMA_3_2_1B,
        QWEN_1_5B
    )

    fun getRecommendedModel(profile: HardwareProfile): ModelDescriptor {
        return when (profile.recommendedTier) {
            HardwareTier.LOW_END -> if (profile.totalRamMb < 2000) SMOL_LM_135M else QWEN_0_5B
            HardwareTier.MID_RANGE -> LLAMA_3_2_1B
            HardwareTier.HIGH_END -> QWEN_1_5B
        }
    }

    fun findById(id: String): ModelDescriptor {
        return allModels.find { it.id == id } ?: QWEN_0_5B
    }
}
