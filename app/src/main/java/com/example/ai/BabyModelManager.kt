package com.example.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

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
    val freeStorageMb: Long = 0L,
    val verificationDetails: String? = null
)

class BabyModelManager(
    private val context: Context
) {
    private val tag = "BabyModelManager"
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    // Dedicated background I/O scope — NEVER executes network or heavy file I/O on the main thread
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val hardwareProfile = HardwareDetector.detect(context)
    var activeModelDescriptor: ModelDescriptor = ModelCatalog.getRecommendedModel(hardwareProfile)
        private set

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

    // High-performance OkHttpClient with transparent redirects, TLS 1.3, and robust timeout handling
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Callback invoked when a model is verified and ready for engine loading
    var onModelInstalledListener: ((File) -> Unit)? = null

    init {
        // Run initial check on background I/O thread
        ioScope.launch {
            migrateOldTempFiles()
            checkInstalledModels()
        }
    }

    /**
     * Migrates any legacy .download files to standard .part format to preserve previously downloaded bytes.
     */
    private fun migrateOldTempFiles() {
        try {
            modelsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".download")) {
                    val partFile = File(file.parentFile, file.name.removeSuffix(".download") + ".part")
                    if (!partFile.exists()) {
                        file.renameTo(partFile)
                        Log.d(tag, "Migrated legacy download file: ${file.name} -> ${partFile.name}")
                    } else {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Temp file migration notice: ${e.message}")
        }
    }

    /**
     * Inspects the app-private models directory to see if a valid model file already exists.
     */
    fun checkInstalledModels(): Boolean {
        refreshStorageInfo()

        // 1. Check if active model file exists and is valid
        val targetFile = File(modelsDir, activeModelDescriptor.filename)
        if (isValidGgufFile(targetFile)) {
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.READY,
                currentModel = activeModelDescriptor,
                installedFile = targetFile,
                isReady = true,
                errorMessage = null,
                verificationDetails = "GGUF Binary Verified (${targetFile.length() / (1024 * 1024)} MB)"
            )
            Log.d(tag, "Active model is installed and verified: ${targetFile.absolutePath}")
            return true
        }

        // 2. Check if any other known model file exists
        for (model in ModelCatalog.allModels) {
            val file = File(modelsDir, model.filename)
            if (isValidGgufFile(file)) {
                activeModelDescriptor = model
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.READY,
                    currentModel = model,
                    installedFile = file,
                    isReady = true,
                    errorMessage = null,
                    verificationDetails = "GGUF Binary Verified (${file.length() / (1024 * 1024)} MB)"
                )
                Log.d(tag, "Found alternative installed model: ${file.name}")
                return true
            }
        }

        // 3. No installed GGUF model exists
        _modelStatus.value = _modelStatus.value.copy(
            state = ModelInstallationState.NOT_INSTALLED,
            currentModel = activeModelDescriptor,
            installedFile = null,
            isReady = false,
            errorMessage = null,
            verificationDetails = null
        )
        return false
    }

    fun isValidGgufFile(file: File): Boolean {
        if (!file.exists() || file.length() < 10_000_000L) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                String(magic, Charsets.US_ASCII) == "GGUF"
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sets the preferred model descriptor.
     */
    fun selectModel(model: ModelDescriptor) {
        if (_modelStatus.value.state == ModelInstallationState.DOWNLOADING) {
            cancelDownload()
        }
        activeModelDescriptor = model
        ioScope.launch {
            checkInstalledModels()
        }
    }

    /**
     * Downloads the selected model into app-private storage using OkHttp with resume support,
     * SHA-256 / GGUF magic header verification, and comprehensive diagnostic reporting.
     * Guaranteed to execute 100% on Dispatchers.IO to prevent NetworkOnMainThreadException.
     */
    fun startDownload(onCompleted: ((Boolean) -> Unit)? = null) {
        if (_modelStatus.value.state == ModelInstallationState.DOWNLOADING) return

        refreshStorageInfo()

        val targetFile = File(modelsDir, activeModelDescriptor.filename)
        val partFile = File(modelsDir, "${activeModelDescriptor.filename}.part")

        // 1. If complete valid model file already exists, do not re-download
        if (isValidGgufFile(targetFile)) {
            Log.d(tag, "Model already fully downloaded and valid: ${targetFile.absolutePath}")
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.READY,
                installedFile = targetFile,
                downloadProgress = 1f,
                downloadSpeed = "Installed",
                isReady = true,
                errorMessage = null,
                verificationDetails = "GGUF Binary Verified (${targetFile.length() / (1024 * 1024)} MB)"
            )
            onModelInstalledListener?.invoke(targetFile)
            ioScope.launch(Dispatchers.Main) { onCompleted?.invoke(true) }
            return
        }

        // 2. Storage check
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val remainingBytes = (activeModelDescriptor.sizeBytes - existingBytes).coerceAtLeast(0L)
        val requiredMb = (remainingBytes / (1024 * 1024)) + 100L
        if (_modelStatus.value.freeStorageMb < requiredMb) {
            val error = "Not enough storage: ${activeModelDescriptor.name} requires ~${requiredMb} MB free space, but only ${_modelStatus.value.freeStorageMb} MB is free on device."
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.ERROR,
                errorMessage = error
            )
            ioScope.launch(Dispatchers.Main) { onCompleted?.invoke(false) }
            return
        }

        // 3. Launch download strictly on Dispatchers.IO
        downloadJob = ioScope.launch(Dispatchers.IO) {
            _modelStatus.value = _modelStatus.value.copy(
                state = ModelInstallationState.DOWNLOADING,
                downloadProgress = if (existingBytes > 0 && activeModelDescriptor.sizeBytes > 0) {
                    (existingBytes.toFloat() / activeModelDescriptor.sizeBytes.toFloat()).coerceIn(0f, 0.95f)
                } else 0f,
                downloadSpeed = if (existingBytes > 0) "Resuming from ${existingBytes / (1024 * 1024)} MB..." else "Connecting...",
                downloadedBytes = existingBytes,
                totalBytes = activeModelDescriptor.sizeBytes,
                errorMessage = null,
                verificationDetails = null
            )

            try {
                val requestBuilder = Request.Builder()
                    .url(activeModelDescriptor.downloadUrl)
                    .header("User-Agent", "Baby-Android-Assistant/1.0 (Linux; Android ${hardwareProfile.androidVersion})")

                // Support resumable downloads via HTTP Range header
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    Log.d(tag, "Attempting resumable download from byte $existingBytes")
                }

                // Explicit network execution on IO thread
                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                val code = response.code
                if (code == 416) {
                    // Range Not Satisfiable: file might already be complete or invalid range. Clear and re-download.
                    response.close()
                    partFile.delete()
                    val retryResponse = okHttpClient.newCall(
                        Request.Builder()
                            .url(activeModelDescriptor.downloadUrl)
                            .header("User-Agent", "Baby-Android-Assistant/1.0")
                            .build()
                    ).execute()
                    handleDownloadStream(retryResponse, partFile, targetFile, 0L, onCompleted)
                    return@launch
                }

                if (!response.isSuccessful) {
                    val statusMsg = when (code) {
                        401 -> "HTTP 401 Unauthorized: Remote repository requires an authentication token"
                        403 -> "HTTP 403 Forbidden: Access denied by remote host"
                        404 -> "HTTP 404 Not Found: Model file not found at ${activeModelDescriptor.downloadUrl}"
                        429 -> "HTTP 429 Too Many Requests: Rate limited by server. Please try again shortly."
                        in 500..599 -> "HTTP $code Server Error: The remote server encountered an internal failure"
                        else -> "HTTP $code: ${response.message.ifBlank { "Unexpected server response" }}"
                    }
                    val isHtml = response.body?.contentType()?.toString()?.contains("text/html") == true
                    val fullErr = if (isHtml) "$statusMsg (Server returned an HTML error/login page instead of binary GGUF)" else statusMsg
                    response.close()
                    throw IOException(fullErr)
                }

                val isPartial = (code == 206)
                val streamStartOffset = if (isPartial) existingBytes else 0L
                handleDownloadStream(response, partFile, targetFile, streamStartOffset, onCompleted)

            } catch (e: CancellationException) {
                // Keep partial file for subsequent resume
                Log.d(tag, "Download was paused/cancelled by user; part file retained for resume (${partFile.length()} bytes)")
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.NOT_INSTALLED,
                    downloadSpeed = "Paused",
                    errorMessage = "Download paused by user"
                )
                withContext(Dispatchers.Main) { onCompleted?.invoke(false) }
            } catch (e: Throwable) {
                val diagnostic = formatDiagnosticError(e)
                Log.e(tag, "Download failed: $diagnostic", e)
                _modelStatus.value = _modelStatus.value.copy(
                    state = ModelInstallationState.ERROR,
                    downloadProgress = 0f,
                    downloadSpeed = "",
                    errorMessage = diagnostic
                )
                withContext(Dispatchers.Main) { onCompleted?.invoke(false) }
            }
        }
    }

    private suspend fun handleDownloadStream(
        response: okhttp3.Response,
        partFile: File,
        targetFile: File,
        startOffset: Long,
        onCompleted: ((Boolean) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        val body = response.body ?: throw IOException("HTTP response body was empty")
        val isAppend = (startOffset > 0L)
        val bodyLength = body.contentLength()
        val totalExpected = if (bodyLength > 0) startOffset + bodyLength else activeModelDescriptor.sizeBytes

        _modelStatus.value = _modelStatus.value.copy(totalBytes = totalExpected)

        var totalRead = startOffset
        var lastUpdateTime = System.currentTimeMillis()
        var bytesSinceUpdate = 0L

        response.use {
            body.byteStream().use { input ->
                FileOutputStream(partFile, isAppend).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("Download cancelled")
                        }

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        bytesSinceUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdateTime
                        if (elapsed >= 400) {
                            val speedKbs = (bytesSinceUpdate * 1000.0) / (elapsed * 1024.0)
                            val speedText = if (speedKbs > 1024) {
                                "%.1f MB/s".format(speedKbs / 1024.0)
                            } else {
                                "%.0f KB/s".format(speedKbs)
                            }
                            val progress = (totalRead.toFloat() / totalExpected.toFloat()).coerceIn(0f, 0.99f)

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
        }

        // Verification phase (all on Dispatchers.IO)
        _modelStatus.value = _modelStatus.value.copy(
            state = ModelInstallationState.VERIFYING,
            downloadSpeed = "Verifying GGUF binary..."
        )

        // 1. File size check
        if (partFile.length() < 10_000_000L) {
            partFile.delete()
            throw IOException("Downloaded file is smaller than expected (${partFile.length()} bytes)")
        }

        // 2. GGUF Magic Header check
        RandomAccessFile(partFile, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            val magicStr = String(magic, Charsets.US_ASCII)
            if (magicStr != "GGUF") {
                partFile.delete()
                throw IllegalArgumentException(
                    "Downloaded file is not a valid GGUF model: Expected 'GGUF' header magic, but received '$magicStr'. The server may have returned an HTML error page."
                )
            }
        }

        // 3. SHA-256 Checksum validation if available
        if (activeModelDescriptor.sha256Hex.isNotBlank()) {
            _modelStatus.value = _modelStatus.value.copy(downloadSpeed = "Verifying SHA-256 integrity...")
            val computedHash = computeSha256(partFile)
            if (!computedHash.equals(activeModelDescriptor.sha256Hex, ignoreCase = true)) {
                partFile.delete()
                throw SecurityException("Checksum verification failed: Downloaded file hash did not match official model checksum.")
            }
            Log.d(tag, "SHA-256 checksum verified: $computedHash")
        }

        // 4. Atomic rename to target model file (.part -> .gguf)
        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }

        Log.d(tag, "Model binary verified and saved: ${targetFile.absolutePath} (${targetFile.length()} bytes)")

        // 5. Notify engine to load the newly installed model
        onModelInstalledListener?.invoke(targetFile)

        _modelStatus.value = _modelStatus.value.copy(
            state = ModelInstallationState.READY,
            installedFile = targetFile,
            downloadProgress = 1f,
            downloadSpeed = "Verified",
            isReady = true,
            errorMessage = null,
            verificationDetails = "GGUF Verified: ${targetFile.name} (${targetFile.length() / (1024 * 1024)} MB)"
        )

        withContext(Dispatchers.Main) { onCompleted?.invoke(true) }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        file.inputStream().use { input ->
            var bytes: Int
            while (input.read(buffer).also { bytes = it } != -1) {
                digest.update(buffer, 0, bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatDiagnosticError(e: Throwable): String {
        return when (e) {
            is UnknownHostException -> "Unable to connect to model server (DNS resolution failed for '${e.message ?: "huggingface.co"}'). Check your internet connection."
            is SocketTimeoutException -> "Download timed out: Server took too long to respond. Partial download is saved and will resume."
            is ConnectException -> "Unable to connect to model server (Connection refused)."
            is SSLException -> "TLS/SSL security failure: Secure connection could not be established (${e.message ?: "handshake error"})."
            is ProtocolException -> "Network protocol violation: ${e.message ?: "Protocol error"}"
            is IllegalArgumentException -> e.message ?: "Downloaded file is not a valid GGUF model."
            is SecurityException -> e.message ?: "Checksum verification failed."
            is IOException -> {
                val msg = e.message
                if (!msg.isNullOrBlank() && msg != "null") msg else "Network connection was interrupted."
            }
            else -> {
                val msg = e.message ?: e.localizedMessage
                if (!msg.isNullOrBlank() && msg != "null") {
                    msg
                } else {
                    "Download error: ${e.javaClass.simpleName}"
                }
            }
        }
    }

    /**
     * Cancels an active download.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _modelStatus.value = _modelStatus.value.copy(
            state = if (_modelStatus.value.installedFile?.exists() == true) ModelInstallationState.READY else ModelInstallationState.NOT_INSTALLED,
            downloadSpeed = "Paused",
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
            if (file.name.endsWith(".gguf") || file.name.endsWith(".part") || file.name.endsWith(".download")) {
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
