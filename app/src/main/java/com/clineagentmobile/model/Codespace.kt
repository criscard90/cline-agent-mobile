package com.clineagentmobile.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * Represents a GitHub Codespace with its current state and configuration.
 */
data class Codespace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("repository") val repository: Repository,
    @SerializedName("container_opened_at") val containerOpenedAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("state") val state: State = State.STOPPED,
    @SerializedName("status") val status: Status? = null,
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
    @SerializedName("container_ready") val containerReady: Boolean = false,
    @SerializedName("connection") val connection: CodespaceConnection? = null
) {
    @JsonAdapter(StateAdapter::class)
    enum class State {
        RUNNING, STOPPED, PAUSED, CREATING, DELETING, UNKNOWN;

        companion object {
            /**
             * Lenient mapping of GitHub's `state` values (e.g. "available",
             * "provisioning", "shutting_down") to local states. Unknown
             * values map to UNKNOWN instead of failing the whole parse.
             */
            fun fromApi(raw: String?): State = when (raw?.lowercase()?.replace("_", "")?.replace("-", "")) {
                "available", "running", "started", "active" -> RUNNING
                "stopped", "shutdown", "shuttingdown", "archived", "moved" -> STOPPED
                "paused", "awaiting", "blocked", "suspended" -> PAUSED
                "creating", "created", "queued", "provisioning", "starting",
                "rebuilding", "rebooting", "updating" -> CREATING
                "deleting", "deleted", "removed" -> DELETING
                else -> UNKNOWN
            }

            fun toApi(state: State): String = when (state) {
                RUNNING -> "available"
                STOPPED -> "stopped"
                PAUSED -> "paused"
                CREATING -> "creating"
                DELETING -> "deleting"
                UNKNOWN -> "unknown"
            }
        }
    }

    @JsonAdapter(StatusAdapter::class)
    enum class Status {
        PENDING, READY, ERROR, SELECT_REPOSITORIES, STOPPED, UNKNOWN;

        companion object {
            fun fromApi(raw: String?): Status = when (raw?.lowercase()?.replace("_", "")) {
                "pending", "provisioning" -> PENDING
                "ready", "available" -> READY
                "error", "failed" -> ERROR
                "selectrepositories" -> SELECT_REPOSITORIES
                "stopped", "ended" -> STOPPED
                else -> UNKNOWN
            }

            fun toApi(status: Status): String = when (status) {
                PENDING -> "pending"
                READY -> "ready"
                ERROR -> "error"
                SELECT_REPOSITORIES -> "select_repositories"
                STOPPED -> "stopped"
                UNKNOWN -> "unknown"
            }
        }
    }

    // Computed via get(): Gson creates instances without running the constructor,
    // so stored vals (isRunning, …) would keep their zero value.
    val isRunning: Boolean get() = state == State.RUNNING
    val isStopped: Boolean get() = state == State.STOPPED
    val isActive: Boolean get() = state == State.RUNNING || state == State.PAUSED
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any>): Codespace {
            val repositoryMap = json["repository"] as? Map<String, Any> ?: emptyMap()
            return Codespace(
                id = (json["id"] as? String) ?: (json["id"] as? Number)?.toString() ?: "unknown",
                name = json["name"] as? String ?: "Unnamed",
                repository = Repository.fromJson(repositoryMap),
                containerOpenedAt = json["container_opened_at"] as? String,
                updatedAt = json["updated_at"] as? String,
                state = State.fromApi(json["state"] as? String),
                status = Status.fromApi(json["status"] as? String),
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

/**
 * Connessione del codespace al servizio Dev Tunnels di GitHub.
 * Contiene le credenziali con cui rendere pubblica una porta forwarded.
 */
data class CodespaceConnection(
    @SerializedName("tunnelProperties") val tunnelProperties: TunnelProperties? = null
)

/**
 * Proprietà del tunnel: arrivano da GET /user/codespaces/{name} (campo
 * `connection.tunnelProperties`) e servono al Dev Tunnel Manager
 * (usato da `gh codespace ports visibility`) per aggiornare la visibilità
 * delle porte — la REST API di GitHub non ha endpoint per le porte.
 */
data class TunnelProperties(
    @SerializedName("serviceUri") val serviceUri: String? = null,
    @SerializedName("tunnelId") val tunnelId: String? = null,
    @SerializedName("clusterId") val clusterId: String? = null,
    @SerializedName("domain") val domain: String? = null,
    @SerializedName("connectAccessToken") val connectAccessToken: String? = null,
    @SerializedName("managePortsAccessToken") val managePortsAccessToken: String? = null
)

/**
 * Lenient (de)serializer for [Codespace.State]: accepts any string GitHub
 * returns — including values introduced in the future — without failing the
 * parse of the whole response.
 */
class StateAdapter : JsonSerializer<Codespace.State>, JsonDeserializer<Codespace.State> {
    override fun serialize(
        src: Codespace.State,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonPrimitive(Codespace.State.toApi(src))

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Codespace.State = try {
        Codespace.State.fromApi(json.asString)
    } catch (e: Exception) {
        Codespace.State.UNKNOWN
    }
}

/**
 * Lenient (de)serializer for [Codespace.Status]; see [StateAdapter].
 */
class StatusAdapter : JsonSerializer<Codespace.Status>, JsonDeserializer<Codespace.Status> {
    override fun serialize(
        src: Codespace.Status,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonPrimitive(Codespace.Status.toApi(src))

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Codespace.Status = try {
        Codespace.Status.fromApi(json.asString)
    } catch (e: Exception) {
        Codespace.Status.UNKNOWN
    }
}
