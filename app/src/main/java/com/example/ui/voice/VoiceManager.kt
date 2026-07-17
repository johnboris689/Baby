package com.example.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File

class VoiceManager(
    private val context: Context,
    private val onPartialSpeech: (String) -> Unit,
    private val onFinalSpeech: (String) -> Unit,
    private val onErrorSpeech: (String) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onTtsStart: () -> Unit,
    private val onTtsDone: () -> Unit,
    private val onTtsInitialized: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isListening = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val silenceTimeoutRunnable = Runnable {
        Log.d("VoiceManager", "Backup silence timeout triggered.")
        stopListening()
    }
    private val SILENCE_TIMEOUT_MS = 3000L
    private val RMS_THRESHOLD = 2.0f // Initial baseline fallback threshold

    // User-controlled or dynamic amplitude threshold for silence detection
    var amplitudeThreshold: Float = 2.0f

    // Custom Selected Voice name
    var selectedVoiceName: String? = null
        set(value) {
            field = value
            applyVoice()
        }

    private fun applyVoice() {
        if (!isTtsInitialized) return
        val tts = textToSpeech ?: return
        selectedVoiceName?.let { name ->
            try {
                val voiceOpt = tts.voices?.firstOrNull { it.name == name }
                if (voiceOpt != null) {
                    tts.voice = voiceOpt
                    Log.d("VoiceManager", "Successfully set TTS voice to: $name")
                } else {
                    Log.w("VoiceManager", "Voice not found or not loaded yet: $name")
                }
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error setting voice: ${e.message}")
            }
        }
    }

    fun getAvailableVoiceNames(): List<String> {
        if (!isTtsInitialized) return emptyList()
        return try {
            val tts = textToSpeech ?: return emptyList()
            val defaultLocale = Locale.getDefault()
            val voices = tts.voices?.toList() ?: emptyList()
            
            // Filter voices matching active locale language or English as a standard fallback
            val filteredVoices = voices.filter { voice ->
                voice.locale.language == defaultLocale.language || voice.locale.language == "en"
            }
            val voiceList = if (filteredVoices.isNotEmpty()) filteredVoices else voices
            voiceList.map { it.name }.distinct().sorted()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error getting available voice names: ${e.message}")
            emptyList()
        }
    }

    // Power save state support
    var isPowerSaveMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                Log.d("VoiceManager", "Power-save mode updated in VoiceManager: $value")
                updateTaskFrequencies()
            }
        }

    private val currentAmplitudeCheckInterval: Long
        get() = if (isPowerSaveMode) 300L else 100L

    private val currentCacheCleanupInterval: Long
        get() = if (isPowerSaveMode) 900000L else 300000L // 15 mins vs 5 mins

    private fun updateTaskFrequencies() {
        if (isListening) {
            stopAmplitudeMonitoring()
            startAmplitudeMonitoring()
        }
        stopPeriodicCacheCleanup()
        startPeriodicCacheCleanup()
    }

    // Real-time amplitude threshold monitor & dynamic noise calibration fields
    private var lastActiveTimeMs = 0L
    private var currentEmaRms = -100f
    private val EMA_ALPHA = 0.25f // Smoothing factor for transient noise filtering
    private var dynamicThreshold = 2.0f
    private var noiseFloorSum = 0f
    private var noiseSampleCount = 0

    private val amplitudeMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isListening) return
            val now = System.currentTimeMillis()
            val silenceDuration = now - lastActiveTimeMs
            if (silenceDuration >= SILENCE_TIMEOUT_MS) {
                Log.d("VoiceManager", "Real-time amplitude monitor auto-stop triggered. Silence duration: ${silenceDuration}ms (threshold: $amplitudeThreshold dB)")
                stopListening()
            } else {
                mainHandler.postDelayed(this, currentAmplitudeCheckInterval)
            }
        }
    }

    private fun startAmplitudeMonitoring() {
        mainHandler.removeCallbacks(amplitudeMonitorRunnable)
        mainHandler.postDelayed(amplitudeMonitorRunnable, currentAmplitudeCheckInterval)
    }

    private fun stopAmplitudeMonitoring() {
        mainHandler.removeCallbacks(amplitudeMonitorRunnable)
    }

    private fun resetSilenceTimer() {
        mainHandler.removeCallbacks(silenceTimeoutRunnable)
        mainHandler.postDelayed(silenceTimeoutRunnable, SILENCE_TIMEOUT_MS)
    }

    private fun cancelSilenceTimer() {
        mainHandler.removeCallbacks(silenceTimeoutRunnable)
        stopAmplitudeMonitoring()
    }

    // --- Automated Storage / Cache Clearing Task ---
    private val cacheCleanupRunnable = object : Runnable {
        override fun run() {
            clearSpeechCache()
            mainHandler.postDelayed(this, currentCacheCleanupInterval)
        }
    }

    private fun startPeriodicCacheCleanup() {
        mainHandler.removeCallbacks(cacheCleanupRunnable)
        mainHandler.postDelayed(cacheCleanupRunnable, currentCacheCleanupInterval)
    }

    private fun stopPeriodicCacheCleanup() {
        mainHandler.removeCallbacks(cacheCleanupRunnable)
    }

    fun clearSpeechCache() {
        try {
            var deletedBytes = 0L
            val cacheDirs = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                context.codeCacheDir
            )
            for (dir in cacheDirs) {
                deletedBytes += deleteDirContents(dir)
            }
            if (deletedBytes > 0) {
                Log.d("VoiceManager", "Cleaned up temporary voice buffer/speech cache. Freed: ${deletedBytes / 1024} KB")
            } else {
                Log.d("VoiceManager", "Voice speech cache is already clean.")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error clearing speech cache: ${e.message}")
        }
    }

    private fun deleteDirContents(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        var bytesFreed = 0L
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    bytesFreed += getFolderSize(child)
                    if (child.isDirectory) {
                        bytesFreed += deleteDirContents(child)
                    }
                    try {
                        child.delete()
                    } catch (e: Exception) {
                        Log.e("VoiceManager", "Failed to delete cache file: ${child.absolutePath}")
                    }
                }
            }
        }
        return bytesFreed
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                size += getFolderSize(child)
            }
        }
        return size
    }

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
        startPeriodicCacheCleanup()
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onListeningStateChanged(true)
                            lastActiveTimeMs = System.currentTimeMillis()
                            resetSilenceTimer()
                            startAmplitudeMonitoring()
                        }

                        override fun onBeginningOfSpeech() {
                            lastActiveTimeMs = System.currentTimeMillis()
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            this@VoiceManager.onRmsChanged(rmsdB)
                            
                            // 1. Dynamic calibration of background noise floor (first 8 samples)
                            if (noiseSampleCount < 8) {
                                noiseFloorSum += rmsdB
                                noiseSampleCount++
                                if (noiseSampleCount == 8) {
                                    val estimatedNoiseFloor = noiseFloorSum / 8f
                                    // Adaptive threshold of noise floor + 2.5 dB, clamped to a reasonable range
                                    dynamicThreshold = (estimatedNoiseFloor + 2.5f).coerceIn(1.5f, 4.0f)
                                    Log.d("VoiceManager", "Dynamic threshold calibrated: $dynamicThreshold dB (estimated noise floor: $estimatedNoiseFloor dB)")
                                }
                            }

                            // 2. Exponential Moving Average (EMA) smoothing to prevent transient dips (e.g. short breaths) from triggering auto-stop
                            currentEmaRms = if (currentEmaRms == -100f) {
                                rmsdB
                            } else {
                                EMA_ALPHA * rmsdB + (1f - EMA_ALPHA) * currentEmaRms
                            }

                            // 3. Keep-alive: Reset active timestamp if energy exceeds customized threshold
                            if (currentEmaRms > amplitudeThreshold) {
                                lastActiveTimeMs = System.currentTimeMillis()
                                resetSilenceTimer()
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            onListeningStateChanged(false)
                            cancelSilenceTimer()
                        }

                        override fun onError(error: Int) {
                            cancelSilenceTimer()
                            val errorMessage = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                                SpeechRecognizer.ERROR_SERVER -> "Server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                                else -> "Unknown speech error"
                            }
                            Log.e("VoiceManager", "Speech recognition error: $errorMessage")
                            onErrorSpeech(errorMessage)
                            onListeningStateChanged(false)
                        }

                        override fun onResults(results: Bundle?) {
                            cancelSilenceTimer()
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onFinalSpeech(matches[0])
                            }
                            onListeningStateChanged(false)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onPartialSpeech(matches[0])
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } else {
                Log.e("VoiceManager", "Speech recognition is not available on this device")
                onErrorSpeech("Speech recognition not available")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error initializing speech recognizer: ${e.message}")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceManager", "Default language is not supported or missing data")
                    tts.language = Locale.US
                }
                isTtsInitialized = true
                applyVoice()
                
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onTtsStart()
                    }

                    override fun onDone(utteranceId: String?) {
                        onTtsDone()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onTtsDone()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onTtsDone()
                    }
                })
                onTtsInitialized()
            }
        } else {
            Log.e("VoiceManager", "Initialization of TextToSpeech failed")
        }
    }

    fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onErrorSpeech("Microphone permission not granted")
            return
        }
        speechRecognizer?.let { recognizer ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                stopSpeaking() // Interrupt speaking if active
                
                // Initialize/reset real-time monitor parameters
                lastActiveTimeMs = System.currentTimeMillis()
                currentEmaRms = -100f
                dynamicThreshold = 2.0f
                noiseFloorSum = 0f
                noiseSampleCount = 0

                recognizer.startListening(intent)
                isListening = true
                resetSilenceTimer()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to start listening: ${e.message}")
                onErrorSpeech("Failed to start speech recognition")
            }
        } ?: run {
            onErrorSpeech("Speech recognizer not ready")
        }
    }

    fun stopListening() {
        if (!isListening) return
        cancelSilenceTimer()
        try {
            speechRecognizer?.stopListening()
            isListening = false
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to stop listening: ${e.message}")
        }
    }

    fun cancelListening() {
        if (!isListening) return
        cancelSilenceTimer()
        try {
            speechRecognizer?.cancel()
            isListening = false
            onListeningStateChanged(false)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to cancel listening: ${e.message}")
        }
    }

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isTtsInitialized) {
            Log.e("VoiceManager", "TextToSpeech is not initialized yet")
            return
        }
        textToSpeech?.let { tts ->
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "baby_utterance_id")
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "baby_utterance_id")
        }
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            textToSpeech?.stop()
        }
    }

    fun destroy() {
        cancelSilenceTimer()
        stopPeriodicCacheCleanup()
        clearSpeechCache()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error destroying speech recognizer: ${e.message}")
        }
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error shutting down text to speech: ${e.message}")
        }
    }
}
