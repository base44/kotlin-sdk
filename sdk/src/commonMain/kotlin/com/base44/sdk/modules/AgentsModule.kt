package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.models.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Provides conversation and messaging operations for AI agents in your Base44 app.
 *
 * ```kotlin
 * // Create a conversation
 * val conv = base44.agents.createConversation(CreateConversationParams(agentName = "SupportBot"))
 *
 * // Send a message and poll for the agent's reply
 * val updated = base44.agents.sendMessage(conv, "Help me with my order")
 * println(updated.messages?.last()?.content)
 * ```
 */
class AgentsModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    private val basePath get() = "/apps/$appId/agents"
    private val json = Json { ignoreUnknownKeys = true }

    // ── Conversations ──────────────────────────────────────────────────────────

    /** Returns all conversations for the current user. */
    suspend fun getConversations(): List<AgentConversation> {
        val data = http.get("$basePath/conversations")
        return json.decodeFromJsonElement(data)
    }

    /** Returns a single conversation by ID. */
    suspend fun getConversation(id: String): AgentConversation {
        val data = http.get("$basePath/conversations/$id")
        return json.decodeFromJsonElement(data)
    }

    /** Lists conversations with optional filter parameters. */
    suspend fun listConversations(filter: Map<String, String> = emptyMap()): List<AgentConversation> {
        val data = http.get("$basePath/conversations", filter)
        return json.decodeFromJsonElement(data)
    }

    /** Creates a new conversation. */
    suspend fun createConversation(params: CreateConversationParams = CreateConversationParams()): AgentConversation {
        val body = json.encodeToJsonElement(params).jsonObject
        val data = http.post("$basePath/conversations", body)
        return json.decodeFromJsonElement(data)
    }

    // ── Messages ───────────────────────────────────────────────────────────────

    /** Adds a message to a conversation (may trigger async agent processing). */
    suspend fun addMessage(conversation: AgentConversation, message: AgentMessage): AgentMessage {
        val body = json.encodeToJsonElement(message).jsonObject
        val data = http.post("$basePath/conversations/v2/${conversation.id}/messages", body)
        return json.decodeFromJsonElement(data)
    }

    /**
     * Sends a user message and polls until the agent replies.
     *
     * @param conversation The conversation to message.
     * @param content The user's message text.
     * @param pollIntervalMs Milliseconds between polls (default 1500).
     * @param maxAttempts Maximum number of polling attempts (default 30, ~45 seconds).
     * @return The updated conversation after the agent has replied.
     */
    suspend fun sendMessage(
        conversation: AgentConversation,
        content: String,
        pollIntervalMs: Long = 1_500L,
        maxAttempts: Int = 30,
    ): AgentConversation {
        val message = AgentMessage(role = "user", content = content)
        addMessage(conversation, message)

        repeat(maxAttempts) {
            delay(pollIntervalMs)
            val updated = getConversation(conversation.id)
            if (updated.messages?.lastOrNull()?.role != "user") {
                return updated
            }
        }
        return getConversation(conversation.id)
    }
}
