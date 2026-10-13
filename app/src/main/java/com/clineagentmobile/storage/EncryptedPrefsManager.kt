package com.clineagentmobile.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clineagentmobile.model.OAuthToken
import com.clineagentmobile.model.ApiKey
import com.google.gson.Gson

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
    private const val KEY_REPOSITORY = "repository"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_MODEL_PREF = "preferred_model"
        private const val KEY_BACKEND_TYPE = "backend_type"
        private const val KEY_OPENROUTER_KEY = "openrouter_key"
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

    fun getBridgeUrl(): String? {
        return sharedPreferences.getString(KEY_BRIDGE_URL, null)
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
        return sharedPreferences.getString(KEY_MODEL_PREF, "gemini-2.0-flash-exp") ?: "gemini-2.0-flash-exp"
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
            remove(KEY_OPENROUTER_KEY)
            remove(KEY_GEMINI_KEY)
            remove(KEY_OLLAMA_HOST)
            apply()
        }
    }
}