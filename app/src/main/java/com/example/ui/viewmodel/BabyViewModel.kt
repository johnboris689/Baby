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
import com.example.data.api.GeminiInlineData
import com.example.data.api.GeminiFileData
import com.example.data.api.GeminiFileUploader
import com.example.data.api.GeminiRequest
import com.example.data.api.GeminiResponse
import com.example.data.api.GeminiGenerationConfig
import com.example.data.model.Attachment
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.AutomationRuleEntity
import com.example.data.repository.BabyRepository
import com.example.data.local.DeviceControlManager
import com.example.data.local.CommandRoutingEngine
import com.example.data.local.RoutingResult
import com.example.data.companion.*
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

    private val _moodSignal = MutableStateFlow(MoodSignal(UserEmotion.NEUTRAL, 0.0f, "warm"))
    val moodSignal: StateFlow<MoodSignal> = _moodSignal.asStateFlow()

    private val _partialSpeechText = MutableStateFlow("")
    val partialSpeechText: StateFlow<String> = _partialSpeechText.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.value = _pendingAttachments.value + attachment
    }

    fun removeAttachment(uri: android.net.Uri) {
        _pendingAttachments.value = _pendingAttachments.value.filter { it.uri != uri }
    }

    fun clearAttachments() {
        _pendingAttachments.value = emptyList()
    }

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
    private val deviceControlManager = DeviceControlManager(application)
    private val routingEngine = CommandRoutingEngine(deviceControlManager)
    private val networkMonitor = NetworkMonitor(application)
    private val _isInternetAvailable = MutableStateFlow(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow("gemini-3.6-flash")
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

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
            }
        }

        // Load initial settings from DB
        viewModelScope.launch {
            _apiKey.value = repository.getSetting("api_key", "")
            _geminiModel.value = repository.getSetting("gemini_model", "gemini-3.6-flash")
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
            _wakePhraseBaby.value = repository.getSetting("wake_phrase_baby", "false").toBoolean()
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
                _moodSignal.value = MoodRadar.detect(text, _rmsDb.value)
            },
            onFinalSpeech = { text ->
                _partialSpeechText.value = ""
                _moodSignal.value = MoodRadar.detect(text, _rmsDb.value)
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
                val current = _partialSpeechText.value
                if (current.isNotBlank()) _moodSignal.value = MoodRadar.detect(current, rms)
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
                    "ADD_LOG" -> {
                        repository.addLog("System", "Automation script ran successfully.")
                    }
                }
            }
        }
    }

    fun sendMessage(content: String, isRegeneration: Boolean = false) {
        val attachments = _pendingAttachments.value
        if (content.trim().isEmpty() && attachments.isEmpty() && !isRegeneration) return
        val convId = _activeConversationId.value ?: return

        _pendingAttachments.value = emptyList()

        // Cancel previous active jobs
        activeGenerationJob?.cancel()

        activeGenerationJob = viewModelScope.launch {
            var finalPrompt = content
            try {
                // Save User Message if not a regeneration
                val displayPrompt = if (attachments.isNotEmpty()) {
                    val attNames = attachments.joinToString(", ") { it.name }
                    if (content.trim().isNotEmpty()) "$content\n[Attached: $attNames]" else "[Attached: $attNames]"
                } else content

                finalPrompt = if (!isRegeneration) {
                    repository.addMessage(convId, "user", displayPrompt)
                    // Infinite-memory drive: preserve the user's complete text as a searchable conversation detail.
                    if (content.trim().isNotEmpty()) {
                        repository.addMemory(
                            content = "Conversation detail: ${content.trim().take(4000)}",
                            type = "CONVERSATION_DETAIL",
                            importance = 1
                        )
                        RelationshipMemoryExtractor.extractRelationshipFacts(content).forEach { (fact, type) ->
                            repository.addMemory(fact, type, if (type == "IMPORTANT") 5 else 4)
                        }
                    }
                    content
                } else {
                    activeMessages.value.lastOrNull { it.role == "user" }?.content ?: content
                }

                _assistantState.value = AssistantState.THINKING

                // Intercept and run local device controls if no binary attachments are present
                if (attachments.isEmpty()) {
                    val localResult = executeLocalDeviceControl(finalPrompt)
                    if (localResult != null) {
                        repository.addLog("Device_Control", "Executed: $localResult")
                        simulateStreamingText(localResult, convId)
                        return@launch
                    }
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

                val responseText = callOnlineAI(finalPrompt, activeMsgHistory, attachments)

                // Simulate token-by-token response streaming
                simulateStreamingText(responseText, convId)

            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.addLog("AI", "Generation job cancelled/interrupted.")
                throw e
            } catch (e: Exception) {
                repository.addLog("AI_Error", "Failed to generate online AI response: ${e.message}. Using offline companion logic.")
                val detectedEmotion = EmotionDetector.detectEmotion(finalPrompt)
                val offlineText = OfflineCompanionEngine.generateOfflineResponse(
                    prompt = finalPrompt,
                    memories = memories.value.take(18),
                    emotion = detectedEmotion
                )
                simulateStreamingText(offlineText, convId)
            } finally {
                // ZIP media is materialized only for the current request; remove it after the request finishes.
                attachments.flatMap { it.extractedMedia }.forEach { media ->
                    runCatching { media.file.delete() }
                }
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
                // The API response has already arrived. Keep the visual stream fast instead of adding seconds of artificial latency.
                val delayMs = (words[i].length * 1L).coerceIn(0L, 8L)
                if (delayMs > 0) delay(delayMs)
            }

            val savedText = _streamingMessageText.value ?: ""
            if (savedText.isNotEmpty()) {
                repository.addMessage(convId, "assistant", savedText)
                speak(savedText)
                extractMemoryInBackground(savedText.take(4000), savedText)
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

    private suspend fun callOnlineAI(
        prompt: String,
        history: List<Map<String, String>>,
        attachments: List<Attachment> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val detectedEmotion = EmotionDetector.detectEmotion(prompt)
        val resolvedKey = _apiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }

        if (resolvedKey.isEmpty() || resolvedKey == "MY_GEMINI_API_KEY" || !_isInternetAvailable.value) {
            return@withContext OfflineCompanionEngine.generateOfflineResponse(
                prompt = prompt,
                memories = memories.value,
                emotion = detectedEmotion
            )
        }

        repository.addLog("AI_Call", "Calling Gemini API with ${attachments.size} attachments...")

        // Fast local memory retrieval: avoid an extra network embedding round-trip on every chat message.
        // Existing embeddings are still generated in the background and can be used later for offline ranking.
        val maxMemories = if (_isPowerSaveActive.value) 4 else 10
        val keywordMemories = repository.searchMemories(prompt, maxMemories)
        val importantMemories = repository.getRecentImportantMemories(if (_isPowerSaveActive.value) 4 else 10)
        val selectedMemories = (keywordMemories + importantMemories)
            .distinctBy { it.content }
            .sortedWith(compareByDescending<MemoryEntity> { it.importance }.thenByDescending { it.timestamp })
            .take(18)

        val memoryContext = if (selectedMemories.isNotEmpty()) {
            "Relevant user memories to remember:\n" + selectedMemories.joinToString("\n") { "- ${it.content}" } + "\n\n"
        } else ""

        val memoryList = selectedMemories
        val currentMood = MoodRadar.detect(prompt, _rmsDb.value)
        _moodSignal.value = currentMood

        val isPowerSave = _isPowerSaveActive.value
        val isDeepThinking = _thinkingMode.value == "deep"

        val systemInstructionText = CompanionPersonality.buildSystemPrompt(
            memories = memoryList,
            detectedEmotion = detectedEmotion,
            isPowerSave = isPowerSave,
            isDeepThinking = isDeepThinking,
            moodSignal = currentMood
        )

        val systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))

        val contents = mutableListOf<GeminiContent>()

        // Append historical turns
        val maxHistory = if (_isPowerSaveActive.value) 3 else 10
        history.takeLast(maxHistory).forEach { turn ->
            contents.add(
                GeminiContent(
                    role = if (turn["role"] == "user") "user" else "model",
                    parts = listOf(GeminiPart(text = turn["content"] ?: ""))
                )
            )
        }

        // Build current turn parts
        val partsList = mutableListOf<GeminiPart>()

        // Small media is sent inline. Large media is uploaded through Gemini Files API.
        // Text, DOCX and ZIP files are expanded locally so Baby can read their contents.
        var docContext = ""
        attachments.forEach { att ->
            if (!att.base64Data.isNullOrEmpty()) {
                partsList.add(GeminiPart(inlineData = GeminiInlineData(mimeType = att.mimeType, data = att.base64Data)))
            } else if (att.isGeminiMedia) {
                try {
                    val uploaded = GeminiFileUploader.upload(
                        context = getApplication<Application>(),
                        uri = att.uri,
                        apiKey = resolvedKey,
                        mimeType = att.mimeType,
                        displayName = att.name
                    )
                    partsList.add(GeminiPart(fileData = GeminiFileData(mimeType = uploaded.mimeType, fileUri = uploaded.uri)))
                } catch (e: Exception) {
                    docContext += "\n[Attached media: ${att.name}]\nUpload failed: ${e.message ?: "unknown error"}.\n"
                }
            }

            if (!att.extractedText.isNullOrBlank()) {
                docContext += "\n[Attached file: ${att.name}]\n${att.extractedText}\n"
            }

            // ZIPs can contain their own images, videos, PDFs, spreadsheets and presentations.
            att.extractedMedia.forEach { media ->
                try {
                    val uploaded = GeminiFileUploader.uploadFile(
                        file = media.file,
                        apiKey = resolvedKey,
                        mimeType = media.mimeType,
                        displayName = "${att.name} / ${media.name}"
                    )
                    partsList.add(GeminiPart(fileData = GeminiFileData(mimeType = uploaded.mimeType, fileUri = uploaded.uri)))
                } catch (e: Exception) {
                    docContext += "\n[ZIP binary entry: ${media.name}]\nUpload failed: ${e.message ?: "unknown error"}.\n"
                }
            }
        }

        var finalPromptText = if (memoryContext.isNotEmpty()) "$memoryContext\nUser prompt: $prompt" else prompt
        if (docContext.isNotEmpty()) {
            finalPromptText += "\n\nAttached File Contents:\n$docContext"
        }

        partsList.add(GeminiPart(text = finalPromptText))

        contents.add(GeminiContent(role = "user", parts = partsList))

        val generationConfig = if (_isPowerSaveActive.value) {
            GeminiGenerationConfig(maxOutputTokens = 150)
        } else null

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = generationConfig
        )

        val modelToUse = _geminiModel.value.ifEmpty { "gemini-3.6-flash" }

        val apiResponse = callGeminiWithExponentialBackoff(
            model = modelToUse,
            apiKey = resolvedKey,
            request = request
        )

        apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API.")
    }

    // --- Vector & Semantic Database Helpers ---

    private fun List<Float>.toEmbeddingString(): String = joinToString(",") { it.toString() }

    private fun String.toEmbeddingList(): List<Float> = split(",").mapNotNull { it.toFloatOrNull() }

    private suspend fun fetchEmbedding(text: String): List<Float>? = withContext(Dispatchers.IO) {
        // The legacy text-embedding-004 model was shut down. Memory retrieval is now local-first,
        // so a dead embedding endpoint can never slow or break normal chat.
        null
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
        // Intentionally local-first. Existing memory records remain fully searchable without a network embedding call.
    }

    // --- Automatic Long-Term Memory Extraction ---

    private fun extractMemoryInBackground(userMsg: String, assistantMsg: String) {
        if (_isPowerSaveActive.value) {
            Log.d("BabyViewModel", "Skipping background memory extraction during Power-Save Mode.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Local Regex Relationship Fact Extraction
                val localFacts = RelationshipMemoryExtractor.extractRelationshipFacts(userMsg)
                localFacts.forEach { (factText, factType) ->
                    repository.addMemory(factText, factType, if (factType == "IMPORTANT") 5 else 4)
                }

                // 2. Online Deep Fact Extraction via Gemini if Key is Present
                val resolvedKey = _apiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
                if (resolvedKey.isNotEmpty() && resolvedKey != "MY_GEMINI_API_KEY") {
                    val memoryPrompt = "Analyze this conversation turn for durable user memory. Extract every useful personal detail, preference, routine, goal, relationship detail, favorite item, explicit remember-this request, or stable fact. Return one concise memory per line, each beginning with 'User:'. Do not invent anything. If nothing durable is present, return NONE.\n\nUser: $userMsg\nAssistant: $assistantMsg\n\nMemories:"

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = memoryPrompt))))
                    )

                    val response = ApiClients.geminiService.generateContent(
                        model = "gemini-3.5-flash-lite",
                        apiKey = resolvedKey,
                        request = request
                    )

                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (!text.isNullOrEmpty() && !text.equals("NONE", ignoreCase = true)) {
                        text.lines()
                            .map { it.trim().removePrefix("-").trim() }
                            .filter { it.isNotBlank() && !it.equals("NONE", ignoreCase = true) }
                            .take(12)
                            .forEach { line ->
                                repository.addMemory(line.removePrefix("User:").trim(), "FACT", 4)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to auto extract memory: ${e.message}")
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
        val mood = _moodSignal.value
        val rateMultiplier = when (mood.style) {
            "cozy-whisper" -> 0.86f
            "calm-grounding" -> 0.92f
            "energetic" -> 1.10f
            else -> 1.0f
        }
        val pitchMultiplier = when (mood.style) {
            "cozy-whisper" -> 0.96f
            "energetic" -> 1.04f
            else -> 1.0f
        }
        voiceManager?.speak(
            text,
            rate = (_voiceRate.value * rateMultiplier).coerceIn(0.65f, 1.35f),
            pitch = (_voicePitch.value * pitchMultiplier).coerceIn(0.75f, 1.25f)
        )
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
                "api_key" -> _apiKey.value = value
                "gemini_model" -> _geminiModel.value = value
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
        }
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
        val result = routingEngine.routeAndExecute(text)
        return if (result is RoutingResult.LocalCommand) {
            result.responseText
        } else {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e("BabyViewModel", "Failed to unregister battery receiver", e)
        }
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
        return@withContext "Please enter your Gemini API Key in Settings to enable AI responses."
    }

    repository?.addLog("AI_Call", "Calling Gemini API in background service...")

    val systemInstructionText = "You are Baby, a natural, emotionally intelligent AI assistant for Android. Keep your answers concise, engaging, and friendly."
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

    val modelToUse = repository?.getSetting("gemini_model", "gemini-3.6-flash") ?: "gemini-3.6-flash"

    val apiResponse = callGeminiWithExponentialBackoff(
        model = modelToUse,
        apiKey = resolvedKey,
        request = request
    )

    apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        ?: throw Exception("Empty response from Gemini API.")
}

suspend fun callGeminiWithExponentialBackoff(
    model: String,
    apiKey: String,
    request: GeminiRequest,
    maxRetries: Int = 3
): GeminiResponse = withContext(Dispatchers.IO) {
    var delayMs = 1000L
    for (attempt in 0 until maxRetries) {
        try {
            return@withContext ApiClients.geminiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
        } catch (e: retrofit2.HttpException) {
            if ((e.code() == 429 || e.code() >= 500) && attempt < maxRetries - 1) {
                Log.w("GeminiAPI", "HTTP ${e.code()} error. Retrying attempt ${attempt + 1} in ${delayMs}ms...")
                delay(delayMs)
                delayMs *= 2
            } else {
                throw e
            }
        } catch (e: Exception) {
            if (attempt < maxRetries - 1) {
                Log.w("GeminiAPI", "Network exception ${e.message}. Retrying attempt ${attempt + 1} in ${delayMs}ms...")
                delay(delayMs)
                delayMs *= 2
            } else {
                throw e
            }
        }
    }
    throw Exception("Gemini API call failed after $maxRetries attempts.")
}
