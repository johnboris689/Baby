package com.example.ai

/**
 * Compatibility classification of a model for the current host Android hardware.
 */
enum class DeviceCompatibility {
    RECOMMENDED,
    COMPATIBLE_HIGH_MEMORY,
    INSUFFICIENT_RAM
}

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
    val sha256Hex: String,
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
        description = "Ultra-lightweight model designed for low-memory Android devices. Fast token generation with minimal battery usage.",
        filename = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        sizeBytes = 105_454_432L,
        sizeFormatted = "~105 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
        sha256Hex = "2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d",
        tier = HardwareTier.LOW_END,
        contextTokens = 2048,
        minRamMb = 1024,
        parameterCount = "135 Million"
    )

    val QWEN_0_5B = ModelDescriptor(
        id = "qwen2.5-0.5b",
        name = "Baby Compact (Qwen 2.5 0.5B)",
        description = "Recommended for devices with 3-4 GB RAM. High conversational quality, instruction following, and broad knowledge in a compact 491 MB package.",
        filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sizeBytes = 491_400_032L,
        sizeFormatted = "~491 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sha256Hex = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        tier = HardwareTier.LOW_END,
        contextTokens = 2048,
        minRamMb = 2048,
        parameterCount = "492 Million"
    )

    val LLAMA_3_2_1B = ModelDescriptor(
        id = "llama-3.2-1b",
        name = "Baby Standard (Llama 3.2 1B)",
        description = "Advanced reasoning and conversational depth from Meta's Llama 3.2 family. Recommended for modern phones with 4.5 GB+ RAM.",
        filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        sizeBytes = 807_694_464L,
        sizeFormatted = "~808 MB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        sha256Hex = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
        tier = HardwareTier.MID_RANGE,
        contextTokens = 4096,
        minRamMb = 3500,
        parameterCount = "1.23 Billion"
    )

    val QWEN_1_5B = ModelDescriptor(
        id = "qwen2.5-1.5b",
        name = "Baby Pro (Qwen 2.5 1.5B)",
        description = "Top-tier on-device reasoning and nuanced dialogue for high-spec phones with 6 GB+ RAM.",
        filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 1_117_320_736L,
        sizeFormatted = "~1.12 GB",
        quantization = "Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sha256Hex = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        tier = HardwareTier.HIGH_END,
        contextTokens = 4096,
        minRamMb = 5000,
        parameterCount = "1.54 Billion"
    )

    val allModels: List<ModelDescriptor> = listOf(
        QWEN_0_5B,
        LLAMA_3_2_1B,
        SMOL_LM_135M,
        QWEN_1_5B
    )

    /**
     * Determines the optimal model tailored to host device total RAM and currently available free RAM.
     * Prevents Android Low Memory Killer (LMK) process termination.
     */
    fun getRecommendedModel(profile: HardwareProfile): ModelDescriptor {
        return when {
            profile.totalRamMb >= 6000 && profile.availableRamMb >= 3000 -> QWEN_1_5B
            profile.totalRamMb >= 4500 && profile.availableRamMb >= 2200 -> LLAMA_3_2_1B
            profile.totalRamMb >= 2048 && profile.availableRamMb >= 700 -> QWEN_0_5B
            else -> SMOL_LM_135M
        }
    }

    /**
     * Computes the compatibility verdict for a model against host hardware.
     */
    fun getCompatibility(model: ModelDescriptor, profile: HardwareProfile): DeviceCompatibility {
        val recommended = getRecommendedModel(profile)
        if (model.id == recommended.id) return DeviceCompatibility.RECOMMENDED
        if (profile.totalRamMb < model.minRamMb || profile.availableRamMb < (model.sizeBytes / (1024 * 1024) + 200)) {
            return DeviceCompatibility.INSUFFICIENT_RAM
        }
        return DeviceCompatibility.COMPATIBLE_HIGH_MEMORY
    }

    fun findById(id: String): ModelDescriptor {
        return allModels.find { it.id == id } ?: QWEN_0_5B
    }
}
