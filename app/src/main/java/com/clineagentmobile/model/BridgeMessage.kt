package com.clineagentmobile.model

import com.clineagentmobile.bridge.BridgeState
import com.google.gson.annotations.SerializedName

/**
 * Represents a message between the Android app and the bridge server.
 */
data class BridgeMessage(
    @SerializedName("type") val type: MessageType,
    @SerializedName("payload") val payload: Map<String, Any> = emptyMap(),
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    enum class MessageType {
        @SerializedName("ping") PING,
        @SerializedName("pong") PONG,
        @SerializedName("connect") CONNECT,
        @SerializedName("disconnect") DISCONNECT,
        @SerializedName("auth") AUTH,
        @SerializedName("auth_success") AUTH_SUCCESS,
        @SerializedName("auth_error") AUTH_ERROR,
        @SerializedName("status") STATUS,
        @SerializedName("chat_message") CHAT_MESSAGE,
        @SerializedName("chat_stream") CHAT_STREAM,
        @SerializedName("chat_stop") CHAT_STOP,
        @SerializedName("action") ACTION,
        @SerializedName("action_approve") ACTION_APPROVE,
        @SerializedName("action_deny") ACTION_DENY,
        @SerializedName("file_update") FILE_UPDATE,
        @SerializedName("diff") DIFF,
        @SerializedName("command") COMMAND,
        @SerializedName("error") ERROR,
        @SerializedName("list_codespaces") LIST_CODESPACES,
        @SerializedName("list_codespaces_success") LIST_CODESPACES_SUCCESS,
        @SerializedName("list_codespaces_error") LIST_CODESPACES_ERROR
    }

    companion object {
        fun ping() = BridgeMessage(type = MessageType.PING)
        fun pong() = BridgeMessage(type = MessageType.PONG)

        fun connect(url: String) = BridgeMessage(
            type = MessageType.CONNECT,
            payload = mapOf("url" to url)
        )

        fun disconnect() = BridgeMessage(type = MessageType.DISCONNECT)

        fun auth(token: String) = BridgeMessage(
            type = MessageType.AUTH,
            payload = mapOf("token" to token)
        )

        fun authSuccess(bridgeState: BridgeState) = BridgeMessage(
            type = MessageType.AUTH_SUCCESS,
            payload = mapOf("bridgeState" to bridgeState)
        )

        fun authError(error: String) = BridgeMessage(
            type = MessageType.AUTH_ERROR,
            payload = mapOf("error" to error)
        )

        fun status(bridgeState: BridgeState) = BridgeMessage(
            type = MessageType.STATUS,
            payload = mapOf("bridgeState" to bridgeState)
        )

        fun chatMessage(
            message: com.clineagentmobile.model.Message,
            model: String,
            apiKey: String,
            conversationId: String
        ) = BridgeMessage(
            type = MessageType.CHAT_MESSAGE,
            payload = mapOf(
                "message" to message,
                "provider" to "openrouter",
                "model" to model,
                "apiKey" to apiKey,
                "conversationId" to conversationId
            )
        )

        fun chatStream(text: String) = BridgeMessage(
            type = MessageType.CHAT_STREAM,
            payload = mapOf("text" to text)
        )

        fun chatStop() = BridgeMessage(type = MessageType.CHAT_STOP)

        fun command(text: String) = BridgeMessage(
            type = MessageType.COMMAND,
            payload = mapOf("command" to text)
        )

        fun action(actionCard: com.clineagentmobile.model.ActionCard) = BridgeMessage(
            type = MessageType.ACTION,
            payload = mapOf("actionCard" to actionCard)
        )

        fun actionApprove(actionId: String) = BridgeMessage(
            type = MessageType.ACTION_APPROVE,
            payload = mapOf("actionId" to actionId)
        )

        fun actionDeny(actionId: String) = BridgeMessage(
            type = MessageType.ACTION_DENY,
            payload = mapOf("actionId" to actionId)
        )

        fun diff(diffFile: com.clineagentmobile.model.DiffFile) = BridgeMessage(
            type = MessageType.DIFF,
            payload = mapOf("diffFile" to diffFile)
        )

        fun listCodespacesSuccess(codespaces: List<com.clineagentmobile.model.Codespace>) = BridgeMessage(
            type = MessageType.LIST_CODESPACES_SUCCESS,
            payload = mapOf("codespaces" to codespaces)
        )

        fun listCodespacesError(error: String) = BridgeMessage(
            type = MessageType.LIST_CODESPACES_ERROR,
            payload = mapOf("error" to error)
        )

        fun error(message: String) = BridgeMessage(
            type = MessageType.ERROR,
            payload = mapOf("error" to message)
        )
    }
}
