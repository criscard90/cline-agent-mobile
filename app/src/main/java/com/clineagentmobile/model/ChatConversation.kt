package com.clineagentmobile.model

import java.util.UUID

data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    val bridgeUrl: String,
    val title: String = DEFAULT_TITLE,
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_TITLE = "New chat"
    }
}
