package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an OAuth token for GitHub API access.
 */
data class OAuthToken(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean = expiresIn != null && System.currentTimeMillis() > createdAt + expiresIn * 1000L

    companion object {
        fun fromJson(json: Map<String, Any>): OAuthToken {
            return OAuthToken(
                accessToken = json["access_token"] as? String ?: "",
                tokenType = json["token_type"] as? String ?: "Bearer",
                scope = json["scope"] as? String ?: "",
                refreshToken = json["refresh_token"] as? String,
                expiresIn = (json["expires_in"] as? Number)?.toInt(),
                createdAt = (json["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * Represents an API key for third-party services like OpenRouter or Gemini.
 */
data class ApiKey(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("service") val service: Service,
    @SerializedName("key") val key: String,
    @SerializedName("label") val label: String? = null,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("last_used_at") val lastUsedAt: Long? = null
) {
    enum class Service {
        @SerializedName("github") GITHUB,
        @SerializedName("openrouter") OPENROUTER,
        @SerializedName("gemini") GEMINI,
        @SerializedName("ollama") OLLAMA,
        @SerializedName("custom") CUSTOM
    }

    companion object {
        fun fromJson(json: Map<String, Any>): ApiKey {
            return ApiKey(
                id = json["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                service = try {
                    Service.valueOf((json["service"] as? String)?.uppercase() ?: "GITHUB")
                } catch (e: Exception) {
                    Service.GITHUB
                },
                key = json["key"] as? String ?: "",
                label = json["label"] as? String,
                createdAt = (json["created_at"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                lastUsedAt = (json["last_used_at"] as? Number)?.toLong()
            )
        }
    }

    fun isHidden(): Boolean = service != Service.GITHUB
}
