package com.example.data.repository

import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BabyRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val settingDao: SettingDao,
    private val logDao: LogDao,
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val automationRuleDao: AutomationRuleDao
) {
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val allSettingsFlow: Flow<List<SettingEntity>> = settingDao.getAllSettingsFlow()
    val allLogs: Flow<List<LogEntity>> = logDao.getAllLogs()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val allRules: Flow<List<AutomationRuleEntity>> = automationRuleDao.getAllRules()

    suspend fun updateMessageContent(id: Long, content: String) = withContext(Dispatchers.IO) {
        messageDao.updateMessageContent(id, content)
    }

    suspend fun updateMessageReaction(id: Long, reaction: String?) = withContext(Dispatchers.IO) {
        messageDao.updateMessageReaction(id, reaction)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(id)
    }

    suspend fun addNote(title: String, content: String) = withContext(Dispatchers.IO) {
        noteDao.insertNote(NoteEntity(title = title, content = content))
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteById(id)
    }

    suspend fun addTask(title: String) = withContext(Dispatchers.IO) {
        taskDao.insertTask(TaskEntity(title = title))
    }

    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        taskDao.updateTaskStatus(id, isCompleted)
    }

    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        taskDao.deleteTaskById(id)
    }

    suspend fun addRule(trigger: String, action: String, isEnabled: Boolean = false) = withContext(Dispatchers.IO) {
        automationRuleDao.insertRule(AutomationRuleEntity(trigger = trigger, action = action, isEnabled = isEnabled))
    }

    suspend fun updateRuleStatus(id: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        automationRuleDao.updateRuleStatus(id, isEnabled)
    }

    suspend fun deleteRule(id: Long) = withContext(Dispatchers.IO) {
        automationRuleDao.deleteRuleById(id)
    }

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun addConversation(title: String): Long = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(title = title)
        val id = conversationDao.insertConversation(conversation)
        addLog("Database", "Created conversation '$title' with ID $id")
        id
    }

    suspend fun updateConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        conversationDao.updateConversation(conversation)
        addLog("Database", "Updated conversation ${conversation.id}")
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        conversationDao.deleteById(id)
        messageDao.deleteMessagesForConversation(id)
        addLog("Database", "Deleted conversation $id and its messages")
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        isError: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            isError = isError
        )
        val id = messageDao.insertMessage(message)
        addLog("Database", "Saved $role message to conversation $conversationId (ID $id)")
        id
    }

    suspend fun addMemory(content: String, type: String, importance: Int = 3, embedding: String? = null): Long = withContext(Dispatchers.IO) {
        val memory = MemoryEntity(
            content = content,
            type = type,
            importance = importance,
            embedding = embedding
        )
        val id = memoryDao.insertMemory(memory)
        addLog("Memory", "Saved memory ($type) with ID $id: \"$content\"${if (embedding != null) " (with semantic embedding)" else ""}")
        id
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
        addLog("Memory", "Deleted memory $id")
    }

    suspend fun updateMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.insertMemory(memory)
        addLog("Memory", "Updated memory vector embedding for ID ${memory.id}")
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        memoryDao.clearAllMemories()
        addLog("Memory", "Cleared all long-term memories")
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        memoryDao.searchMemories(query)
    }

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        settingDao.getSetting(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.insertSetting(SettingEntity(key, value))
        addLog("Settings", "Updated setting: $key = $value")
    }

    suspend fun addLog(tag: String, message: String) = withContext(Dispatchers.IO) {
        logDao.insertLog(LogEntity(tag = tag, message = message))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearLogs()
    }
}
