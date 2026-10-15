package com.clineagentmobile.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.clineagentmobile.model.ActionCard
import com.clineagentmobile.model.BridgeMessage
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.DiffFile
import com.clineagentmobile.model.Message
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class BridgeWebSocketClient(
    private val gson: Gson = Gson(),
    private val listener: BridgeListener? = null
) {
    private var socket: WebSocket? = null
    @Volatile private var connected = false
    private var currentUrl: String? = null
    private var shouldReconnect = false
    private var reconnectAttempts = 0

    private val messageQueue = mutableListOf<BridgeMessage>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Shared OkHttp client (reused across reconnects to avoid leaking dispatchers)
    private val okClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(url: String) {
        currentUrl = url
        shouldReconnect = true
        reconnectAttempts = 0
        attemptConnect()
    }

    private fun attemptConnect() {
        val url = currentUrl ?: return
        if (connected) return
        val request = Request.Builder().url(url).build()
        socket = okClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                reconnectAttempts = 0
                listener?.onConnected()
                synchronized(messageQueue) {
                    val pending = ArrayList(messageQueue)
                    messageQueue.clear()
                    pending.forEach { send(it) }
                }
                schedulePing()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, BridgeMessage::class.java)
                    handleIncoming(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Bridge WebSocket failed url=$currentUrl code=${response?.code} msg=${response?.message}", t)
                connected = false
                mainHandler.post {
                    listener?.onError("WebSocket connection to $currentUrl failed: ${t.message ?: t::class.java.simpleName}")
                    listener?.onDisconnected()
                }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Bridge WebSocket closed url=$currentUrl code=$code reason=$reason")
                connected = false
                mainHandler.post {
                    listener?.onError("WebSocket closed ($code): $reason")
                    listener?.onDisconnected()
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        val delay = (RECONNECT_BASE_DELAY_MS * (1 shl reconnectAttempts)).coerceAtMost(RECONNECT_MAX_DELAY_MS)
        reconnectAttempts++
        mainHandler.postDelayed({ if (!connected) attemptConnect() }, delay)
    }

    private fun handleIncoming(message: BridgeMessage) {
        listener?.onMessageReceived(message)
        when (message.type) {
            BridgeMessage.MessageType.AUTH_SUCCESS -> {
                @Suppress("UNCHECKED_CAST")
                val nested = message.payload["bridgeState"] as? Map<String, Any>
                val state = if (nested != null) {
                    BridgeState.fromJson(nested).copy(
                        connected = true,
                        authenticated = true,
                        status = BridgeState.Status.AUTHENTICATED
                    )
                } else {
                    BridgeState(
                        connected = true,
                        authenticated = true,
                        status = BridgeState.Status.AUTHENTICATED
                    )
                }
                listener?.onBridgeStateChanged(state)
            }

            BridgeMessage.MessageType.AUTH_ERROR -> {
                val err = message.payload["error"] as? String ?: "Auth error"
                listener?.onError(err)
                listener?.onBridgeStateChanged(BridgeState(status = BridgeState.Status.ERROR, message = err))
            }

            BridgeMessage.MessageType.STATUS -> {
                @Suppress("UNCHECKED_CAST")
                val nested = message.payload["bridgeState"] as? Map<String, Any>
                val stateMap = nested ?: message.payload
                listener?.onBridgeStateChanged(BridgeState.fromJson(stateMap))
            }

            BridgeMessage.MessageType.CHAT_STREAM -> {
                val text = message.payload["text"] as? String ?: ""
                if (text.isNotEmpty()) listener?.onChatStream(text)
            }

            BridgeMessage.MessageType.CHAT_MESSAGE -> {
                messageFromPayload(message)?.let { listener?.onChatMessage(it) }
            }

            BridgeMessage.MessageType.ERROR -> {
                val err = message.payload["error"] as? String ?: "Bridge error"
                Log.e(TAG, "Bridge reported error: $err")
                listener?.onError(err)
            }

            else -> Unit
        }
    }

    private fun messageFromPayload(message: BridgeMessage): Message? {
        val raw = message.payload["message"] ?: return null
        return try {
            gson.fromJson(gson.toJson(raw), Message::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chat message", e)
            null
        }
    }

    fun send(message: BridgeMessage) {
        val active = socket
        if (!connected || active == null) {
            synchronized(messageQueue) { messageQueue.add(message) }
            return
        }
        active.send(gson.toJson(message))
    }

    fun sendIfConnected(message: BridgeMessage): Boolean {
        val active = socket
        return connected && active != null && active.send(gson.toJson(message))
    }

    fun authenticate(token: String) {
        send(BridgeMessage.auth(token))
    }

    fun disconnect() {
        socket?.close(1000, "App requested close")
        socket = null
        connected = false
        listener?.onDisconnected()
    }

    fun isConnectedNow(): Boolean = connected

    private fun schedulePing() {
        mainHandler.postDelayed({
            if (connected) {
                send(BridgeMessage.ping())
                schedulePing()
            }
        }, 30000)
    }

    companion object {
        private const val TAG = "BridgeWebSocketClient"
        private const val MAX_RECONNECT_ATTEMPTS = 6
        private const val RECONNECT_BASE_DELAY_MS = 3000L
        private const val RECONNECT_MAX_DELAY_MS = 60000L
    }
}

interface BridgeListener {
    fun onBridgeStateChanged(state: BridgeState)
    fun onMessageReceived(message: BridgeMessage)
    fun onConnected()
    fun onDisconnected()
    fun onChatStream(text: String)
    fun onChatMessage(message: Message)
    fun onActionCard(actionCard: ActionCard)
    fun onActionApproved(actionId: String)
    fun onActionDenied(actionId: String)
    fun onDiffUpdated(diffFile: DiffFile)
    fun onCommandReceived(command: String)
    fun onCodespacesUpdated(codespaces: List<Codespace>)
    fun onCodespacesError(error: String)
    fun onError(error: String)
}
