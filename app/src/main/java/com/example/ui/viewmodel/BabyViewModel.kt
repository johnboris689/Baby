package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.voice.BabyAssistantService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.ApiClients
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.GeminiGenerationConfig
import com.example.data.api.ChatRequest
import com.example.data.api.LlamaCompletionRequest
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.AutomationRuleEntity
import com.example.data.repository.BabyRepository
import com.example.data.local.DeviceControlManager
import com.example.ui.voice.VoiceManager
import com.example.data.NetworkMonitor
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class BabyViewModel(
    application: Application,
    private val repository: BabyRepository
) : AndroidViewModel(application) {

    // --- Assistant States ---
    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _rmsDb = MutableStateFlow(0.0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialSpeechText = MutableStateFlow("")
    val partialSpeechText: StateFlow<String> = _partialSpeechText.asStateFlow()

    private val _backendHealth = MutableStateFlow(false)
    val backendHealth: StateFlow<Boolean> = _backendHealth.asStateFlow()

    // --- Conversational State ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessages(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Memories & Logs Flow ---
    val memories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<LogEntity>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Configuration State ---
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isAutoSwitchEnabled = MutableStateFlow(true)
    val isAutoSwitchEnabled: StateFlow<Boolean> = _isAutoSwitchEnabled.asStateFlow()

    private val deviceControlManager = DeviceControlManager(application)
    private val networkMonitor = NetworkMonitor(application)
    private val _isInternetAvailable = MutableStateFlow(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val _selectedProvider = MutableStateFlow("gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _backendUrl = MutableStateFlow("http://10.0.2.2:5000/")
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _llamaUrl = MutableStateFlow("http://10.0.2.2:8080/")
    val llamaUrl: StateFlow<String> = _llamaUrl.asStateFlow()

    private val _voicePitch = MutableStateFlow(1.0f)
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    private val _voiceRate = MutableStateFlow(1.0f)
    val voiceRate: StateFlow<Float> = _voiceRate.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _silenceThreshold = MutableStateFlow(2.0f)
    val silenceThreshold: StateFlow<Float> = _silenceThreshold.asStateFlow()

    private val _voiceStyle = MutableStateFlow("default")
    val voiceStyle: StateFlow<String> = _voiceStyle.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow("")
    val selectedVoiceName: StateFlow<String> = _selectedVoiceName.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isPowerSaveActive = MutableStateFlow(false)
    val isPowerSaveActive: StateFlow<Boolean> = _isPowerSaveActive.asStateFlow()

    private val _forcePowerSave = MutableStateFlow(false)
    val forcePowerSave: StateFlow<Boolean> = _forcePowerSave.asStateFlow()

    private val _autoPowerSave = MutableStateFlow(true)
    val autoPowerSave: StateFlow<Boolean> = _autoPowerSave.asStateFlow()

    private val _thinkingMode = MutableStateFlow("balanced")
    val thinkingMode: StateFlow<String> = _thinkingMode.asStateFlow()

    private val _streamingMessageText = MutableStateFlow<String?>(null)
    val streamingMessageText: StateFlow<String?> = _streamingMessageText.asStateFlow()

    // --- Device Control settings ---
    private val _wakeWordEnabled = MutableStateFlow(true)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _backgroundListeningEnabled = MutableStateFlow(true)
    val backgroundListeningEnabled: StateFlow<Boolean> = _backgroundListeningEnabled.asStateFlow()

    private val _flashlightControlEnabled = MutableStateFlow(true)
    val flashlightControlEnabled: StateFlow<Boolean> = _flashlightControlEnabled.asStateFlow()

    private val _appLaunchingEnabled = MutableStateFlow(true)
    val appLaunchingEnabled: StateFlow<Boolean> = _appLaunchingEnabled.asStateFlow()

    private val _notificationAccessEnabled = MutableStateFlow(true)
    val notificationAccessEnabled: StateFlow<Boolean> = _notificationAccessEnabled.asStateFlow()

    private val _storageAccessEnabled = MutableStateFlow(true)
    val storageAccessEnabled: StateFlow<Boolean> = _storageAccessEnabled.asStateFlow()

    private val _microphoneAccessEnabled = MutableStateFlow(true)
    val microphoneAccessEnabled: StateFlow<Boolean> = _microphoneAccessEnabled.asStateFlow()

    private val _cameraAccessEnabled = MutableStateFlow(true)
    val cameraAccessEnabled: StateFlow<Boolean> = _cameraAccessEnabled.asStateFlow()

    private val _contactAccessEnabled = MutableStateFlow(true)
    val contactAccessEnabled: StateFlow<Boolean> = _contactAccessEnabled.asStateFlow()

    private val _smsAccessEnabled = MutableStateFlow(true)
    val smsAccessEnabled: StateFlow<Boolean> = _smsAccessEnabled.asStateFlow()

    private val _backgroundServiceEnabled = MutableStateFlow(false)
    val backgroundServiceEnabled: StateFlow<Boolean> = _backgroundServiceEnabled.asStateFlow()

    private val _bootOnStartupEnabled = MutableStateFlow(false)
    val bootOnStartupEnabled: StateFlow<Boolean> = _bootOnStartupEnabled.asStateFlow()

    private val _batteryOptimizationEnabled = MutableStateFlow(true)
    val batteryOptimizationEnabled: StateFlow<Boolean> = _batteryOptimizationEnabled.asStateFlow()

    // Wake phrases
    private val _wakePhraseHeyBaby = MutableStateFlow(true)
    val wakePhraseHeyBaby: StateFlow<Boolean> = _wakePhraseHeyBaby.asStateFlow()

    private val _wakePhraseHiBaby = MutableStateFlow(true)
    val wakePhraseHiBaby: StateFlow<Boolean> = _wakePhraseHiBaby.asStateFlow()

    private val _wakePhraseHelloBaby = MutableStateFlow(true)
    val wakePhraseHelloBaby: StateFlow<Boolean> = _wakePhraseHelloBaby.asStateFlow()

    private val _wakePhraseBaby = MutableStateFlow(true)
    val wakePhraseBaby: StateFlow<Boolean> = _wakePhraseBaby.asStateFlow()

    private val _customWakePhrase = MutableStateFlow("")
    val customWakePhrase: StateFlow<String> = _customWakePhrase.asStateFlow()

    private var activeGenerationJob: Job? = null

    private var healthCheckJob: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                val pct = (level * 100f / scale).toInt()
                _batteryLevel.value = pct
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            updatePowerSaveState()
        }
    }

    // --- Voice Manager ---
    private var voiceManager: VoiceManager? = null

    init {
        // Observe real-time network connectivity
        viewModelScope.launch {
            networkMonitor.isConnected.collect { available ->
                _isInternetAvailable.value = available
                repository.addLog("Network", "Internet status changed: ${if (available) "CONNECTED" else "DISCONNECTED"}")
                
                if (_isAutoSwitchEnabled.value) {
                    val targetOfflineMode = !available
                    if (_isOfflineMode.value != targetOfflineMode) {
                        _isOfflineMode.value = targetOfflineMode
                        repository.saveSetting("is_offline_mode", targetOfflineMode.toString())
                        repository.addLog(
                            "Network_AutoSwitch",
                            "Auto-switched AI engine to ${if (targetOfflineMode) "Local Llama.cpp (Offline)" else "Gemini Cloud (Online)"}"
                        )
                    }
                }
            }
        }

        // Load initial settings from DB
        viewModelScope.launch {
            _isAutoSwitchEnabled.value = repository.getSetting("is_auto_switch_enabled", "true").toBoolean()
            _isOfflineMode.value = repository.getSetting("is_offline_mode", "false").toBoolean()
            _selectedProvider.value = repository.getSetting("selected_provider", "gemini")
            _apiKey.value = repository.getSetting("api_key", "")
            _backendUrl.value = repository.getSetting("backend_url", "http://10.0.2.2:5000/")
            _llamaUrl.value = repository.getSetting("llama_url", "http://10.0.2.2:8080/")
            _voicePitch.value = repository.getSetting("voice_pitch", "1.0").toFloatOrNull() ?: 1.0f
            _voiceRate.value = repository.getSetting("voice_rate", "1.0").toFloatOrNull() ?: 1.0f
            _voiceStyle.value = repository.getSetting("voice_style", "default")
            _selectedVoiceName.value = repository.getSetting("selected_voice_name", "")
            _isContinuousMode.value = repository.getSetting("is_continuous_mode", "false").toBoolean()
            _silenceThreshold.value = repository.getSetting("silence_amplitude_threshold", "2.0").toFloatOrNull() ?: 2.0f
            _forcePowerSave.value = repository.getSetting("force_power_save", "false").toBoolean()
            _autoPowerSave.value = repository.getSetting("auto_power_save", "true").toBoolean()
            _thinkingMode.value = repository.getSetting("thinking_mode", "balanced")

            // Load Device Control Settings
            _wakeWordEnabled.value = repository.getSetting("wake_word_enabled", "true").toBoolean()
            _backgroundListeningEnabled.value = repository.getSetting("background_listening_enabled", "true").toBoolean()
            _flashlightControlEnabled.value = repository.getSetting("flashlight_control_enabled", "true").toBoolean()
            _appLaunchingEnabled.value = repository.getSetting("app_launching_enabled", "true").toBoolean()
            _notificationAccessEnabled.value = repository.getSetting("notification_access_enabled", "true").toBoolean()
            _storageAccessEnabled.value = repository.getSetting("storage_access_enabled", "true").toBoolean()
            _microphoneAccessEnabled.value = repository.getSetting("microphone_access_enabled", "true").toBoolean()
            _cameraAccessEnabled.value = repository.getSetting("camera_access_enabled", "true").toBoolean()
            _contactAccessEnabled.value = repository.getSetting("contact_access_enabled", "true").toBoolean()
            _smsAccessEnabled.value = repository.getSetting("sms_access_enabled", "true").toBoolean()
            _backgroundServiceEnabled.value = repository.getSetting("background_service", "false").toBoolean()
            _bootOnStartupEnabled.value = repository.getSetting("boot_on_startup", "false").toBoolean()
            _batteryOptimizationEnabled.value = repository.getSetting("battery_optimization_enabled", "true").toBoolean()
            _wakePhraseHeyBaby.value = repository.getSetting("wake_phrase_hey_baby", "true").toBoolean()
            _wakePhraseHiBaby.value = repository.getSetting("wake_phrase_hi_baby", "true").toBoolean()
            _wakePhraseHelloBaby.value = repository.getSetting("wake_phrase_hello_baby", "true").toBoolean()
            _wakePhraseBaby.value = repository.getSetting("wake_phrase_baby", "true").toBoolean()
            _customWakePhrase.value = repository.getSetting("custom_wake_phrase", "")

            // Update power save state initially
            updatePowerSaveState()

            // Sync with voiceManager
            voiceManager?.amplitudeThreshold = _silenceThreshold.value
            voiceManager?.isPowerSaveMode = _isPowerSaveActive.value
            voiceManager?.selectedVoiceName = _selectedVoiceName.value.ifEmpty { null }

            // Select or create first conversation
            conversations.firstOrNull()?.firstOrNull()?.id?.let { firstId ->
                _activeConversationId.value = firstId
            } ?: run {
                createNewConversation("Baby AI Chat")
            }

            // Start periodic health check with frequency matching current power save state
            startHealthCheckTimer()

            // Generate semantic embeddings for existing memories that lack them
            initializeMemoriesWithEmbeddings()
        }

        // Register Battery Broadcast Receiver
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            application.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e("BabyViewModel", "Failed to register battery receiver", e)
        }

        // Initialize voice manager
        voiceManager = VoiceManager(
            context = application,
            onPartialSpeech = { text ->
                _partialSpeechText.value = text
            },
            onFinalSpeech = { text ->
                _partialSpeechText.value = ""
                viewModelScope.launch {
                    sendMessage(text)
                }
            },
            onErrorSpeech = { err ->
                _partialSpeechText.value = ""
                _assistantState.value = AssistantState.IDLE
                viewModelScope.launch {
                    repository.addLog("Voice", "STT Error: $err")
                }
            },
            onRmsChanged = { rms ->
                _rmsDb.value = rms
            },
            onListeningStateChanged = { listening ->
                if (listening) {
                    _assistantState.value = AssistantState.LISTENING
                } else {
                    if (_assistantState.value == AssistantState.LISTENING) {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
            },
            onTtsStart = {
                _assistantState.value = AssistantState.SPEAKING
            },
            onTtsDone = {
                _assistantState.value = AssistantState.IDLE
                if (_isContinuousMode.value) {
                    startListening() // Automatically listen again for continuous conversation!
                }
            },
            onTtsInitialized = {
                _availableVoices.value = voiceManager?.getAvailableVoiceNames() ?: emptyList()
                voiceManager?.selectedVoiceName = _selectedVoiceName.value.ifEmpty { null }
                Log.d("BabyViewModel", "TTS Initialized callback. Voices: ${_availableVoices.value.size}")
            }
        )
    }

    // --- Messaging and AI Logics ---

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
        stopSpeaking()
    }

    fun createNewConversation(title: String) {
        viewModelScope.launch {
            val id = repository.addConversation(title)
            _activeConversationId.value = id
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            val conv = conversations.value.find { it.id == id }
            if (conv != null) {
                repository.updateConversation(conv.copy(title = newTitle))
            }
        }
    }

    fun pinConversation(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            val conv = conversations.value.find { it.id == id }
            if (conv != null) {
                repository.updateConversation(conv.copy(isPinned = isPinned))
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = conversations.value.firstOrNull { it.id != id }?.id
                    ?: repository.addConversation("New Chat")
            }
        }
    }

    fun saveThinkingMode(mode: String) {
        _thinkingMode.value = mode
        viewModelScope.launch {
            repository.saveSetting("thinking_mode", mode)
        }
    }

    fun interruptGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _streamingMessageText.value = null
        _assistantState.value = AssistantState.IDLE
        stopSpeaking()
    }

    fun continueGenerating() {
        val lastMsg = activeMessages.value.lastOrNull()
        if (lastMsg != null && lastMsg.role == "assistant") {
            sendMessage("Continue generating the response for: " + lastMsg.content.take(100) + "...")
        }
    }

    fun editMessageAndRegenerate(messageId: Long, newContent: String) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateMessageContent(messageId, newContent)
            // Delete all subsequent messages in the conversation
            val currentMsgs = activeMessages.value
            val msgIndex = currentMsgs.indexOfFirst { it.id == messageId }
            if (msgIndex != -1) {
                for (i in (msgIndex + 1) until currentMsgs.size) {
                    repository.deleteMessage(currentMsgs[i].id)
                }
            }
            // Trigger regeneration using the last user message
            val lastUserMessage = activeMessages.value.lastOrNull { it.role == "user" }
            if (lastUserMessage != null) {
                sendMessage(lastUserMessage.content, isRegeneration = true)
            }
        }
    }

    fun regenerateResponse() {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val currentMsgs = activeMessages.value
            if (currentMsgs.isNotEmpty()) {
                val lastMsg = currentMsgs.last()
                if (lastMsg.role == "assistant") {
                    repository.deleteMessage(lastMsg.id)
                }
            }
            val lastUserMessage = activeMessages.value.lastOrNull { it.role == "user" }
            if (lastUserMessage != null) {
                sendMessage(lastUserMessage.content, isRegeneration = true)
            }
        }
    }

    fun updateMessageReaction(messageId: Long, reaction: String?) {
        viewModelScope.launch {
            repository.updateMessageReaction(messageId, reaction)
        }
    }

    // --- Productivity & Automation Flows ---
    val notes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<AutomationRuleEntity>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repository.addNote(title, content)
            repository.addLog("Productivity", "Added note: $title")
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            repository.addLog("Productivity", "Deleted note")
        }
    }

    fun addTask(title: String) {
        viewModelScope.launch {
            repository.addTask(title)
            repository.addLog("Productivity", "Added task: $title")
        }
    }

    fun updateTaskStatus(id: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskStatus(id, isCompleted)
            repository.addLog("Productivity", "Updated task status")
            // Active automation trigger check!
            checkAutomationTriggers("TASK_COMPLETED")
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
            repository.addLog("Productivity", "Deleted task")
        }
    }

    fun addAutomationRule(trigger: String, action: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.addRule(trigger, action, isEnabled)
            repository.addLog("Automation", "Added rule: IF $trigger THEN $action")
        }
    }

    fun updateRuleStatus(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateRuleStatus(id, isEnabled)
            repository.addLog("Automation", "Updated rule status")
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
            repository.addLog("Automation", "Deleted rule")
        }
    }

    private fun checkAutomationTriggers(triggerType: String) {
        viewModelScope.launch {
            val activeRules = repository.allRules.firstOrNull()?.filter { it.isEnabled && it.trigger == triggerType } ?: return@launch
            activeRules.forEach { rule ->
                repository.addLog("Automation", "Executing automation action: ${rule.action} triggered by $triggerType")
                when (rule.action) {
                    "ENABLE_POWER_SAVE" -> {
                        _forcePowerSave.value = true
                        saveSetting("force_power_save", "true")
                        updatePowerSaveState()
                    }
                    "OFFLINE_AI" -> {
                        saveSetting("is_offline_mode", "true")
                        _isOfflineMode.value = true
                    }
                    "ADD_LOG" -> {
                        repository.addLog("System", "Automation script ran successfully.")
                    }
                }
            }
        }
    }

    fun sendMessage(content: String, isRegeneration: Boolean = false) {
        if (content.trim().isEmpty() && !isRegeneration) return
        val convId = _activeConversationId.value ?: return

        // Cancel previous active jobs
        activeGenerationJob?.cancel()

        activeGenerationJob = viewModelScope.launch {
            try {
                // Save User Message if not a regeneration
                val finalPrompt = if (!isRegeneration) {
                    repository.addMessage(convId, "user", content)
                    content
                } else {
                    activeMessages.value.lastOrNull { it.role == "user" }?.content ?: content
                }

                _assistantState.value = AssistantState.THINKING

                // Intercept and run local device controls
                val localResult = executeLocalDeviceControl(finalPrompt)
                if (localResult != null) {
                    repository.addLog("Device_Control", "Executed: $localResult")
                    simulateStreamingText(localResult, convId)
                    return@launch
                }

                // If first message in a standard title conversation, auto generate a title
                val currentConv = conversations.value.find { it.id == convId }
                if (currentConv != null && (currentConv.title == "Baby AI Chat" || currentConv.title == "New Chat") && !isRegeneration) {
                    viewModelScope.launch {
                        val autoTitle = if (finalPrompt.length > 20) finalPrompt.take(15) + "..." else finalPrompt
                        renameConversation(convId, autoTitle)
                    }
                }

                val activeMsgHistory = activeMessages.value.map {
                    mapOf("role" to it.role, "content" to it.content)
                }

                val responseText = if (_isOfflineMode.value || !_isInternetAvailable.value) {
                    callOfflineAI(finalPrompt, activeMsgHistory)
                } else {
                    callOnlineAI(finalPrompt, activeMsgHistory)
                }

                // Simulate token-by-token response streaming
                simulateStreamingText(responseText, convId)

            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.addLog("AI", "Generation job cancelled/interrupted.")
            } catch (e: Exception) {
                repository.addLog("AI_Error", "Failed to generate AI response: ${e.message}")
                repository.addMessage(convId, "assistant", "Error: ${e.localizedMessage}. Please verify settings or connection.", isError = true)
                _assistantState.value = AssistantState.IDLE
            }
        }
    }

    private suspend fun simulateStreamingText(fullText: String, convId: Long) {
        _streamingMessageText.value = ""
        _assistantState.value = AssistantState.SPEAKING

        val words = fullText.split(" ")
        val sb = StringBuilder()

        try {
            for (i in words.indices) {
                if (i > 0) sb.append(" ")
                sb.append(words[i])
                _streamingMessageText.value = sb.toString()

                // Adaptive delay simulating natural typing speed
                val delayMs = (words[i].length * 12L).coerceIn(30L, 100L)
                delay(delayMs)
            }

            val savedText = _streamingMessageText.value ?: ""
            if (savedText.isNotEmpty()) {
                repository.addMessage(convId, "assistant", savedText)
                speak(savedText)
                extractMemoryInBackground(words.joinToString(" ").take(150), savedText)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            val partialText = _streamingMessageText.value ?: ""
            if (partialText.isNotEmpty()) {
                repository.addMessage(convId, "assistant", "$partialText... [Interrupted]")
                speak("$partialText")
            }
            throw e
        } finally {
            _streamingMessageText.value = null
            _assistantState.value = AssistantState.IDLE
            activeGenerationJob = null
        }
    }

    private suspend fun callOnlineAI(prompt: String, history: List<Map<String, String>>): String = withContext(Dispatchers.IO) {
        val resolvedKey = _apiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (resolvedKey.isEmpty() || resolvedKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing. Please enter it in Settings or configure the Secrets Panel.")
        }

        repository.addLog("AI_Call", "Calling Gemini API (Power-Save Active: ${_isPowerSaveActive.value})...")

        // Gather relevant memory context to enrich the conversation prompt using hybrid semantic vector search!
        val maxMemories = if (_isPowerSaveActive.value) 1 else 5
        val semanticResults = retrieveSemanticMemories(prompt, limit = maxMemories)
        val memoryContext = if (semanticResults.isNotEmpty()) {
            "Relevant user memories to remember:\n" + semanticResults.joinToString("\n") { "- ${it.first.content} (similarity score: ${"%.2f".format(it.second)})" } + "\n\n"
        } else ""

        // Build Gemini request with system instructions and user history
        val isPowerSave = _isPowerSaveActive.value
        val isDeepThinking = _thinkingMode.value == "deep"
        val systemInstructionText = "You are Baby, a natural, emotionally intelligent offline-first AI assistant for Android. " +
                "Use the local memories if provided. Keep your answers concise, engaging, and friendly." +
                (if (isPowerSave) " Power-save mode is active: restrict responses to be extremely short (under 15 words) to conserve battery." else "") +
                (if (isDeepThinking) " Please think deeply. Start your response with your step-by-step reasoning process inside <thinking>...</thinking> XML tags. For example: <thinking>To answer your question...</thinking> Here is the answer." else "")
        val systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))

        val contents = mutableListOf<GeminiContent>()

        // Append historical turns (last 10 turns max, or 3 if in Power-Save mode)
        val maxHistory = if (_isPowerSaveActive.value) 3 else 10
        history.takeLast(maxHistory).forEach { turn ->
            contents.add(
                GeminiContent(
                    role = if (turn["role"] == "user") "user" else "model",
                    parts = listOf(GeminiPart(text = turn["content"] ?: ""))
                )
            )
        }

        // If context is present, insert or prepend it to the final prompt
        val enrichedPrompt = if (memoryContext.isNotEmpty()) {
            "$memoryContext\nUser prompt: $prompt"
        } else prompt

        // Ensure the last item matches the enriched final prompt
        if (contents.isNotEmpty() && contents.last().role == "user") {
            contents[contents.lastIndex] = GeminiContent(role = "user", parts = listOf(GeminiPart(text = enrichedPrompt)))
        } else {
            contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = enrichedPrompt))))
        }

        // Restrict output length in Power-Save mode to save network and local TTS generation energy
        val generationConfig = if (_isPowerSaveActive.value) {
            GeminiGenerationConfig(maxOutputTokens = 150)
        } else null

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = generationConfig
        )

        val apiResponse = ApiClients.geminiService.generateContent(
            model = "gemini-3.5-flash",
            apiKey = resolvedKey,
            request = request
        )

        apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API.")
    }

    private suspend fun callOfflineAI(prompt: String, history: List<Map<String, String>>): String = withContext(Dispatchers.IO) {
        val autoFallback = !_isOfflineMode.value && !_isInternetAvailable.value
        if (autoFallback) {
            repository.addLog("AI_Call", "Internet is unavailable! Automatically routing to local llama.cpp backend.")
        } else {
            repository.addLog("AI_Call", "Calling local AI Flask backend (Power-Save Active: ${_isPowerSaveActive.value})...")
        }

        // Gather relevant memory context to enrich the conversation prompt using hybrid semantic vector search!
        val maxMemories = if (_isPowerSaveActive.value) 1 else 5
        val semanticResults = retrieveSemanticMemories(prompt, limit = maxMemories)
        val memoryContext = if (semanticResults.isNotEmpty()) {
            "Relevant user memories to remember:\n" + semanticResults.joinToString("\n") { "- ${it.first.content}" } + "\n\n"
        } else ""

        val enrichedPrompt = if (memoryContext.isNotEmpty()) {
            "$memoryContext\nUser prompt: $prompt"
        } else prompt

        // Let's attempt to contact local flask backend first
        try {
            val maxHistory = if (_isPowerSaveActive.value) 3 else 10
            val request = ChatRequest(
                message = enrichedPrompt,
                history = history.takeLast(maxHistory)
            )
            val response = ApiClients.getBackendService(_backendUrl.value).chat(request)
            _backendHealth.value = true
            return@withContext response.response
        } catch (backendException: Exception) {
            repository.addLog("AI_Call", "Backend failed or unreachable. Trying direct llama.cpp completed API...")
            // If Flask fails, try direct completion on llama.cpp HTTP server
            try {
                val queryPrompt = "User: $enrichedPrompt\nAssistant:"
                // Restrict predict/generation length in Power-Save mode to save local device CPU
                val maxPredict = if (_isPowerSaveActive.value) 64 else 128
                val request = LlamaCompletionRequest(prompt = queryPrompt, nPredict = maxPredict)
                val response = ApiClients.getLlamaDirectService(_llamaUrl.value).complete(request)
                _backendHealth.value = true
                return@withContext response.content
            } catch (llamaException: Exception) {
                // If both fail, log details and fallback to an intelligent simulated offline response
                _backendHealth.value = false
                repository.addLog("AI_Call", "Offline AI server could not be reached.")

                val reasonPrefix = if (autoFallback) {
                    "I noticed your internet connection is unavailable, so I automatically attempted to route your message to Baby's private local offline AI (llama.cpp / Flask server).\n\n"
                } else {
                    "I am in Offline Mode, but I cannot connect to Baby's local AI Server.\n\n"
                }

                return@withContext reasonPrefix +
                        "**Troubleshooting Checklist:**\n" +
                        "1. Ensure llama.cpp or Flask server is running on your machine.\n" +
                        "2. Ensure your phone or emulator can access `${_backendUrl.value}` (use `10.0.2.2` in emulator to point to your PC's localhost).\n" +
                        "3. Start Flask api_server.py or run `llama-server -m models/tinyllama.gguf -c 512`.\n\n" +
                        "*Simulated fallback assistant response:* Hello there! I'm here to assist you. Once you connect your local llama.cpp server, I will possess fully private, offline intelligence!"
            }
        }
    }

    // --- Vector & Semantic Database Helpers ---

    private fun List<Float>.toEmbeddingString(): String = joinToString(",") { it.toString() }

    private fun String.toEmbeddingList(): List<Float> = split(",").mapNotNull { it.toFloatOrNull() }

    private suspend fun fetchEmbedding(text: String): List<Float>? = withContext(Dispatchers.IO) {
        val resolvedKey = _apiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (resolvedKey.isEmpty() || resolvedKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }
        try {
            val request = com.example.data.api.GeminiEmbeddingRequest(
                content = GeminiContent(parts = listOf(GeminiPart(text = text)))
            )
            val response = ApiClients.geminiService.embedContent(
                model = "text-embedding-004",
                apiKey = resolvedKey,
                request = request
            )
            response.embedding?.values
        } catch (e: Exception) {
            Log.e("BabyViewModel", "Failed to fetch embedding: ${e.message}")
            null
        }
    }

    private fun cosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        return if (normA > 0f && normB > 0f) {
            (dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble()))).toFloat()
        } else {
            0f
        }
    }

    suspend fun retrieveSemanticMemories(query: String, limit: Int = 5): List<Pair<MemoryEntity, Float>> {
        val allDbMemories = memories.value
        val queryVector = fetchEmbedding(query)

        if (queryVector == null) {
            // Fallback to keyword search in SQLite when offline or no API key
            val keywordResults = repository.searchMemories(query)
            return keywordResults.take(limit).map { Pair(it, 1.0f) }
        }

        val scoredResults = allDbMemories.mapNotNull { memory ->
            val embeddingStr = memory.embedding
            if (!embeddingStr.isNullOrEmpty()) {
                val vector = embeddingStr.toEmbeddingList()
                val score = cosineSimilarity(queryVector, vector)
                Pair(memory, score)
            } else {
                null
            }
        }

        // Sort by similarity score descending
        return scoredResults.sortedByDescending { it.second }.take(limit)
    }

    private fun initializeMemoriesWithEmbeddings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(3000) // Wait for initialization to settle
                val allMemories = memories.value
                val missingEmbeddings = allMemories.filter { it.embedding.isNullOrEmpty() }
                if (missingEmbeddings.isNotEmpty()) {
                    repository.addLog("Memory", "Vector Database: Found ${missingEmbeddings.size} memories missing embeddings. Processing...")
                    missingEmbeddings.forEach { memory ->
                        val vector = fetchEmbedding(memory.content)
                        if (vector != null) {
                            val updatedMemory = memory.copy(embedding = vector.toEmbeddingString())
                            repository.updateMemory(updatedMemory)
                        }
                        delay(1000) // rate limit guard
                    }
                    repository.addLog("Memory", "Vector Database: Semantic indexing completed.")
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to batch embed existing memories: ${e.message}", e)
            }
        }
    }

    // --- Automatic Long-Term Memory Extraction ---

    private fun extractMemoryInBackground(userMsg: String, assistantMsg: String) {
        if (_isPowerSaveActive.value) {
            Log.d("BabyViewModel", "Skipping background memory extraction during Power-Save Mode.")
            return
        }
        val resolvedKey = _apiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (resolvedKey.isEmpty() || resolvedKey == "MY_GEMINI_API_KEY") return // Skip if no online key available

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val memoryPrompt = "Analyze the following message turn. " +
                        "If the user shares an important fact, preference, preference rating, dog name, habit, or specific details about themselves, extract it into a 1-sentence statement starting with 'User...'. " +
                        "If there is no personal info shared, return 'NONE'.\n\n" +
                        "User: $userMsg\n" +
                        "Assistant: $assistantMsg\n\n" +
                        "Output Statement:"

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = memoryPrompt))))
                )

                val response = ApiClients.geminiService.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = resolvedKey,
                    request = request
                )

                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                if (!text.isNullOrEmpty() && !text.contains("NONE", ignoreCase = true)) {
                    val finalMemoryText = text.replace("Statement:", "").trim()
                    val embeddingStr = fetchEmbedding(finalMemoryText)?.toEmbeddingString()
                    repository.addMemory(finalMemoryText, "FACT", 4, embeddingStr)
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to auto extract memory: ${e.message}")
            }
        }
    }

    // --- Health Check ---

    fun checkBackendHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val health = ApiClients.getBackendService(_backendUrl.value).checkHealth()
                _backendHealth.value = health.llamaCppConnected || health.status == "healthy"
                repository.addLog("System", "Backend health status: Status=${health.status}, llama.cpp=${health.llamaCppConnected}")
            } catch (e: Exception) {
                _backendHealth.value = false
                repository.addLog("System", "Backend unreachable at ${_backendUrl.value}")
            }
        }
    }

    // --- Speech Control Methods ---

    fun startListening() {
        voiceManager?.startListening()
    }

    fun stopListening() {
        voiceManager?.stopListening()
    }

    fun cancelListening() {
        _partialSpeechText.value = ""
        voiceManager?.cancelListening()
        _assistantState.value = AssistantState.IDLE
    }

    fun speak(text: String) {
        voiceManager?.speak(text, rate = _voiceRate.value, pitch = _voicePitch.value)
    }

    fun stopSpeaking() {
        voiceManager?.stopSpeaking()
        _assistantState.value = AssistantState.IDLE
    }

    // --- Settings Saving Logic ---

    fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.saveSetting(key, value)
            when (key) {
                "is_auto_switch_enabled" -> {
                    _isAutoSwitchEnabled.value = value.toBoolean()
                    if (value.toBoolean()) {
                        val targetOfflineMode = !_isInternetAvailable.value
                        if (_isOfflineMode.value != targetOfflineMode) {
                            _isOfflineMode.value = targetOfflineMode
                            repository.saveSetting("is_offline_mode", targetOfflineMode.toString())
                            repository.addLog(
                                "Network_AutoSwitch",
                                "Auto-switch enabled: Switched AI core to ${if (targetOfflineMode) "Local Llama.cpp (Offline)" else "Gemini Cloud (Online)"}"
                            )
                        }
                    }
                }
                "is_offline_mode" -> _isOfflineMode.value = value.toBoolean()
                "selected_provider" -> _selectedProvider.value = value
                "api_key" -> _apiKey.value = value
                "backend_url" -> {
                    _backendUrl.value = value
                    checkBackendHealth()
                }
                "llama_url" -> _llamaUrl.value = value
                "voice_pitch" -> {
                    val pitchVal = value.toFloatOrNull() ?: 1.0f
                    _voicePitch.value = pitchVal
                    checkIfCustomStyle()
                }
                "voice_rate" -> {
                    val rateVal = value.toFloatOrNull() ?: 1.0f
                    _voiceRate.value = rateVal
                    checkIfCustomStyle()
                }
                "voice_style" -> {
                    _voiceStyle.value = value
                    applySpeechStylePreset(value)
                }
                "selected_voice_name" -> {
                    _selectedVoiceName.value = value
                    voiceManager?.selectedVoiceName = value.ifEmpty { null }
                }
                "is_continuous_mode" -> _isContinuousMode.value = value.toBoolean()
                "silence_amplitude_threshold" -> {
                    val floatVal = value.toFloatOrNull() ?: 2.0f
                    _silenceThreshold.value = floatVal
                    voiceManager?.amplitudeThreshold = floatVal
                }
                "force_power_save" -> {
                    _forcePowerSave.value = value.toBoolean()
                    updatePowerSaveState()
                }
                "auto_power_save" -> {
                    _autoPowerSave.value = value.toBoolean()
                    updatePowerSaveState()
                }
            }
        }
    }

    private fun applySpeechStylePreset(style: String) {
        val (pitch, rate) = when (style) {
            "default" -> Pair(1.0f, 1.0f)
            "baby" -> Pair(1.35f, 1.05f)
            "deep" -> Pair(0.75f, 0.95f)
            "playful" -> Pair(1.15f, 1.15f)
            "calm" -> Pair(0.9f, 0.8f)
            else -> return // custom / no-op
        }
        viewModelScope.launch {
            saveSetting("voice_pitch", pitch.toString())
            saveSetting("voice_rate", rate.toString())
        }
    }

    private fun checkIfCustomStyle() {
        val pitch = _voicePitch.value
        val rate = _voiceRate.value
        val style = when {
            pitch == 1.0f && rate == 1.0f -> "default"
            pitch == 1.35f && rate == 1.05f -> "baby"
            pitch == 0.75f && rate == 0.95f -> "deep"
            pitch == 1.15f && rate == 1.15f -> "playful"
            pitch == 0.9f && rate == 0.8f -> "calm"
            else -> "custom"
        }
        if (_voiceStyle.value != style) {
            _voiceStyle.value = style
            viewModelScope.launch {
                repository.saveSetting("voice_style", style)
            }
        }
    }

    private fun updatePowerSaveState() {
        val lowBattery = _batteryLevel.value < 20 && !_isCharging.value
        val autoActive = _autoPowerSave.value && lowBattery
        val active = _forcePowerSave.value || autoActive
        
        if (_isPowerSaveActive.value != active) {
            _isPowerSaveActive.value = active
            viewModelScope.launch {
                repository.addLog("System", "Power-Save Mode changed to: $active (Battery: ${_batteryLevel.value}%, Charging: ${_isCharging.value})")
            }
            
            // Adjust background tasks / voice manager
            voiceManager?.isPowerSaveMode = active
            
            // Adjust health check frequency if needed
            resetHealthCheckTimer()
        }
    }

    private fun startHealthCheckTimer() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch {
            while (isActive) {
                checkBackendHealth()
                val delayMs = if (_isPowerSaveActive.value) {
                    120000L // 2 minutes in power-save mode
                } else {
                    30000L // 30 seconds in normal mode
                }
                delay(delayMs)
            }
        }
    }

    private fun resetHealthCheckTimer() {
        startHealthCheckTimer()
    }

    fun addManualMemory(content: String, type: String, importance: Int) {
        viewModelScope.launch {
            val embeddingStr = fetchEmbedding(content)?.toEmbeddingString()
            repository.addMemory(content, type, importance, embeddingStr)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // --- Device Control settings managers ---
    fun updateBackgroundServiceState(enabled: Boolean) {
        _backgroundServiceEnabled.value = enabled
        viewModelScope.launch {
            repository.saveSetting("background_service", enabled.toString())
            val intent = Intent(getApplication(), BabyAssistantService::class.java)
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            } else {
                getApplication<Application>().stopService(intent)
            }
        }
    }

    fun saveDeviceControlSetting(key: String, value: Boolean) {
        viewModelScope.launch {
            repository.saveSetting(key, value.toString())
            when (key) {
                "wake_word_enabled" -> _wakeWordEnabled.value = value
                "background_listening_enabled" -> _backgroundListeningEnabled.value = value
                "flashlight_control_enabled" -> _flashlightControlEnabled.value = value
                "app_launching_enabled" -> _appLaunchingEnabled.value = value
                "notification_access_enabled" -> _notificationAccessEnabled.value = value
                "storage_access_enabled" -> _storageAccessEnabled.value = value
                "microphone_access_enabled" -> _microphoneAccessEnabled.value = value
                "camera_access_enabled" -> _cameraAccessEnabled.value = value
                "contact_access_enabled" -> _contactAccessEnabled.value = value
                "sms_access_enabled" -> _smsAccessEnabled.value = value
                "boot_on_startup" -> _bootOnStartupEnabled.value = value
                "battery_optimization_enabled" -> _batteryOptimizationEnabled.value = value
                "wake_phrase_hey_baby" -> _wakePhraseHeyBaby.value = value
                "wake_phrase_hi_baby" -> _wakePhraseHiBaby.value = value
                "wake_phrase_hello_baby" -> _wakePhraseHelloBaby.value = value
                "wake_phrase_baby" -> _wakePhraseBaby.value = value
            }
            if (_backgroundServiceEnabled.value) {
                val intent = Intent(getApplication(), BabyAssistantService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        }
    }

    fun saveCustomWakePhrase(phrase: String) {
        _customWakePhrase.value = phrase
        viewModelScope.launch {
            repository.saveSetting("custom_wake_phrase", phrase)
            if (_backgroundServiceEnabled.value) {
                val intent = Intent(getApplication(), BabyAssistantService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        }
    }

    fun executeLocalDeviceControl(text: String): String? {
        val lowerText = text.lowercase(java.util.Locale.ROOT)

        // Flashlight
        if (_flashlightControlEnabled.value) {
            if (lowerText.contains("turn on flashlight") || lowerText.contains("turn on torch") || lowerText.contains("flashlight on") || lowerText.contains("torch on")) {
                return deviceControlManager.setFlashlight(true)
            }
            if (lowerText.contains("turn off flashlight") || lowerText.contains("turn off torch") || lowerText.contains("flashlight off") || lowerText.contains("torch off")) {
                return deviceControlManager.setFlashlight(false)
            }
        }

        // Launch Apps
        if (_appLaunchingEnabled.value) {
            if (lowerText.startsWith("open ") || lowerText.startsWith("launch ")) {
                val appName = text.substringAfter("open").substringAfter("launch").trim()
                return deviceControlManager.launchApp(appName)
            }
        }

        // Music
        if (lowerText.contains("play music") || lowerText.contains("resume music")) {
            return deviceControlManager.controlMusic("play")
        }
        if (lowerText.contains("pause music") || lowerText.contains("stop music")) {
            return deviceControlManager.controlMusic("pause")
        }
        if (lowerText.contains("next song") || lowerText.contains("next track")) {
            return deviceControlManager.controlMusic("next")
        }
        if (lowerText.contains("previous song") || lowerText.contains("previous track")) {
            return deviceControlManager.controlMusic("previous")
        }
        if (lowerText.contains("volume up") || lowerText.contains("louder")) {
            return deviceControlManager.controlMusic("volume_up")
        }
        if (lowerText.contains("volume down") || lowerText.contains("quieter")) {
            return deviceControlManager.controlMusic("volume_down")
        }

        // Navigation
        if (lowerText.startsWith("navigate to ") || lowerText.startsWith("directions to ")) {
            val dest = text.substringAfter("navigate to").substringAfter("directions to").trim()
            return deviceControlManager.openMaps(dest)
        }
        if (lowerText.contains("navigate home")) {
            return deviceControlManager.openMaps("Home")
        }
        if (lowerText.contains("navigate to work")) {
            return deviceControlManager.openMaps("Work")
        }
        if (lowerText.contains("search nearby restaurants") || lowerText.contains("restaurants nearby")) {
            return deviceControlManager.openMaps("restaurants")
        }

        // Device states (Bluetooth, Wifi, DND)
        if (lowerText.contains("turn bluetooth on") || lowerText.contains("bluetooth on")) {
            return deviceControlManager.setBluetoothState(true)
        }
        if (lowerText.contains("turn bluetooth off") || lowerText.contains("bluetooth off")) {
            return deviceControlManager.setBluetoothState(false)
        }
        if (lowerText.contains("turn wifi on") || lowerText.contains("wifi on")) {
            return deviceControlManager.setWifiState(true)
        }
        if (lowerText.contains("turn wifi off") || lowerText.contains("wifi off")) {
            return deviceControlManager.setWifiState(false)
        }
        if (lowerText.contains("enable do not disturb") || lowerText.contains("turn dnd on") || lowerText.contains("dnd on")) {
            return deviceControlManager.setDNDMode(true)
        }
        if (lowerText.contains("disable do not disturb") || lowerText.contains("turn dnd off") || lowerText.contains("dnd off")) {
            return deviceControlManager.setDNDMode(false)
        }

        // Brightness
        if (lowerText.contains("brightness to ")) {
            val raw = lowerText.substringAfter("brightness to").trim().removeSuffix("%").trim()
            val parsed = raw.toFloatOrNull()
            if (parsed != null) {
                val value = (parsed / 100f).coerceIn(0f, 1f)
                return deviceControlManager.setBrightness(value)
            }
        }

        // Web Search
        if (lowerText.startsWith("search google for ") || lowerText.startsWith("search for ")) {
            val q = text.substringAfter("search google for").substringAfter("search for").trim()
            return deviceControlManager.searchWeb(q, "google")
        }
        if (lowerText.startsWith("search youtube for ")) {
            val q = text.substringAfter("search youtube for").trim()
            return deviceControlManager.searchWeb(q, "youtube")
        }
        if (lowerText.startsWith("search wikipedia for ")) {
            val q = text.substringAfter("search wikipedia for").trim()
            return deviceControlManager.searchWeb(q, "wikipedia")
        }
        if (lowerText.startsWith("open website ")) {
            val site = text.substringAfter("open website").trim()
            return deviceControlManager.openWebsite(site)
        }

        // Settings Shortcuts
        if (lowerText.contains("open wifi settings")) return deviceControlManager.openSettingsScreen("wifi")
        if (lowerText.contains("open bluetooth settings")) return deviceControlManager.openSettingsScreen("bluetooth")
        if (lowerText.contains("open battery settings")) return deviceControlManager.openSettingsScreen("battery")
        if (lowerText.contains("open developer settings") || lowerText.contains("open developer options")) return deviceControlManager.openSettingsScreen("developer")
        if (lowerText.contains("open accessibility settings")) return deviceControlManager.openSettingsScreen("accessibility")
        if (lowerText.contains("open application settings")) return deviceControlManager.openSettingsScreen("applications")

        // Clipboard
        if (lowerText.contains("read clipboard") || lowerText.contains("what is in my clipboard")) {
            return "In your clipboard: " + deviceControlManager.readClipboard()
        }

        // Contacts / Calls
        if (_contactAccessEnabled.value) {
            if (lowerText.startsWith("call ")) {
                val contactName = text.substringAfter("call").trim()
                val contacts = deviceControlManager.searchContacts(contactName)
                return if (contacts.isNotEmpty()) {
                    val contact = contacts[0]
                    deviceControlManager.makeCall(contact.phoneNumber)
                    "Placing call to ${contact.name} at ${contact.phoneNumber}."
                } else {
                    "No contact found matching '$contactName'."
                }
            }
            if (lowerText.contains("search contact ") || lowerText.contains("find contact ")) {
                val q = text.substringAfter("search contact").substringAfter("find contact").trim()
                val list = deviceControlManager.searchContacts(q)
                return if (list.isNotEmpty()) {
                    "Found contacts:\n" + list.joinToString("\n") { "- ${it.name}: ${it.phoneNumber}" }
                } else {
                    "No contacts found matching '$q'."
                }
            }
        }

        // SMS
        if (_smsAccessEnabled.value) {
            if (lowerText.startsWith("send sms to ") || lowerText.startsWith("text ")) {
                val parts = text.substringAfter("send sms to").substringAfter("text").trim().split(" ", limit = 2)
                if (parts.size >= 2) {
                    val contactName = parts[0]
                    val msg = parts[1]
                    val contacts = deviceControlManager.searchContacts(contactName)
                    if (contacts.isNotEmpty()) {
                        val phone = contacts[0].phoneNumber
                        return deviceControlManager.sendSMS(phone, msg)
                    }
                }
            }
        }

        // Camera
        if (_cameraAccessEnabled.value) {
            if (lowerText.contains("open camera")) return deviceControlManager.openSystemCamera(false)
            if (lowerText.contains("record video")) return deviceControlManager.openSystemCamera(true)
        }

        return null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e("BabyViewModel", "Failed to unregister battery receiver", e)
        }
        healthCheckJob?.cancel()
        voiceManager?.destroy()
    }
}

suspend fun callOnlineAIWrapper(
    prompt: String,
    history: List<Map<String, String>>,
    apiKey: String,
    repository: BabyRepository?
): String = withContext(Dispatchers.IO) {
    val resolvedKey = apiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
    if (resolvedKey.isEmpty() || resolvedKey == "MY_GEMINI_API_KEY") {
        throw IllegalStateException("API Key is missing.")
    }

    repository?.addLog("AI_Call", "Calling Gemini API in background service...")

    val systemInstructionText = "You are Baby, a natural, emotionally intelligent offline-first AI assistant for Android. Keep your answers concise, engaging, and friendly."
    val systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))

    val contents = mutableListOf<GeminiContent>()
    history.takeLast(5).forEach { turn ->
        contents.add(
            GeminiContent(
                role = if (turn["role"] == "user") "user" else "model",
                parts = listOf(GeminiPart(text = turn["content"] ?: ""))
            )
        )
    }

    if (contents.isNotEmpty() && contents.last().role == "user") {
        contents[contents.lastIndex] = GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))
    } else {
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt))))
    }

    val request = GeminiRequest(
        contents = contents,
        systemInstruction = systemInstruction
    )

    val apiResponse = ApiClients.geminiService.generateContent(
        model = "gemini-3.5-flash",
        apiKey = resolvedKey,
        request = request
    )

    apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: throw Exception("Empty response from Gemini API.")
}

