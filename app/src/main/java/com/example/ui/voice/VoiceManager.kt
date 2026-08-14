package com.example.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Manual voice input owns the microphone exclusively. Background wake-word listening
 * is paused before a session and resumed only after the recognizer has completely ended.
 * Speech timeout/no-match errors are retried instead of immediately closing the UI.
 */
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

    private val tag = "BabyVoice"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var retryCount = 0
    private var sessionStartedAt = 0L
    private var lastPartialAt = 0L
    private var shuttingDown = false

    private val MAX_SESSION_MS = 90_000L
    private val MAX_RETRIES = 5
    private val RETRY_DELAY_MS = 280L

    private val sessionGuard = Runnable {
        if (listening && System.currentTimeMillis() - sessionStartedAt >= MAX_SESSION_MS) {
            Log.d(tag, "Manual voice session reached safety limit")
            finishListening("Voice session timed out. Tap the mic to try again.", resume = true)
        }
    }

    private val cacheCleanup = object : Runnable {
        override fun run() {
            clearSpeechCache()
            if (!shuttingDown) mainHandler.postDelayed(this, if (isPowerSaveMode) 900_000L else 300_000L)
        }
    }

    var amplitudeThreshold: Float = 0f
    var isPowerSaveMode: Boolean = false
        set(value) {
            field = value
            mainHandler.removeCallbacks(cacheCleanup)
            mainHandler.postDelayed(cacheCleanup, if (value) 900_000L else 300_000L)
        }

    var selectedVoiceName: String? = null
        set(value) {
            field = value
            applyVoice()
        }

    init {
        initializeTextToSpeech()
        mainHandler.postDelayed(cacheCleanup, 300_000L)
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return try {
            // The normal Android recognition service is the most compatible path on OEM devices.
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(tag, "Standard recognizer unavailable: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            } else null
        }
    }

    private fun configureRecognizer() {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                onListeningStateChanged(true)
                lastPartialAt = System.currentTimeMillis()
            }

            override fun onBeginningOfSpeech() {
                lastPartialAt = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged(rmsdB)
                lastPartialAt = System.currentTimeMillis()
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                // Do NOT flip the UI back to idle here. Android often delivers onResults shortly after onEndOfSpeech.
                lastPartialAt = System.currentTimeMillis()
            }

            override fun onError(error: Int) {
                Log.w(tag, "SpeechRecognizer error=$error retry=$retryCount")
                val retryable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

                if (listening && retryable && retryCount < MAX_RETRIES && !shuttingDown) {
                    retryCount++
                    mainHandler.postDelayed({ restartListeningAfterError() }, RETRY_DELAY_MS)
                    return
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be opened."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service could not be reached."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input was busy. Please try again."
                    else -> "Voice input stopped unexpectedly."
                }
                finishListening(message, resume = true)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        mainHandler.postDelayed({ restartListeningAfterError() }, RETRY_DELAY_MS)
                    } else {
                        finishListening("I didn't catch that. Please try again.", resume = true)
                    }
                    return
                }

                finishListening(null, resume = true)
                onFinalSpeech(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    lastPartialAt = System.currentTimeMillis()
                    onPartialSpeech(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun startListening() {
        if (shuttingDown || listening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onErrorSpeech("Microphone permission is required.")
            return
        }

        pauseBackgroundVoice()
        retryCount = 0
        sessionStartedAt = System.currentTimeMillis()
        startRecognizerSession()
    }

    private fun startRecognizerSession() {
        if (shuttingDown) return
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) { }
        recognizer = createRecognizer()
        if (recognizer == null) {
            finishListening("Speech recognition is not available on this phone.", resume = true)
            return
        }
        configureRecognizer()

        val language = if (Locale.getDefault().language == "en") "en-US" else Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        try {
            recognizer?.startListening(intent)
            listening = true
            onListeningStateChanged(true)
            mainHandler.removeCallbacks(sessionGuard)
            mainHandler.postDelayed(sessionGuard, MAX_SESSION_MS)
        } catch (e: Exception) {
            Log.e(tag, "Unable to start recognizer", e)
            finishListening("I couldn't start the microphone. Please try again.", resume = true)
        }
    }

    private fun restartListeningAfterError() {
        if (shuttingDown || !listening) return
        if (System.currentTimeMillis() - sessionStartedAt >= MAX_SESSION_MS) {
            finishListening("Voice session timed out. Please try again.", resume = true)
            return
        }
        startRecognizerSession()
    }

    fun stopListening() {
        if (!listening) return
        finishListening(null, resume = true)
    }

    fun cancelListening() {
        retryCount = MAX_RETRIES + 1
        finishListening(null, resume = true)
    }

    private fun finishListening(errorMessage: String?, resume: Boolean) {
        val wasListening = listening
        listening = false
        mainHandler.removeCallbacks(sessionGuard)
        try { recognizer?.cancel() } catch (_: Exception) { }
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        onListeningStateChanged(false)
        onPartialSpeech("")
        if (resume && wasListening) resumeBackgroundVoice()
        if (!errorMessage.isNullOrBlank()) onErrorSpeech(errorMessage)
    }

    private fun pauseBackgroundVoice() {
        runCatching {
            context.sendBroadcast(Intent(BabyAssistantService.ACTION_PAUSE_BACKGROUND_VOICE).setPackage(context.packageName))
        }
    }

    private fun resumeBackgroundVoice() {
        runCatching {
            context.sendBroadcast(Intent(BabyAssistantService.ACTION_RESUME_BACKGROUND_VOICE).setPackage(context.packageName))
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(tag, "TTS initialization failed")
            return
        }
        textToSpeech?.let { tts ->
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            ttsReady = true
            applyVoice()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onTtsStart()
                override fun onDone(utteranceId: String?) = onTtsDone()
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = onTtsDone()
                override fun onError(utteranceId: String?, errorCode: Int) = onTtsDone()
            })
            onTtsInitialized()
        }
    }

    private fun applyVoice() {
        if (!ttsReady) return
        val name = selectedVoiceName ?: return
        runCatching {
            textToSpeech?.voices?.firstOrNull { it.name == name }?.let { textToSpeech?.voice = it }
        }
    }

    fun getAvailableVoiceNames(): List<String> = runCatching {
        val language = Locale.getDefault().language
        (textToSpeech?.voices ?: emptySet()).filter { it.locale.language == language || it.locale.language == "en" }
            .map { it.name }.distinct().sorted()
    }.getOrDefault(emptyList())

    fun speak(text: String, rate: Float = 1f, pitch: Float = 1f) {
        if (!ttsReady || text.isBlank()) return
        textToSpeech?.setSpeechRate(rate)
        textToSpeech?.setPitch(pitch)
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "baby_${System.currentTimeMillis()}") }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
    }

    fun stopSpeaking() { if (ttsReady) textToSpeech?.stop() }

    fun clearSpeechCache() {
        listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        }
    }

    fun destroy() {
        shuttingDown = true
        finishListening(null, resume = false)
        mainHandler.removeCallbacks(cacheCleanup)
        stopSpeaking()
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
    }
}
