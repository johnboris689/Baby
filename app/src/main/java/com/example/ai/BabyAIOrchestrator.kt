package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.local.CommandRoutingEngine
import com.example.data.local.DeviceControlManager
import com.example.data.local.RoutingResult
import com.example.data.model.Attachment
import com.example.data.repository.BabyRepository
import com.example.safety.JBRestrictions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Central intelligence layer for Baby.
 * All chat, voice, background services, wake-word commands, and file analyses
 * are routed through this single authoritative orchestrator without requiring any external APIs.
 */
class BabyAIOrchestrator(
    private val context: Context,
    val modelManager: BabyModelManager,
    val localEngine: BabyLocalAIEngine,
    private val repository: BabyRepository,
    private val routingEngine: CommandRoutingEngine = CommandRoutingEngine(DeviceControlManager(context))
) {
    private val tag = "BabyAIOrchestrator"

    /**
     * Executes local device action if matched.
     */
    private fun tryExecuteDeviceAction(text: String): String? {
        val result = routingEngine.routeAndExecute(text)
        return if (result is RoutingResult.LocalCommand) {
            result.responseText
        } else {
            null
        }
    }

    /**
     * Streams tokens for real-time chat output in Jetpack Compose UI.
     */
    fun streamResponse(
        prompt: String,
        conversationId: Long,
        attachments: List<Attachment> = emptyList()
    ): Flow<String> = flow {
        val trimmedPrompt = prompt.trim()

        // 1. Device Controls (Local system tasks without model latency)
        if (attachments.isEmpty()) {
            val deviceAction = tryExecuteDeviceAction(trimmedPrompt)
            if (deviceAction != null) {
                repository.addLog("Device_Control", "Executed on-device command: $deviceAction")
                emit(deviceAction)
                return@flow
            }
        }

        // 2. Centralized AI Safety & Usage Guardrails (JB Restrictions)
        val extractedText = attachments.mapNotNull { it.extractedText }.joinToString("\n")
        val safetyEval = JBRestrictions.evaluate(trimmedPrompt, extractedText)
        if (!safetyEval.isAllowed) {
            val refusal = safetyEval.userFacingResponse ?: "I cannot assist with that request under safety guidelines."
            repository.addLog("Safety_Policy", "JB Restrictions intercepted: ${safetyEval.category?.title}")
            emit(refusal)
            return@flow
        }

        // 3. Gather local context (Room Database memories and conversation history)
        val recentMemories: List<String> = try {
            repository.allMemories.firstOrNull()?.take(5)?.map { it.content } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val systemPrompt = "You are Baby, an independent on-device AI assistant. Respond calmly, clearly, and helpfully."

        // 4. Stream tokens from Local AI Engine
        val fullAccumulator = StringBuilder()
        localEngine.streamTokens(
            prompt = trimmedPrompt,
            systemPrompt = systemPrompt,
            conversationHistory = emptyList(),
            extractedDocumentText = extractedText,
            userMemories = recentMemories
        ).collect { token ->
            fullAccumulator.append(token)
            emit(token)
        }

        val completedText = fullAccumulator.toString()

        // 5. Post-inference output safety verification
        val outputCheck = JBRestrictions.evaluateOutput(completedText)
        if (!outputCheck.isAllowed) {
            val safeReplacement = "\n\n[Content adjusted in accordance with safety restrictions]"
            emit(safeReplacement)
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Synchronous response generator for background services, wake-word handlers,
     * and voice Conversation Mode.
     */
    suspend fun generateResponse(
        prompt: String,
        conversationId: Long = 1L,
        attachments: List<Attachment> = emptyList()
    ): String = withContext(Dispatchers.Default) {
        val trimmedPrompt = prompt.trim()

        // 1. Device Controls
        if (attachments.isEmpty()) {
            val deviceAction = tryExecuteDeviceAction(trimmedPrompt)
            if (deviceAction != null) {
                repository.addLog("Device_Control", "Executed on-device command: $deviceAction")
                return@withContext deviceAction
            }
        }

        // 2. Safety Evaluation
        val extractedText = attachments.mapNotNull { it.extractedText }.joinToString("\n")
        val safetyEval = JBRestrictions.evaluate(trimmedPrompt, extractedText)
        if (!safetyEval.isAllowed) {
            repository.addLog("Safety_Policy", "JB Restrictions intercepted: ${safetyEval.category?.title}")
            return@withContext safetyEval.userFacingResponse ?: "I cannot assist with that request."
        }

        // 3. Memory & Context
        val recentMemories: List<String> = try {
            repository.allMemories.firstOrNull()?.take(5)?.map { it.content } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val sb = StringBuilder()
        localEngine.streamTokens(
            prompt = trimmedPrompt,
            systemPrompt = "You are Baby, an independent on-device AI assistant.",
            conversationHistory = emptyList(),
            extractedDocumentText = extractedText,
            userMemories = recentMemories
        ).collect { token ->
            sb.append(token)
        }

        var response = sb.toString()
        val outputCheck = JBRestrictions.evaluateOutput(response)
        if (!outputCheck.isAllowed) {
            response = outputCheck.userFacingResponse ?: "I cannot provide that specific content under safety guidelines."
        }

        return@withContext response
    }
}
