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
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.callOfflineAIWrapper
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
    private var networkMonitor: NetworkMonitor? = null

    // Setting values
    private var wakeWordEnabled = true
    private var wakePhrases = setOf("hey baby", "hi baby", "hello baby", "baby")

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
        deviceControlManager = DeviceControlManager(this)
        
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

        // 1. Analyze if it is a local device command
        val matchResult = executeLocalDeviceControl(commandText)
        if (matchResult != null) {
            repository?.addMessage(activeConvId, "assistant", matchResult)
            speak(matchResult)
            
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

        // 2. Otherwise fall back to online/offline AI response
        updateNotificationText("BabyAI: Thinking...")
        try {
            val isOffline = repository?.getSetting("is_offline_mode", "false").toBoolean()
            val apiKey = repository?.getSetting("api_key", "") ?: ""
            val backendUrl = repository?.getSetting("backend_url", "http://10.0.2.2:5000/") ?: ""

            val messagesHistory = listOf(mapOf("role" to "user", "content" to commandText))
            
            val aiResponse = if (isOffline) {
                callOfflineAIWrapper(commandText, messagesHistory, repository)
            } else {
                callOnlineAIWrapper(commandText, messagesHistory, apiKey, repository)
            }

            repository?.addMessage(activeConvId, "assistant", aiResponse)
            speak(aiResponse)
        } catch (e: Exception) {
            val errMsg = "Error processing: ${e.localizedMessage}"
            repository?.addMessage(activeConvId, "assistant", errMsg)
            speak("Sorry, I had an error processing that.")
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

    // Matches speech text with specific Device Controls
    private fun executeLocalDeviceControl(text: String): String? {
        val manager = deviceControlManager ?: return null
        val lowerText = text.lowercase(Locale.ROOT)

        // Flashlight
        if (lowerText.contains("turn on flashlight") || lowerText.contains("turn on torch") || lowerText.contains("flashlight on") || lowerText.contains("torch on")) {
            return manager.setFlashlight(true)
        }
        if (lowerText.contains("turn off flashlight") || lowerText.contains("turn off torch") || lowerText.contains("flashlight off") || lowerText.contains("torch off")) {
            return manager.setFlashlight(false)
        }

        // Launch Apps
        if (lowerText.startsWith("open ") || lowerText.startsWith("launch ")) {
            val appName = text.substringAfter("open").substringAfter("launch").trim()
            return manager.launchApp(appName)
        }

        // Music
        if (lowerText.contains("play music") || lowerText.contains("resume music")) {
            return manager.controlMusic("play")
        }
        if (lowerText.contains("pause music") || lowerText.contains("stop music")) {
            return manager.controlMusic("pause")
        }
        if (lowerText.contains("next song") || lowerText.contains("next track")) {
            return manager.controlMusic("next")
        }
        if (lowerText.contains("previous song") || lowerText.contains("previous track")) {
            return manager.controlMusic("previous")
        }
        if (lowerText.contains("volume up") || lowerText.contains("louder")) {
            return manager.controlMusic("volume_up")
        }
        if (lowerText.contains("volume down") || lowerText.contains("quieter")) {
            return manager.controlMusic("volume_down")
        }

        // Navigation
        if (lowerText.startsWith("navigate to ") || lowerText.startsWith("directions to ")) {
            val dest = text.substringAfter("navigate to").substringAfter("directions to").trim()
            return manager.openMaps(dest)
        }
        if (lowerText.contains("navigate home")) {
            return manager.openMaps("Home")
        }
        if (lowerText.contains("navigate to work")) {
            return manager.openMaps("Work")
        }
        if (lowerText.contains("search nearby restaurants") || lowerText.contains("restaurants nearby")) {
            return manager.openMaps("restaurants")
        }

        // Device states
        if (lowerText.contains("turn bluetooth on") || lowerText.contains("bluetooth on")) {
            return manager.setBluetoothState(true)
        }
        if (lowerText.contains("turn bluetooth off") || lowerText.contains("bluetooth off")) {
            return manager.setBluetoothState(false)
        }
        if (lowerText.contains("turn wifi on") || lowerText.contains("wifi on")) {
            return manager.setWifiState(true)
        }
        if (lowerText.contains("turn wifi off") || lowerText.contains("wifi off")) {
            return manager.setWifiState(false)
        }
        if (lowerText.contains("enable do not disturb") || lowerText.contains("turn dnd on") || lowerText.contains("dnd on")) {
            return manager.setDNDMode(true)
        }
        if (lowerText.contains("disable do not disturb") || lowerText.contains("turn dnd off") || lowerText.contains("dnd off")) {
            return manager.setDNDMode(false)
        }

        // Brightness
        if (lowerText.contains("brightness to ")) {
            val raw = lowerText.substringAfter("brightness to").trim().removeSuffix("%").trim()
            val parsed = raw.toFloatOrNull()
            if (parsed != null) {
                val value = (parsed / 100f).coerceIn(0f, 1f)
                return manager.setBrightness(value)
            }
        }

        // Web Search / Browser
        if (lowerText.startsWith("search google for ") || lowerText.startsWith("search for ")) {
            val q = text.substringAfter("search google for").substringAfter("search for").trim()
            return manager.searchWeb(q, "google")
        }
        if (lowerText.startsWith("search youtube for ")) {
            val q = text.substringAfter("search youtube for").trim()
            return manager.searchWeb(q, "youtube")
        }
        if (lowerText.startsWith("search wikipedia for ")) {
            val q = text.substringAfter("search wikipedia for").trim()
            return manager.searchWeb(q, "wikipedia")
        }
        if (lowerText.startsWith("open website ")) {
            val site = text.substringAfter("open website").trim()
            return manager.openWebsite(site)
        }

        // Settings Shortcuts
        if (lowerText.contains("open wifi settings")) return manager.openSettingsScreen("wifi")
        if (lowerText.contains("open bluetooth settings")) return manager.openSettingsScreen("bluetooth")
        if (lowerText.contains("open battery settings")) return manager.openSettingsScreen("battery")
        if (lowerText.contains("open developer settings") || lowerText.contains("open developer options")) return manager.openSettingsScreen("developer")
        if (lowerText.contains("open accessibility settings")) return manager.openSettingsScreen("accessibility")
        if (lowerText.contains("open application settings")) return manager.openSettingsScreen("applications")

        // Clipboard
        if (lowerText.contains("read clipboard") || lowerText.contains("what is in my clipboard")) {
            return "In your clipboard: " + manager.readClipboard()
        }

        // Contacts / Calls
        if (lowerText.startsWith("call ")) {
            val contactName = text.substringAfter("call").trim()
            val contacts = manager.searchContacts(contactName)
            return if (contacts.isNotEmpty()) {
                val contact = contacts[0]
                manager.makeCall(contact.phoneNumber)
                "Placing call to ${contact.name} at ${contact.phoneNumber}."
            } else {
                "No contact found matching '$contactName'."
            }
        }
        if (lowerText.contains("search contact ") || lowerText.contains("find contact ")) {
            val q = text.substringAfter("search contact").substringAfter("find contact").trim()
            val list = manager.searchContacts(q)
            return if (list.isNotEmpty()) {
                "Found contacts:\n" + list.joinToString("\n") { "- ${it.name}: ${it.phoneNumber}" }
            } else {
                "No contacts found matching '$q'."
            }
        }

        // SMS
        if (lowerText.startsWith("send sms to ") || lowerText.startsWith("text ")) {
            val parts = text.substringAfter("send sms to").substringAfter("text").trim().split(" ", limit = 2)
            if (parts.size >= 2) {
                val contactName = parts[0]
                val msg = parts[1]
                val contacts = manager.searchContacts(contactName)
                if (contacts.isNotEmpty()) {
                    val phone = contacts[0].phoneNumber
                    return manager.sendSMS(phone, msg)
                }
            }
        }

        // Camera
        if (lowerText.contains("open camera")) return manager.openSystemCamera(false)
        if (lowerText.contains("record video")) return manager.openSystemCamera(true)

        return null
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
                val isAutoSwitchEnabled = repo.getSetting("is_auto_switch_enabled", "true").toBoolean()
                if (isAutoSwitchEnabled) {
                    val targetOfflineMode = !available
                    val currentOfflineMode = repo.getSetting("is_offline_mode", "false").toBoolean()
                    if (currentOfflineMode != targetOfflineMode) {
                        repo.saveSetting("is_offline_mode", targetOfflineMode.toString())
                        repo.addLog(
                            "Network_AutoSwitch",
                            "Background service detected connection change. Switched AI core to ${if (targetOfflineMode) "Local Llama.cpp (Offline)" else "Gemini Cloud (Online)"}"
                        )
                    }
                }
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
