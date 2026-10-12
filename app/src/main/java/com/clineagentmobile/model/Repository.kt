package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

data class Repository(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("full_name") val fullName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("owner") val owner: User? = null,
    @SerializedName("private") val isPrivate: Boolean = false,
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("default_branch") val defaultBranch: String = "main",
    @SerializedName("description") val description: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("stargazers_count") val stargazersCount: Int = 0,
    @SerializedName("forks_count") val forksCount: Int = 0
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any>): Repository {
            val ownerMap = json["owner"] as? Map<String, Any>
            return Repository(
                id = (json["id"] as? Number)?.toLong() ?: 0L,
                fullName = json["full_name"] as? String ?: "",
                name = json["name"] as? String ?: "",
                owner = ownerMap?.let { User.fromJson(it) },
                isPrivate = json["private"] as? Boolean ?: false,
                htmlUrl = json["html_url"] as? String ?: "",
                defaultBranch = json["default_branch"] as? String ?: "main",
                description = json["description"] as? String,
                language = json["language"] as? String,
                stargazersCount = (json["stargazers_count"] as? Number)?.toInt() ?: 0,
                forksCount = (json["forks_count"] as? Number)?.toInt() ?: 0
            )
        }
    }
}
