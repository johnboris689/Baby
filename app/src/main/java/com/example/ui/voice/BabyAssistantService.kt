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
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.data.NetworkMonitor
import com.example.data.local.DeviceControlManager
import com.example.data.local.CommandRoutingEngine
import com.example.data.local.RoutingResult
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.callOnlineAIWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class BabyAssistantService : Service(), TextToSpeech.OnInitListener {

    private val tag = "BabyAssistantService"
    private val channelId = "baby_assistant_channel"
    private val notificationId = 1001

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isListening = false
    private var isContinuousConversation = false
    private var isProcessingCommand = false

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var repository: BabyRepository? = null
    private var deviceControlManager: DeviceControlManager? = null
    private var routingEngine: CommandRoutingEngine? = null
    private var networkMonitor: NetworkMonitor? = null

    // Setting values
    private var wakeWordEnabled = true
    private var wakePhrases = setOf("hey baby", "hi baby", "hello baby", "baby", "good morning baby")

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_SPEAK_NOTIFICATION") {
                val text = intent.getStringExtra("text") ?: return
                speak(text)
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

        // Register broadcast for notifications speech
        val filter = IntentFilter("com.example.ACTION_SPEAK_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }

        setupNetworkMonitor()
        loadSettingsAndStart()
    }

    private fun loadSettingsAndStart() {
        scope.launch {
            val repo = repository ?: return@launch
            wakeWordEnabled = repo.getSetting("wake_word_enabled", "true").toBoolean()
            isContinuousConversation = repo.getSetting("is_continuous_mode", "false").toBoolean()
            
            // Phrases settings
            val list = mutableSetOf<String>()
            if (repo.getSetting("wake_phrase_hey_baby", "true").toBoolean()) list.add("hey baby")
            if (repo.getSetting("wake_phrase_hi_baby", "true").toBoolean()) list.add("hi baby")
            if (repo.getSetting("wake_phrase_hello_baby", "true").toBoolean()) list.add("hello baby")
            if (repo.getSetting("wake_phrase_baby", "true").toBoolean()) list.add("baby")
            
            val customPhrase = repo.getSetting("custom_wake_phrase", "").trim().lowercase(Locale.ROOT)
            if (customPhrase.isNotEmpty()) {
                list.add(customPhrase)
            }

            if (list.isNotEmpty()) {
                wakePhrases = list
            }

            if (wakeWordEnabled) {
                startWakeWordListening()
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
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "Recognizer ready for speech")
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "Speech beginning")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "Speech ended")
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.e(tag, "SpeechRecognizer Error: $error")
                        // Automatically restart wake-word listening loop on error
                        if (wakeWordEnabled && !isProcessingCommand) {
                            scope.launch {
                                delay(1000)
                                startWakeWordListening()
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim().lowercase(Locale.ROOT)
                            Log.d(tag, "OnResults text: $text")
                            handleSpeechResult(text)
                        } else {
                            if (wakeWordEnabled) {
                                startWakeWordListening()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim().lowercase(Locale.ROOT)
                            // Check if wake word is spoken partially to speed up detection!
                            if (wakeWordEnabled && !isProcessingCommand) {
                                for (phrase in wakePhrases) {
                                    if (text.contains(phrase)) {
                                        Log.d(tag, "Wake-word triggered partially: $phrase")
                                        triggerWakeActivation()
                                        break
                                    }
                                }
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startWakeWordListening() {
        if (isListening || isProcessingCommand) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "Audio permission missing for background listening")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(tag, "Started Wake Word Listening loop")
        } catch (e: Exception) {
            Log.e(tag, "Error starting wake word listening", e)
        }
    }

    private fun triggerWakeActivation() {
        speechRecognizer?.cancel()
        isListening = false
        isProcessingCommand = true

        // 1. Play activation tone (using ToneGenerator)
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e(tag, "Failed to play beep sound", e)
        }

        // Update notification
        updateNotificationText("BabyAI: Activated")

        // 2. Speak prompt response
        scope.launch {
            speak("Yes?")
            delay(1000)
            // 3. Start active listening for commands
            startCommandListening()
        }
    }

    private fun startCommandListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            isProcessingCommand = false
            if (wakeWordEnabled) startWakeWordListening()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(tag, "Listening for user command")
        } catch (e: Exception) {
            Log.e(tag, "Error starting command listening", e)
            isProcessingCommand = false
            if (wakeWordEnabled) startWakeWordListening()
        }
    }

    private fun handleSpeechResult(text: String) {
        if (isProcessingCommand) {
            scope.launch {
                processCommand(text)
            }
        } else {
            // Check if wake word is mentioned in standard listening
            var wakeTriggered = false
            for (phrase in wakePhrases) {
                if (text.contains(phrase)) {
                    wakeTriggered = true
                    break
                }
            }
            if (wakeTriggered) {
                triggerWakeActivation()
            } else if (wakeWordEnabled) {
                startWakeWordListening()
            }
        }
    }

    private suspend fun processCommand(commandText: String) {
        if (commandText.trim().isEmpty()) {
            isProcessingCommand = false
            if (wakeWordEnabled) startWakeWordListening()
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
                startCommandListening() // Listen immediately again!
            } else if (wakeWordEnabled) {
                delay(2000)
                startWakeWordListening()
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
                startWakeWordListening()
            }
        }
    }

    // Matches speech text with specific Device Controls via CommandRoutingEngine
    private fun executeLocalDeviceControl(text: String): String? {
        val result = routingEngine?.routeAndExecute(text)
        return if (result is RoutingResult.LocalCommand) {
            result.responseText
        } else {
            null
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
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister notification receiver", e)
        }
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        scope.launch {
            repository?.addLog("Voice_Service", "Background assistant service terminated.")
        }
    }
}