suspend fun callOfflineAIWrapper(
    prompt: String,
    history: List<Map<String, String>>,
    repository: BabyRepository?
): String = withContext(Dispatchers.IO) {
    val backendUrl = repository?.getSetting("backend_url", "http://10.0.2.2:5000/") ?: "http://10.0.2.2:5000/"
    val llamaUrl = repository?.getSetting("llama_url", "http://10.0.2.2:8080/") ?: "http://10.0.2.2:8080/"

    repository?.addLog("AI_Call", "Calling local AI Flask backend in background service...")

    try {
        val request = ChatRequest(
            message = prompt,
            history = history.takeLast(5)
        )
        val response = ApiClients.getBackendService(backendUrl).chat(request)
        return@withContext response.response
    } catch (backendException: Exception) {
        try {
            val queryPrompt = "User: $prompt\nAssistant:"
            val request = LlamaCompletionRequest(prompt = queryPrompt, nPredict = 64)
            val response = ApiClients.getLlamaDirectService(llamaUrl).complete(request)
            return@withContext response.content
        } catch (llamaException: Exception) {
            repository?.addLog("AI_Call", "Offline AI server could not be reached in background.")
            return@withContext "I am running offline in the background, but my local AI server is currently unreachable. How else can I help you control your device?"
        }
    }
}

