package com.example.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ModelInstallationState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    READY,
    ERROR
}

data class ModelStatus(
    val state: ModelInstallationState,
    val currentModel: ModelDescriptor,
    val installedFile: File?,
    val downloadProgress: Float = 0f,
    val downloadSpeed: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val isReady: Boolean = false,
    val freeStorageMb: Long = 0L
)

class BabyModelManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val tag = "BabyModelManager"
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val hardwareProfile = HardwareDetector.detect(context)
    private var activeModelDescriptor: ModelDescriptor = ModelCatalog.getRecommendedModel(hardwareProfile)

    private val _modelStatus = MutableStateFlow(
        ModelStatus(
            state = ModelInstallationState.NOT_INSTALLED,
            currentModel = activeModelDescriptor,
            installedFile = null,
            freeStorageMb = hardwareProfile.freeStorageMb
        )
    )
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private var downloadJob: Job? = null

    init {
        checkInstalledModels()
    }

    /**
     * Inspects the app-private models directory to see if a valid model file already exists.
     */
    fun checkInstalledModels(): Boolean {
        refreshStorageInfo()
        val candidateFiles = modelsDir.listFiles { _, name -> name.endsWith(".gguf") } ?: emptyArray()

        // 1. Check if active model file exists
        val targetFile = File(modelsDir, activeModelDescriptor.filename)
        if (targetFile.exists() && targetFile.length() > 1_000_000L) {
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.READY,
                installedFile = targetFile,
                isReady = true,
                errorMessage = null
            )
            Log.d(tag, "Active model is installed and ready: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return true
        }

        // 2. Check if any other known model file exists
        for (model in ModelCatalog.allModels) {
            val file = File(modelsDir, model.filename)
            if (file.exists() && file.length() > 1_000_000L) {
                activeModelDescriptor = model
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.READY,
                    currentModel = model,
                    installedFile = file,
                    isReady = true,
                    errorMessage = null
                )
                Log.d(tag, "Found alternative installed model: ${file.name}")
                return true
            }
        }

        // 3. No large GGUF file found; local embedded engine handles requests smoothly
        _modelStatus.value = _modelStatus.value.copy(
            state = ModelInstallationState.NOT_INSTALLED,
            installedFile = null,
            isReady = false
        )
        return false
    }

    /**
     * Sets the preferred model descriptor.
     */
    fun selectModel(model: ModelDescriptor) {
        if (_modelStatus.value.state == ModelInstallationState.DOWNLOADING) {
            cancelDownload()
        }
        activeModelDescriptor = model
        checkInstalledModels()
    }

    /**
     * Downloads the selected model into app-private storage.
     */
    fun startDownload(onCompleted: ((Boolean) -> Unit)? = null) {
        if (_modelStatus.value.state == ModelInstallationState.DOWNLOADING) return

        refreshStorageInfo()
        val requiredMb = (activeModelDescriptor.sizeBytes / (1024 * 1024)) + 150L
        if (_modelStatus.value.freeStorageMb < requiredMb) {
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.ERROR,
                errorMessage = "Insufficient storage. Model requires ~${requiredMb} MB, but only ${_modelStatus.value.freeStorageMb} MB is free."
            )
            onCompleted?.invoke(false)
            return
        }

        val targetFile = File(modelsDir, activeModelDescriptor.filename)
        val tempFile = File(modelsDir, "${activeModelDescriptor.filename}.download")

        downloadJob = coroutineScope.launch {
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.DOWNLOADING,
                downloadProgress = 0f,
                downloadSpeed = "Connecting...",
                downloadedBytes = 0L,
                totalBytes = activeModelDescriptor.sizeBytes,
                errorMessage = null
            )

            var connection: HttpURLConnection? = null
            try {
                val url = URL(activeModelDescriptor.downloadUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Baby-Android-Assistant/1.0")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw Exception("Server returned HTTP $responseCode: ${connection.responseMessage}")
                }

                val contentLength = connection.contentLengthLong.let {
                    if (it > 0) it else activeModelDescriptor.sizeBytes
                }

                _modelStatus.value = _modelStatus.value.copy(totalBytes = contentLength)

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastUpdateTime = System.currentTimeMillis()
                        var bytesSinceUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled by user")

                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            bytesSinceUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastUpdateTime
                            if (elapsed >= 500) {
                                val speedKbs = (bytesSinceUpdate * 1000.0) / (elapsed * 1024.0)
                                val speedText = if (speedKbs > 1024) {
                                    "%.1f MB/s".format(speedKbs / 1024.0)
                                } else {
                                    "%.0f KB/s".format(speedKbs)
                                }
                                val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.99f)

                                _modelStatus.value = _modelStatus.value.copy(
                                    downloadProgress = progress,
                                    downloadSpeed = speedText,
                                    downloadedBytes = totalRead
                                )
                                lastUpdateTime = now
                                bytesSinceUpdate = 0L
                            }
                        }
                    }
                }

                // Verification step
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.VERIFYING,
                    downloadSpeed = "Verifying..."
                )

                if (tempFile.length() < 100_000L) {
                    throw Exception("Downloaded model file is incomplete (${tempFile.length()} bytes)")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.READY,
                    installedFile = targetFile,
                    downloadProgress = 1f,
                    downloadSpeed = "Complete",
                    isReady = true,
                    errorMessage = null
                )
                Log.d(tag, "Model successfully installed: ${targetFile.absolutePath}")
                withContext(Dispatchers.Main) { onCompleted?.invoke(true) }

            } catch (e: CancellationException) {
                tempFile.delete()
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.NOT_INSTALLED,
                    downloadProgress = 0f,
                    downloadSpeed = "",
                    errorMessage = "Download cancelled"
                )
                withContext(Dispatchers.Main) { onCompleted?.invoke(false) }
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(tag, "Failed to download model: ${e.message}", e)
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.ERROR,
                    downloadProgress = 0f,
                    downloadSpeed = "",
                    errorMessage = "Download failed: ${e.localizedMessage ?: e.message}"
                )
                withContext(Dispatchers.Main) { onCompleted?.invoke(false) }
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Cancels an active download.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val tempFile = File(modelsDir, "${activeModelDescriptor.filename}.download")
        if (tempFile.exists()) tempFile.delete()
        _modelStatus.value = _modelStatus.value.copy(
            state = if (_modelStatus.value.installedFile?.exists() == true) ModelInstallationState.READY else ModelInstallationState.NOT_INSTALLED,
            downloadSpeed = "",
            downloadProgress = 0f
        )
    }

    /**
     * Deletes the installed model to reclaim storage.
     */
    fun deleteInstalledModel(): Boolean {
        cancelDownload()
        var deletedAny = false
        modelsDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".gguf") || file.name.endsWith(".download")) {
                if (file.delete()) deletedAny = true
            }
        }
        checkInstalledModels()
        return deletedAny
    }

    fun getInstalledModelFile(): File? {
        val file = _modelStatus.value.installedFile
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    private fun refreshStorageInfo() {
        val freeMb = try {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) {
            1024L
        }
        _modelStatus.value = _modelStatus.value.copy(freeStorageMb = freeMb)
    }
}
