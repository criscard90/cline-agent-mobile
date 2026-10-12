package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a message in the chat conversation.
 * Supports both user and assistant (AI) messages.
 */
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role,
    @SerializedName("content") val text: String = "",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("action_card") val actionCard: ActionCard? = null,
    @SerializedName("diff") val diff: DiffFile? = null,
    @SerializedName("tools_used") val toolsUsed: List<String> = emptyList(),
    @SerializedName("thinking") val thinking: String = "",
    val contextFiles: List<String> = emptyList(),
    @SerializedName("images") val images: List<ImageRef> = emptyList()
) {
    enum class Role {
        @SerializedName("user") USER,
        @SerializedName("assistant") ASSISTANT,
        @SerializedName("system") SYSTEM,
        @SerializedName("tool") TOOL
    }

    companion object {
        fun user(text: String, contextFiles: List<String> = emptyList()) =
            Message(role = Role.USER, text = text, contextFiles = contextFiles)

        fun assistant(
            text: String,
            thinking: String = "",
            actionCard: ActionCard? = null,
            diff: DiffFile? = null
        ) = Message(
            role = Role.ASSISTANT,
            text = text,
            thinking = thinking,
            actionCard = actionCard,
            diff = diff
        )

        fun tool(name: String, output: String) =
            Message(
                role = Role.TOOL,
                text = "[$name] $output",
                toolsUsed = listOf(name)
            )
    }
}
