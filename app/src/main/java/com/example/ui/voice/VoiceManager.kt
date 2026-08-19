package com.example.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.util.Locale

/**
 * Owns foreground/manual microphone input for Baby.
 *
 * The background wake-word service and this class must never capture audio at the
 * same time. MicrophoneArbiter serializes ownership, while the small native-audio
 * settle delay prevents SpeechRecognizer from being started immediately after an
 * AudioRecord/SpeechRecognizer instance has been cancelled or destroyed.
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
    private var sessionStarting = false
    private var retryCount = 0
    private var sessionStartedAt = 0L
    private var shuttingDown = false
    private var allowBackgroundResume = true

    // Changes every time a voice session starts/ends. Recognition callbacks from an
    // old recognizer are ignored instead of touching a newer session.
    private var sessionGeneration = 0L

    private val MAX_SESSION_MS = 90_000L
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 350L
    private val MIC_HANDOFF_TIMEOUT_MS = 4000L
    private val MIC_HANDOFF_POLL_MS = 100L
    private val MIC_SETTLE_DELAY_MS = 250L
    private val MANUAL_MIC_OWNER = "manual_voice"

    private val sessionGuard = Runnable {
        if (listening && System.currentTimeMillis() - sessionStartedAt >= MAX_SESSION_MS) {
            Log.d(tag, "Manual voice session reached safety limit")
            finishListening("Voice session timed out. Tap the mic to try again.", resume = true)
        }
    }

    private val cacheCleanup = object : Runnable {
        override fun run() {
            clearSpeechCache()
            if (!shuttingDown) {
                mainHandler.postDelayed(this, if (isPowerSaveMode) 900_000L else 300_000L)
            }
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "SpeechRecognizer is not available on this device")
            return null
        }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .onFailure { Log.e(tag, "Failed to create SpeechRecognizer", it) }
            .getOrNull()
    }

    private fun disposeRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun configureRecognizer(generation: Long) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            private fun isCurrent(): Boolean = !shuttingDown && generation == sessionGeneration

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrent()) return
                listening = true
                onListeningStateChanged(true)
                Log.d(tag, "SpeechRecognizer ready")
            }

            override fun onBeginningOfSpeech() {
                if (isCurrent()) Log.d(tag, "SpeechRecognizer speech beginning")
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isCurrent()) onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (isCurrent()) Log.d(tag, "SpeechRecognizer speech ended")
            }

            override fun onError(error: Int) {
                if (!isCurrent()) return
                Log.w(tag, "SpeechRecognizer error=$error retry=$retryCount")

                val retryable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT

                if (retryable && retryCount < MAX_RETRIES &&
                    System.currentTimeMillis() - sessionStartedAt < MAX_SESSION_MS
                ) {
                    retryCount++
                    scheduleRecognizerRetry(generation)
                    return
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed. Please try the microphone again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection is needed for speech recognition."
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the microphone and try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone was busy. Please try again."
                    SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error. Please try again."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition restarted safely. Please try again."
                    else -> "Speech recognition ended. Please try again."
                }
                finishListening(message, resume = true)
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrent()) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()

                Log.d(tag, "SpeechRecognizer result='$text'")
                if (text.isBlank()) {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        scheduleRecognizerRetry(generation)
                    } else {
                        finishListening("No speech recognized. Tap the microphone to try again.", resume = true)
                    }
                    return
                }

                finishListening(null, resume = false)
                runCatching { onFinalSpeech(text) }
                    .onFailure { Log.e(tag, "Voice result callback failed", it) }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent()) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                if (text.isNotBlank()) onPartialSpeech(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun scheduleRecognizerRetry(generation: Long) {
        mainHandler.postDelayed({
            if (shuttingDown || generation != sessionGeneration || !listening) return@postDelayed
            startRecognizerSession(generation)
        }, RETRY_DELAY_MS)
    }

    fun setBackgroundResumeAllowed(allowed: Boolean) {
        allowBackgroundResume = allowed
    }

    fun startListening() {
        mainHandler.post {
            if (shuttingDown || listening || sessionStarting) return@post
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                onErrorSpeech("Microphone permission is required.")
                return@post
            }

            pauseBackgroundVoice()
            retryCount = 0
            sessionStartedAt = System.currentTimeMillis()
            val generation = ++sessionGeneration
            sessionStarting = true
            waitForMicrophoneAndStart(generation, 0L)
        }
    }

    private fun waitForMicrophoneAndStart(generation: Long, elapsedMs: Long) {
        if (shuttingDown || !sessionStarting || generation != sessionGeneration) return

        if (MicrophoneArbiter.tryAcquire(MANUAL_MIC_OWNER)) {
            sessionStarting = false
            startRecognizerSession(generation)
            return
        }

        if (elapsedMs >= MIC_HANDOFF_TIMEOUT_MS) {
            sessionStarting = false
            finishListening("The microphone is still busy. Please try again in a moment.", resume = true)
            return
        }

        mainHandler.postDelayed(
            { waitForMicrophoneAndStart(generation, elapsedMs + MIC_HANDOFF_POLL_MS) },
            MIC_HANDOFF_POLL_MS
        )
    }

    private fun startRecognizerSession(generation: Long) {
        if (shuttingDown || generation != sessionGeneration) return

        // A previous SpeechRecognizer can remain internally attached to AudioFlinger
        // for a short time after cancel(). Destroy it on the main thread and wait for
        // the native audio stack to settle before creating the next session.
        disposeRecognizer()

        recognizer = createRecognizer()
        if (recognizer == null) {
            finishListening("Speech recognition is unavailable on this device.", resume = true)
            return
        }

        configureRecognizer(generation)

        val language = if (Locale.getDefault().language == "en") {
            "en-US"
        } else {
            Locale.getDefault().toLanguageTag()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            listening = true
            onListeningStateChanged(true)
            recognizer?.startListening(intent)
            mainHandler.removeCallbacks(sessionGuard)
            mainHandler.postDelayed(sessionGuard, MAX_SESSION_MS)
        } catch (e: Exception) {
            Log.e(tag, "Unable to start SpeechRecognizer", e)
            finishListening("Could not start the microphone. Please try again.", resume = true)
        }
    }

    fun stopListening() {
        if (!listening && !sessionStarting) return
        finishListening(null, resume = true)
    }

    fun cancelListening() {
        finishListening(null, resume = true)
    }

    private fun finishListening(errorMessage: String?, resume: Boolean) {
        if (shuttingDown) return

        val wasActive = listening || sessionStarting
        sessionStarting = false
        listening = false
        ++sessionGeneration // invalidate callbacks from the old recognizer
        mainHandler.removeCallbacks(sessionGuard)
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed(cacheCleanup, if (isPowerSaveMode) 900_000L else 300_000L)

        disposeRecognizer()

        // Do not immediately hand the native microphone to another component.
        // Android devices can take a short moment to tear down AudioRecord/
        // SpeechRecognizer after cancel/destroy. Resume the background service only
        // after the arbiter has been released, otherwise it can race the handoff.
        mainHandler.postDelayed({
            MicrophoneArbiter.release(MANUAL_MIC_OWNER)
            if (resume && wasActive && allowBackgroundResume && !shuttingDown) {
                resumeBackgroundVoice()
            }
        }, MIC_SETTLE_DELAY_MS)

        onListeningStateChanged(false)
        onPartialSpeech("")
        if (!errorMessage.isNullOrBlank()) {
            runCatching { onErrorSpeech(errorMessage) }
                .onFailure { Log.e(tag, "Voice error callback failed", it) }
        }
    }

    private fun pauseBackgroundVoice() {
        runCatching {
            context.sendBroadcast(
                Intent(BabyAssistantService.ACTION_PAUSE_BACKGROUND_VOICE)
                    .setPackage(context.packageName)
            )
        }
    }

    private fun resumeBackgroundVoice() {
        runCatching {
            context.sendBroadcast(
                Intent(BabyAssistantService.ACTION_RESUME_BACKGROUND_VOICE)
                    .setPackage(context.packageName)
            )
        }
    }

    fun resumeBackgroundIfAllowed() {
        if (allowBackgroundResume && !shuttingDown) resumeBackgroundVoice()
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
                override fun onDone(utteranceId: String?) {
                    if (allowBackgroundResume) resumeBackgroundVoice()
                    runCatching { onTtsDone() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (allowBackgroundResume) resumeBackgroundVoice()
                    runCatching { onTtsDone() }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (allowBackgroundResume) resumeBackgroundVoice()
                    runCatching { onTtsDone() }
                }
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
        (textToSpeech?.voices ?: emptySet())
            .filter { it.locale.language == language || it.locale.language == "en" }
            .map { it.name }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    fun speak(text: String, rate: Float = 1f, pitch: Float = 1f) {
        if (!ttsReady || text.isBlank()) return
        textToSpeech?.setSpeechRate(rate)
        textToSpeech?.setPitch(pitch)
        val utteranceId = "baby_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        runCatching {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun stopSpeaking() {
        runCatching { if (ttsReady) textToSpeech?.stop() }
    }

    private fun clearSpeechCache() {
        listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        }
    }

    fun destroy() {
        if (shuttingDown) return
        shuttingDown = true
        ++sessionGeneration
        mainHandler.removeCallbacksAndMessages(null)
        disposeRecognizer()
        MicrophoneArbiter.release(MANUAL_MIC_OWNER)
        stopSpeaking()
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
        ttsReady = false
    }
}
