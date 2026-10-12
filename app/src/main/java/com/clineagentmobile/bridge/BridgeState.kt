package com.clineagentmobile.bridge

import com.google.gson.annotations.SerializedName

/**
 * Represents the state of the bridge server inside the Codespace.
 */
data class BridgeState(
    @SerializedName("connected") val connected: Boolean = false,
    @SerializedName("authenticated") val authenticated: Boolean = false,
    @SerializedName("lastSeen") val lastSeen: Long = System.currentTimeMillis(),
    @SerializedName("version") val version: String? = null,
    @SerializedName("serverTime") val serverTime: Long? = null,
    @SerializedName("bridgeUrl") val bridgeUrl: String? = null,
    @SerializedName("status") val status: Status = Status.DISCONNECTED,
    @SerializedName("message") val message: String? = null,
    @SerializedName("ping") val ping: Long? = null
) {
    enum class Status {
        @SerializedName("disconnected") DISCONNECTED,
        @SerializedName("connecting") CONNECTING,
        @SerializedName("connected") CONNECTED,
        @SerializedName("authenticated") AUTHENTICATED,
        @SerializedName("error") ERROR,
        @SerializedName("reconnecting") RECONNECTING
    }

    val isReady: Boolean = connected && authenticated

    companion object {
        fun fromJson(json: Map<String, Any>): BridgeState {
            return BridgeState(
                connected = json["connected"] as? Boolean ?: false,
                authenticated = json["authenticated"] as? Boolean ?: false,
                lastSeen = (json["lastSeen"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                version = json["version"] as? String,
                serverTime = (json["serverTime"] as? Number)?.toLong(),
                bridgeUrl = json["bridgeUrl"] as? String,
                status = try {
                    Status.valueOf((json["status"] as? String)?.uppercase() ?: "DISCONNECTED")
                } catch (e: Exception) {
                    Status.DISCONNECTED
                },
                message = json["message"] as? String,
                ping = json["ping"] as? Long
            )
        }
    }
}
