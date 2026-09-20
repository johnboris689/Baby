package com.example.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Hardware tier classifications for Android on-device AI inference.
 */
enum class HardwareTier(val displayName: String, val description: String) {
    LOW_END("Low-End (Lightweight)", "Devices with under 3GB RAM. Uses ultra-compact quantized models."),
    MID_RANGE("Mid-Range (Balanced)", "Devices with 3GB to 6GB RAM. Uses balanced 0.5B-1B parameter models."),
    HIGH_END("High-End (Full Power)", "Devices with over 6GB RAM. Can run larger 1.5B-3B parameter models.")
}

/**
 * Snapshot of host device hardware resources relevant to local AI inference.
 */
data class HardwareProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val cpuCores: Int,
    val supportedAbis: List<String>,
    val freeStorageMb: Long,
    val androidVersion: Int,
    val recommendedTier: HardwareTier
) {
    val is64Bit: Boolean
        get() = supportedAbis.any { it.contains("64") }

    val formattedRam: String
        get() = "%.1f GB (%.1f GB free)".format(totalRamMb / 1024.0, availableRamMb / 1024.0)

    val formattedStorage: String
        get() = "%.1f GB free".format(freeStorageMb / 1024.0)
}

/**
 * Helper to inspect the Android device's hardware specs.
 */
object HardwareDetector {

    fun detect(context: Context): HardwareProfile {
        // 1. RAM Detection via ActivityManager
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        // 2. CPU Cores
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        // 3. Supported ABIs
        val abis = Build.SUPPORTED_ABIS.toList()

        // 4. Free Storage in App-Private Files Directory
        val freeStorageMb = try {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) {
            1024L
        }

        // 5. Determine Recommended Tier
        val tier = when {
            totalRamMb < 3200 -> HardwareTier.LOW_END
            totalRamMb in 3200..6144 -> HardwareTier.MID_RANGE
            else -> HardwareTier.HIGH_END
        }

        return HardwareProfile(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            cpuCores = cpuCores,
            supportedAbis = abis,
            freeStorageMb = freeStorageMb,
            androidVersion = Build.VERSION.SDK_INT,
            recommendedTier = tier
        )
    }
}
