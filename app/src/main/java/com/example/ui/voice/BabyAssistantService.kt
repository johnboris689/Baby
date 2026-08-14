package com.example.ui.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.NetworkMonitor
import com.example.data.local.CommandRoutingEngine
import com.example.data.local.DeviceControlManager
import com.example.data.local.RoutingResult
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import com.example.ui.viewmodel.callOnlineAIWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class BabyAssistantService : Service(), TextToSpeech.OnInitListener {

    private val tag = "BabyAssistantService"
    private val channelId = "baby_assistant_channel"
    private val notificationId = 1001

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isSpeaking = false
    private var isListening = false
    private var isContinuousConversation = false
    private var isProcessingCommand = false

    // Single stable AudioRecord for passive wake-word detection
    private var audioRecord: AudioRecord? = null
    private var passiveListeningJob: Job? = null
    private var isPassiveListening = false
    @Volatile private var passiveGeneration = 0L

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var repository: BabyRepository? = null
    private var deviceControlManager: DeviceControlManager? = null
    private var routingEngine: CommandRoutingEngine? = null
    private var networkMonitor: NetworkMonitor? = null

    // Setting values
    private var wakeWordEnabled = true
    private var wakePhrases = setOf("hey baby", "hi baby", "hello baby")
    private var wakeWordConfidenceThreshold = 0.75f
    private var minSpeechThreshold = 2200.0f // Minimum RMS amplitude for VAD

    // Debounce & false activation suppression tracking
    private var lastTriggerTimeMs = 0L
    private val DEBOUNCE_WINDOW_MS = 3000L
    private var falseTriggerCount = 0
    private var lastFalseTriggerCheckTimeMs = 0L
    private var suppressionUntilMs = 0L
    private var backgroundVoicePausedForForeground = false

    companion object {
        const val ACTION_PAUSE_BACKGROUND_VOICE = "com.example.ACTION_PAUSE_BACKGROUND_VOICE"
        const val ACTION_RESUME_BACKGROUND_VOICE = "com.example.ACTION_RESUME_BACKGROUND_VOICE"
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_SPEAK_NOTIFICATION") {
                val text = intent.getStringExtra("text") ?: return
                speak(text)
            }
        }
    }

    private val backgroundVoiceControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE_BACKGROUND_VOICE -> {
                    backgroundVoicePausedForForeground = true
                    stopPassiveAudioRecord()
                    updateNotificationText("BabyAI: Background mic paused for chat")
                }
                ACTION_RESUME_BACKGROUND_VOICE -> {
                    backgroundVoicePausedForForeground = false
                    if (wakeWordEnabled && !isProcessingCommand && !isSpeaking) {
                        startPassiveWakeWordListening()
                        updateNotificationText("BabyAI Background Assistant active")
                    }
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service onCreate")
        val dcm = DeviceControlManager(this)
        deviceControlManager = dcm
        routingEngine = CommandRoutingEngine(dcm)

        val db = AppDatabase.getDatabase(applicationContext)
        repository = BabyRepository(
            db.conversationDao(),
            db.messageDao(),
            db.memoryDao(),
            db.settingDao(),
            db.logDao(),
            db.noteDao(),
            db.taskDao(),
            db.automationRuleDao()
        )

        createNotificationChannel()
        startForeground(notificationId, createNotification("BabyAI Background Assistant active"))

        initializeTTS()
        initializeSpeechRecognizer()

        val notificationFilter = IntentFilter("com.example.ACTION_SPEAK_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, notificationFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, notificationFilter)
        }

        val voiceControlFilter = IntentFilter().apply {
            addAction(ACTION_PAUSE_BACKGROUND_VOICE)
            addAction(ACTION_RESUME_BACKGROUND_VOICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backgroundVoiceControlReceiver, voiceControlFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(backgroundVoiceControlReceiver, voiceControlFilter)
        }

        setupNetworkMonitor()
        loadSettingsAndStart()
    }

    private fun loadSettingsAndStart() {
        scope.launch {
            val repo = repository ?: return@launch
            wakeWordEnabled = repo.getSetting("wake_word_enabled", "true").toBoolean()
            isContinuousConversation = repo.getSetting("is_continuous_mode", "false").toBoolean()
            wakeWordConfidenceThreshold = repo.getSetting("wake_word_confidence_threshold", "0.75").toFloatOrNull() ?: 0.75f
            minSpeechThreshold = (repo.getSetting("min_speech_threshold", "1600.0").toFloatOrNull() ?: 1600.0f).coerceIn(900.0f, 5000.0f)

            // Phrases settings
            val list = mutableSetOf<String>()
            if (repo.getSetting("wake_phrase_hey_baby", "true").toBoolean()) list.add("hey baby")
            if (repo.getSetting("wake_phrase_hi_baby", "true").toBoolean()) list.add("hi baby")
            if (repo.getSetting("wake_phrase_hello_baby", "true").toBoolean()) list.add("hello baby")
            if (repo.getSetting("wake_phrase_baby", "false").toBoolean()) list.add("baby")

            val customPhrase = repo.getSetting("custom_wake_phrase", "").trim().lowercase(Locale.ROOT)
            if (customPhrase.isNotEmpty()) {
                list.add(customPhrase)
            }

            if (list.isNotEmpty()) {
                wakePhrases = list
            }

            if (wakeWordEnabled) {
                startPassiveWakeWordListening()
            }
        }
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val localeResult = tts.setLanguage(Locale.getDefault())
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.language = Locale.US
                }
                isTtsInitialized = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                    }
                })
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "Recognizer ready for command speech")
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "Command speech beginning")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "Command speech ended")
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.e(tag, "Command SpeechRecognizer Error: $error")
                        if (isProcessingCommand) {
                            isProcessingCommand = false
                            if (wakeWordEnabled) {
                                scope.launch {
                                    delay(1000)
                                    startPassiveWakeWordListening()
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim().lowercase(Locale.ROOT)
                            Log.d(tag, "Command speech result: $text")
                            handleCommandSpeechResult(text)
                        } else {
                            isProcessingCommand = false
                            if (wakeWordEnabled) {
                                startPassiveWakeWordListening()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    // --- Passive AudioRecord Wake-Word Engine ---

    private fun startPassiveWakeWordListening() {
        if (isPassiveListening || isProcessingCommand || !wakeWordEnabled || backgroundVoicePausedForForeground) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "Audio permission missing for passive listening")
            return
        }

        stopPassiveAudioRecord()
        val generation = ++passiveGeneration

        passiveListeningJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, 4096)

            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (generation != passiveGeneration) {
                    recorder.release()
                    return@launch
                }

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(tag, "AudioRecord failed to initialize")
                    recorder.release()
                    return@launch
                }

                audioRecord = recorder
                recorder.startRecording()
                isPassiveListening = true
                Log.d(tag, "Passive wake-word AudioRecord session started (generation=$generation)")

                val buffer = ShortArray(bufferSize / 2)
                var consecutiveSpeechFrames = 0
                var noiseFloorSum = 0.0
                var noiseSampleCount = 0
                var estimatedNoiseFloor = 120.0

                while (isActive && isPassiveListening && wakeWordEnabled && generation == passiveGeneration) {
                    if (isProcessingCommand || isSpeaking) {
                        delay(250)
                        continue
                    }

                    val readSize = recorder.read(buffer, 0, buffer.size)
                    if (readSize <= 0) {
                        delay(50)
                        continue
                    }

                    var sumSquare = 0.0
                    for (i in 0 until readSize) {
                        val sample = buffer[i].toDouble()
                        sumSquare += sample * sample
                    }
                    val rms = Math.sqrt(sumSquare / readSize).toFloat()

                    // Calibrate over a longer quiet window so a single sound cannot become the noise floor.
                    if (noiseSampleCount < 40) {
                        noiseFloorSum += rms
                        noiseSampleCount++
                        if (noiseSampleCount == 40) {
                            estimatedNoiseFloor = (noiseFloorSum / 40.0).coerceIn(30.0, 4000.0)
                            Log.d(tag, "Estimated ambient noise floor: $estimatedNoiseFloor")
                        }
                        continue
                    }

                    // Require meaningful energy above both the configured floor and ambient noise.
                    val dynamicThreshold = maxOf(minSpeechThreshold.toDouble(), estimatedNoiseFloor * 2.6).toFloat()
                    if (rms < dynamicThreshold) {
                        consecutiveSpeechFrames = 0
                        continue
                    }

                    consecutiveSpeechFrames++
                    // ~800ms of sustained speech before opening a recognition session.
                    if (consecutiveSpeechFrames < 8) continue
                    consecutiveSpeechFrames = 0

                    val now = System.currentTimeMillis()
                    if (now < suppressionUntilMs || now - lastTriggerTimeMs < DEBOUNCE_WINDOW_MS) continue

                    withContext(Dispatchers.Main) {
                        if (generation == passiveGeneration && !isProcessingCommand && !isSpeaking) {
                            evaluateSpeechCandidate(rms)
                        }
                    }
                    delay(1500)
                }
            } catch (e: Exception) {
                if (generation == passiveGeneration) {
                    Log.e(tag, "Passive wake-word listener stopped: ${e.message}")
                }
            } finally {
                stopPassiveAudioRecord(generation)
            }
        }
    }

    private fun stopPassiveAudioRecord(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && expectedGeneration != passiveGeneration) return
        passiveGeneration++
        isPassiveListening = false
        val recorder = audioRecord
        audioRecord = null
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioRecord", e)
        }
    }

    private fun evaluateSpeechCandidate(rms: Float) {
        if (isProcessingCommand || isSpeaking) return

        // Temporarily pause passive AudioRecord while SpeechRecognizer captures text candidate
        stopPassiveAudioRecord()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Locale.getDefault().language == "en") "en-US" else Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        var tempRecognizer: SpeechRecognizer? = null
        try {
            tempRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)
            tempRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    tempRecognizer?.destroy()
                    // Silently resume passive listening without triggering activation or sound
                    if (wakeWordEnabled && !isProcessingCommand) {
                        startPassiveWakeWordListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    tempRecognizer?.destroy()

                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].trim().lowercase(Locale.ROOT)
                        processWakeWordCandidate(text, rms)
                    } else {
                        if (wakeWordEnabled && !isProcessingCommand) {
                            startPassiveWakeWordListening()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            tempRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error starting candidate speech check: ${e.message}")
            tempRecognizer?.destroy()
            if (wakeWordEnabled && !isProcessingCommand) {
                startPassiveWakeWordListening()
            }
        }
    }

    private fun processWakeWordCandidate(text: String, rms: Float) {
        var highestConfidence = 0.0f
        var matchedPhrase = ""

        for (phrase in wakePhrases) {
            val conf = calculateWakeWordConfidence(text, phrase)
            if (conf > highestConfidence) {
                highestConfidence = conf
                matchedPhrase = phrase
            }
        }

        if (highestConfidence >= wakeWordConfidenceThreshold && matchedPhrase.isNotEmpty()) {
            val now = System.currentTimeMillis()
            lastTriggerTimeMs = now
            falseTriggerCount = 0

            Log.d(tag, "CONFIDENT WAKE-WORD DETECTED! Matched: '$matchedPhrase', Text: '$text', Confidence: $highestConfidence, RMS: $rms")
            scope.launch { repository?.addLog("Voice_Service", "Wake-word detected: '$matchedPhrase' (Confidence: ${"%.2f".format(highestConfidence)})") }

            triggerWakeActivation()
        } else {
            Log.d(tag, "REJECTED Candidate Speech: '$text', Highest Confidence: $highestConfidence (< threshold $wakeWordConfidenceThreshold)")

            // Track false activations for automatic suppression
            val now = System.currentTimeMillis()
            if (now - lastFalseTriggerCheckTimeMs > 20000) {
                falseTriggerCount = 1
                lastFalseTriggerCheckTimeMs = now
            } else {
                falseTriggerCount++
                if (falseTriggerCount >= 3) {
                    suppressionUntilMs = now + 15000 // Suppress triggers for 15s
                    Log.w(tag, "Automatic suppression activated for 15s due to $falseTriggerCount false candidate triggers in 20s")
                    scope.launch { repository?.addLog("Voice_Service", "Suppression activated for 15s due to repeated false triggers") }
                }
            }

            // Silently resume passive AudioRecord loop
            if (wakeWordEnabled && !isProcessingCommand) {
                startPassiveWakeWordListening()
            }
        }
    }

    private fun calculateWakeWordConfidence(text: String, phrase: String): Float {
        val cleanText = text.trim().lowercase(Locale.ROOT)
        val cleanPhrase = phrase.trim().lowercase(Locale.ROOT)

        if (cleanText.isEmpty() || cleanPhrase.isEmpty()) return 0.0f

        if (cleanText == cleanPhrase) return 1.0f // Exact match

        if (cleanText.startsWith("$cleanPhrase ") || cleanText.endsWith(" $cleanPhrase") || cleanText.contains(" $cleanPhrase ")) {
            return 0.90f // Word boundary match
        }

        if (cleanText.contains(cleanPhrase)) {
            val ratio = cleanPhrase.length.toFloat() / cleanText.length.toFloat()
            return (0.70f + (ratio * 0.25f)).coerceIn(0.70f, 0.95f)
        }

        // Token match
        val textTokens = cleanText.split("\\s+".toRegex())
        val phraseTokens = cleanPhrase.split("\\s+".toRegex())
        var matches = 0
        for (pToken in phraseTokens) {
            if (textTokens.contains(pToken)) matches++
        }
        if (phraseTokens.isNotEmpty()) {
            val matchRatio = matches.toFloat() / phraseTokens.size.toFloat()
            if (matchRatio >= 1.0f) return 0.85f
            if (matchRatio >= 0.5f) return 0.50f
        }

        return 0.0f
    }

    private fun triggerWakeActivation() {
        stopPassiveAudioRecord()
        isProcessingCommand = true

        // Play activation tone ONCE when confidently activated
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e(tag, "Failed to play beep sound", e)
        }

        updateNotificationText("BabyAI: Activated")

        scope.launch {
            speak("Yes?")
            delay(1000)
            startCommandListening()
        }
    }

    private fun startCommandListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            isProcessingCommand = false
            if (wakeWordEnabled) startPassiveWakeWordListening()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Locale.getDefault().language == "en") "en-US" else Locale.getDefault().toLanguageTag())
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(tag, "Listening for user command")
        } catch (e: Exception) {
            Log.e(tag, "Error starting command listening", e)
            isProcessingCommand = false
            if (wakeWordEnabled) startPassiveWakeWordListening()
        }
    }

    private fun handleCommandSpeechResult(text: String) {
        if (isProcessingCommand) {
            scope.launch {
                processCommand(text)
            }
        } else {
            processWakeWordCandidate(text, 3000f)
        }
    }

    private suspend fun processCommand(commandText: String) {
        if (commandText.trim().isEmpty()) {
            isProcessingCommand = false
            if (wakeWordEnabled) startPassiveWakeWordListening()
            return
        }

        Log.d(tag, "Processing Voice Command: $commandText")
        repository?.addLog("Voice_Service", "Command received: $commandText")

        // Create or load active conversation ID
        val activeConvId = repository?.allConversations?.firstOrNull()?.firstOrNull()?.id
            ?: repository?.addConversation("Voice Conversation") ?: 1L

        // Save command into database
        repository?.addMessage(activeConvId, "user", commandText)

        // 1. Route intent using CommandRoutingEngine
        val routeResult = routingEngine?.routeAndExecute(commandText) ?: RoutingResult.SendToGemini

        if (routeResult is RoutingResult.LocalCommand) {
            val response = routeResult.responseText
            repository?.addMessage(activeConvId, "assistant", response)
            speak(response)

            isProcessingCommand = false
            if (isContinuousConversation) {
                delay(2000)
                startCommandListening()
            } else if (wakeWordEnabled) {
                delay(2000)
                startPassiveWakeWordListening()
            }
            return
        }

        // 2. Otherwise fall back to Gemini AI response
        updateNotificationText("BabyAI: Thinking...")
        try {
            val apiKey = repository?.getSetting("api_key", "") ?: ""
            val lastMessages = repository?.getMessages(activeConvId)?.firstOrNull()?.takeLast(6) ?: emptyList()
            val messagesHistory = if (lastMessages.isNotEmpty()) {
                lastMessages.map {
                    mapOf("role" to it.role, "content" to it.content)
                }
            } else {
                listOf(mapOf("role" to "user", "content" to commandText))
            }

            val aiResponse = callOnlineAIWrapper(commandText, messagesHistory, apiKey, repository)

            repository?.addMessage(activeConvId, "assistant", aiResponse)
            speak(aiResponse)
        } catch (e: Exception) {
            val errMsg = "Sorry, I had an error processing that: ${e.localizedMessage}"
            repository?.addMessage(activeConvId, "assistant", errMsg)
            speak("Sorry, I couldn't complete that request.")
        } finally {
            updateNotificationText("BabyAI Background Assistant active")
            isProcessingCommand = false
            if (isContinuousConversation) {
                delay(4000)
                startCommandListening()
            } else if (wakeWordEnabled) {
                delay(4000)
                startPassiveWakeWordListening()
            }
        }
    }

    private fun speak(text: String) {
        if (!isTtsInitialized) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "service_tts_id")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "BabyAI Assistant Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BabyAI Assistant")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationText(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, createNotification(content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service onStartCommand")
        if (intent?.action == ACTION_PAUSE_BACKGROUND_VOICE) {
            backgroundVoicePausedForForeground = true
            stopPassiveAudioRecord()
            return START_STICKY
        }
        if (intent?.action == ACTION_RESUME_BACKGROUND_VOICE) {
            backgroundVoicePausedForForeground = false
            if (wakeWordEnabled) startPassiveWakeWordListening()
            return START_STICKY
        }
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupNetworkMonitor() {
        val monitor = NetworkMonitor(applicationContext)
        networkMonitor = monitor
        scope.launch {
            monitor.isConnected.collect { available ->
                val repo = repository ?: return@collect
                repo.addLog("Voice_Service", "Network connectivity: ${if (available) "CONNECTED" else "DISCONNECTED"}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service onDestroy")
        try {
            unregisterReceiver(backgroundVoiceControlReceiver)
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister background voice receiver", e)
        }
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister notification receiver", e)
        }
        passiveListeningJob?.cancel()
        stopPassiveAudioRecord()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        scope.launch {
            repository?.addLog("Voice_Service", "Background assistant service terminated.")
        }
    }
}
