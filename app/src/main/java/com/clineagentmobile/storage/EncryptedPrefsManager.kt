package com.clineagentmobile.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clineagentmobile.model.OAuthToken
import com.clineagentmobile.model.ApiKey
import com.clineagentmobile.model.ChatConversation
import com.clineagentmobile.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EncryptedPrefsManager private constructor(appContext: Context) {
    private val context: Context = appContext.applicationContext
    private val sharedPreferences: android.content.SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val gson: Gson = Gson()

    companion object {
        private const val PREFS_NAME = "cline_agent_mobile_prefs"
        private const val KEY_OAUTH_TOKEN = "oauth_token"
        private const val KEY_OAUTH_REFRESH = "oauth_refresh_token"
        private const val KEY_BRIDGE_URL = "bridge_url"
        private const val KEY_BRIDGE_TUNNEL_URL = "bridge_tunnel_url"
    private const val KEY_REPOSITORY = "repository"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_MODEL_PREF = "preferred_model"
        private const val KEY_BACKEND_TYPE = "backend_type"
        private const val KEY_OPENROUTER_KEY = "openrouter_key"
        private const val KEY_CHAT_MESSAGES = "chat_messages"
        private const val KEY_CHAT_CONVERSATIONS = "chat_conversations"
        private const val KEY_ACTIVE_CHAT_IDS = "active_chat_ids"
        private const val KEY_GEMINI_KEY = "gemini_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_OLLAMA_HOST = "ollama_host"

        @Volatile private var instance: EncryptedPrefsManager? = null

        fun getInstance(context: Context): EncryptedPrefsManager {
            return instance ?: synchronized(this) {
                val newInstance = EncryptedPrefsManager(context.applicationContext)
                instance = newInstance
                newInstance
            }
        }
    }

    fun init() {}

    fun saveOAuthToken(token: OAuthToken) {
        with(sharedPreferences.edit()) {
            putString(KEY_OAUTH_TOKEN, gson.toJson(token))
            apply()
        }
    }

    fun getOAuthToken(): OAuthToken? {
        val json = sharedPreferences.getString(KEY_OAUTH_TOKEN, null)
        return json?.let { gson.fromJson(it, OAuthToken::class.java) }
    }

    fun clearOAuthToken() {
        with(sharedPreferences.edit()) {
            remove(KEY_OAUTH_TOKEN)
            remove(KEY_OAUTH_REFRESH)
            apply()
        }
    }

    fun saveBridgeUrl(url: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_BRIDGE_URL, url)
            apply()
        }
    }

    fun saveBridgeTunnelUrl(url: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_BRIDGE_TUNNEL_URL, url)
            apply()
        }
    }

    fun getBridgeUrl(): String? {
        return sharedPreferences.getString(KEY_BRIDGE_URL, null)
    }

    fun getBridgeTunnelUrl(): String? {
        return sharedPreferences.getString(KEY_BRIDGE_TUNNEL_URL, null)
    }

    fun saveRepository(repo: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_REPOSITORY, repo)
            apply()
        }
    }

    fun getRepository(): String? {
        return sharedPreferences.getString(KEY_REPOSITORY, null)
    }

    fun saveTimeout(seconds: Int) {
        with(sharedPreferences.edit()) {
            putInt(KEY_TIMEOUT, seconds)
            apply()
        }
    }

    fun getTimeout(): Int {
        return sharedPreferences.getInt(KEY_TIMEOUT, 30)
    }

    fun setIsFirstLaunch(isFirst: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_FIRST_LAUNCH, isFirst)
            apply()
        }
    }

    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    // Model preference
    fun savePreferredModel(model: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_MODEL_PREF, model)
            apply()
        }
    }

    fun getPreferredModel(): String {
        return sharedPreferences.getString(KEY_MODEL_PREF, "") ?: ""
    }

    fun saveChatMessages(messages: List<Message>) {
        with(sharedPreferences.edit()) {
            putString(KEY_CHAT_MESSAGES, gson.toJson(messages))
            apply()
        }
    }

    fun getChatMessages(): List<Message> {
        val json = sharedPreferences.getString(KEY_CHAT_MESSAGES, null) ?: return emptyList()
        val messageListType = object : TypeToken<List<Message>>() {}.type
        return gson.fromJson<List<Message>>(json, messageListType) ?: emptyList()
    }

    fun getOrCreateActiveChat(bridgeUrl: String): ChatConversation {
        val conversations = loadChatConversations()
        migrateLegacyChatHistory(conversations, bridgeUrl)

        val activeId = loadActiveChatIds()[bridgeUrl]
        val activeConversation = conversations.firstOrNull {
            it.id == activeId && it.bridgeUrl == bridgeUrl
        } ?: conversations
            .filter { it.bridgeUrl == bridgeUrl }
            .maxByOrNull { it.updatedAt }
            ?: ChatConversation(bridgeUrl = bridgeUrl).also(conversations::add)

        saveChatConversations(conversations)
        saveActiveChatId(bridgeUrl, activeConversation.id)
        return activeConversation
    }

    fun getChatConversations(bridgeUrl: String): List<ChatConversation> {
        val conversations = loadChatConversations()
        migrateLegacyChatHistory(conversations, bridgeUrl)
        saveChatConversations(conversations)
        return conversations
            .filter { it.bridgeUrl == bridgeUrl }
            .sortedByDescending { it.updatedAt }
    }

    fun createChatConversation(bridgeUrl: String): ChatConversation {
        val conversation = ChatConversation(bridgeUrl = bridgeUrl)
        val conversations = loadChatConversations()
        migrateLegacyChatHistory(conversations, bridgeUrl)
        conversations.add(conversation)
        saveChatConversations(conversations)
        saveActiveChatId(bridgeUrl, conversation.id)
        return conversation
    }

    fun saveChatConversation(conversation: ChatConversation) {
        val conversations = loadChatConversations()
        val index = conversations.indexOfFirst { it.id == conversation.id }
        if (index >= 0) {
            conversations[index] = conversation
        } else {
            conversations.add(conversation)
        }
        saveChatConversations(conversations)
    }

    fun setActiveChat(bridgeUrl: String, conversationId: String) {
        val exists = loadChatConversations().any {
            it.id == conversationId && it.bridgeUrl == bridgeUrl
        }
        require(exists) { "Chat does not belong to the configured Codespace" }
        saveActiveChatId(bridgeUrl, conversationId)
    }

    private fun loadChatConversations(): MutableList<ChatConversation> {
        val json = sharedPreferences.getString(KEY_CHAT_CONVERSATIONS, null) ?: return mutableListOf()
        val conversationListType = object : TypeToken<List<ChatConversation>>() {}.type
        return (gson.fromJson<List<ChatConversation>>(json, conversationListType) ?: emptyList())
            .toMutableList()
    }

    private fun saveChatConversations(conversations: List<ChatConversation>) {
        sharedPreferences.edit()
            .putString(KEY_CHAT_CONVERSATIONS, gson.toJson(conversations))
            .apply()
    }

    private fun loadActiveChatIds(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_ACTIVE_CHAT_IDS, null) ?: return emptyMap()
        val activeIdsType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson<Map<String, String>>(json, activeIdsType) ?: emptyMap()
    }

    private fun saveActiveChatId(bridgeUrl: String, conversationId: String) {
        val activeIds = loadActiveChatIds().toMutableMap()
        activeIds[bridgeUrl] = conversationId
        sharedPreferences.edit()
            .putString(KEY_ACTIVE_CHAT_IDS, gson.toJson(activeIds))
            .apply()
    }

    private fun migrateLegacyChatHistory(
        conversations: MutableList<ChatConversation>,
        bridgeUrl: String
    ) {
        if (!sharedPreferences.contains(KEY_CHAT_MESSAGES)) return

        if (conversations.isEmpty()) {
            val messages = getChatMessages()
            if (messages.isNotEmpty()) {
                val firstUserMessage = messages.firstOrNull { it.role == Message.Role.USER }?.text
                conversations.add(
                    ChatConversation(
                        bridgeUrl = bridgeUrl,
                        title = firstUserMessage?.toChatTitle() ?: ChatConversation.DEFAULT_TITLE,
                        messages = messages
                    )
                )
            }
        }
        sharedPreferences.edit().remove(KEY_CHAT_MESSAGES).apply()
    }

    private fun String.toChatTitle(): String {
        val title = trim().replace(Regex("\\s+"), " ")
        return when {
            title.isBlank() -> ChatConversation.DEFAULT_TITLE
            title.length <= 40 -> title
            else -> "${title.take(37)}..."
        }
    }

    // Backend type preference
    fun saveBackendType(type: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_BACKEND_TYPE, type)
            apply()
        }
    }

    fun getBackendType(): String {
        return sharedPreferences.getString(KEY_BACKEND_TYPE, "auto") ?: "auto"
    }

    // OpenRouter API Key
    fun saveOpenRouterKey(key: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_OPENROUTER_KEY, key)
            apply()
        }
    }

    fun getOpenRouterKey(): String? {
        return sharedPreferences.getString(KEY_OPENROUTER_KEY, null)
    }

    // Gemini API Key
    fun saveGeminiKey(key: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_GEMINI_KEY, key)
            apply()
        }
    }

    fun getGeminiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_KEY, null)
    }

    // Ollama host
    fun saveOllamaHost(host: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_OLLAMA_HOST, host)
            apply()
        }
    }

    fun getOllamaHost(): String {
        return sharedPreferences.getString(KEY_OLLAMA_HOST, "http://localhost:11434") ?: "http://localhost:11434"
    }

    // List of stored API keys
    fun getApiKeys(): List<ApiKey> {
        val keys = mutableListOf<ApiKey>()

        val openRouterKey = getOpenRouterKey()
        if (!openRouterKey.isNullOrBlank()) {
            keys.add(ApiKey(service = ApiKey.Service.OPENROUTER, key = openRouterKey))
        }

        val geminiKey = getGeminiKey()
        if (!geminiKey.isNullOrBlank()) {
            keys.add(ApiKey(service = ApiKey.Service.GEMINI, key = geminiKey))
        }

        return keys
    }

    // Clear all stored data
    fun clearAll() {
        with(sharedPreferences.edit()) {
            remove(KEY_OAUTH_TOKEN)
            remove(KEY_OAUTH_REFRESH)
            remove(KEY_BRIDGE_URL)
            remove(KEY_REPOSITORY)
            remove(KEY_TIMEOUT)
            remove(KEY_IS_FIRST_LAUNCH)
            remove(KEY_MODEL_PREF)
            remove(KEY_BACKEND_TYPE)
            remove(KEY_CHAT_MESSAGES)
            remove(KEY_CHAT_CONVERSATIONS)
            remove(KEY_ACTIVE_CHAT_IDS)
            remove(KEY_OPENROUTER_KEY)
            remove(KEY_GEMINI_KEY)
            remove(KEY_OLLAMA_HOST)
            apply()
        }
    }
}