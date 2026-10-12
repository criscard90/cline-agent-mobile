package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a GitHub user.
 */
data class User(
    @SerializedName("login") val login: String,
    @SerializedName("id") val id: Long,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("company") val company: String? = null,
    @SerializedName("blog") val blog: String? = null,
    @SerializedName("twitter_username") val twitterUsername: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("email") val email: String? = null
) {
    companion object {
        fun fromJson(json: Map<String, Any>): User {
            return User(
                login = json["login"] as? String ?: "Unknown",
                id = (json["id"] as? Number)?.toLong() ?: 0L,
                avatarUrl = json["avatar_url"] as? String,
                htmlUrl = json["html_url"] as? String,
                type = json["type"] as? String,
                name = json["name"] as? String,
                bio = json["bio"] as? String,
                company = json["company"] as? String,
                blog = json["blog"] as? String,
                twitterUsername = json["twitter_username"] as? String,
                location = json["location"] as? String,
                email = json["email"] as? String
            )
        }
    }
}
