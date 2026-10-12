package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a GitHub Codespace with its current state and configuration.
 */
data class Codespace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("repository") val repository: Repository,
    @SerializedName("container_opened_at") val containerOpenedAt: Long? = null,
    @SerializedName("updated_at") val updatedAt: Long? = null,
    @SerializedName("state") val state: State = State.STOPPED,
    @SerializedName("status") val status: Status = Status.UNKNOWN,
    @SerializedName("url") val url: String? = null,
    @SerializedName("web_url") val webUrl: String? = null,
    @SerializedName("ssh_url") val sshUrl: String? = null,
    @SerializedName("vnc_url") val vncUrl: String? = null,
    @SerializedName("storage_size") val storageSize: Int? = null,
    @SerializedName("cpu_usage") val cpuUsage: Double? = null,
    @SerializedName("memory_usage") val memoryUsage: Double? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("bridge_url") val bridgeUrl: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("statusText") val statusText: String? = null,
    @SerializedName("ssh_ready") val sshReady: Boolean = false,
    @SerializedName("container_ready") val containerReady: Boolean = false
) {
    enum class State {
        @SerializedName("running") RUNNING,
        @SerializedName("stopped") STOPPED,
        @SerializedName("paused") PAUSED,
        @SerializedName("creating") CREATING,
        @SerializedName("deleting") DELETING,
        @SerializedName("unknown") UNKNOWN
    }

    enum class Status {
        @SerializedName("pending") PENDING,
        @SerializedName("ready") READY,
        @SerializedName("error") ERROR,
        @SerializedName("select_repositories") SELECT_REPOSITORIES,
        @SerializedName("stopped") STOPPED,
        @SerializedName("unknown") UNKNOWN
    }

    val isRunning: Boolean = state == State.RUNNING
    val isStopped: Boolean = state == State.STOPPED
    val isActive: Boolean = state == State.RUNNING || state == State.PAUSED
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any>): Codespace {
            val repositoryMap = json["repository"] as? Map<String, Any> ?: emptyMap()
            return Codespace(
                id = json["id"] as? String ?: "unknown",
                name = json["name"] as? String ?: "Unnamed",
                repository = Repository.fromJson(repositoryMap),
                containerOpenedAt = (json["container_opened_at"] as? Number)?.toLong(),
                updatedAt = (json["updated_at"] as? Number)?.toLong(),
                state = try {
                    State.valueOf((json["state"] as? String)?.uppercase() ?: "UNKNOWN")
                } catch (e: Exception) {
                    State.UNKNOWN
                },
                status = try {
                    Status.valueOf((json["status"] as? String)?.uppercase() ?: "UNKNOWN")
                } catch (e: Exception) {
                    Status.UNKNOWN
                },
                url = json["url"] as? String,
                webUrl = json["web_url"] as? String,
                sshUrl = json["ssh_url"] as? String,
                storageSize = (json["storage_size"] as? Number)?.toInt(),
                cpuUsage = (json["cpu_usage"] as? Number)?.toDouble(),
                memoryUsage = (json["memory_usage"] as? Number)?.toDouble(),
                timestamp = (json["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                bridgeUrl = json["bridge_url"] as? String,
                model = json["model"] as? String,
                region = json["region"] as? String,
                displayName = json["display_name"] as? String,
                statusText = json["statusText"] as? String,
                sshReady = json["ssh_ready"] as? Boolean ?: false,
                containerReady = json["container_ready"] as? Boolean ?: false
            )
        }
    }
}
