package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a Git reference (branch or tag) in a repository.
 */
data class GitRef(
    @SerializedName("name") val name: String,
    @SerializedName("ref") val ref: String,
    @SerializedName("sha") val sha: String,
    @SerializedName("url") val url: String,
    @SerializedName("object") val obj: GitObject? = null,
    @SerializedName("repository") val repository: Repository? = null
) {
    enum class Type {
        @SerializedName("branch") BRANCH,
        @SerializedName("tag") TAG,
        @SerializedName("commit") COMMIT
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any>): GitRef {
            val objMap = json["object"] as? Map<String, Any>
            val repoMap = json["repository"] as? Map<String, Any>
            return GitRef(
                name = json["name"] as? String ?: "",
                ref = json["ref"] as? String ?: "",
                sha = json["sha"] as? String ?: "",
                url = json["url"] as? String ?: "",
                obj = objMap?.let { GitObject.fromJson(it) },
                repository = repoMap?.let { Repository.fromJson(it) }
            )
        }
    }
}

/**
 * Represents a Git object.
 */
data class GitObject(
    @SerializedName("sha") val sha: String,
    @SerializedName("type") val type: String,
    @SerializedName("url") val url: String
) {
    companion object {
        fun fromJson(json: Map<String, Any>): GitObject {
            return GitObject(
                sha = json["sha"] as? String ?: "",
                type = json["type"] as? String ?: "",
                url = json["url"] as? String ?: ""
            )
        }
    }
}
