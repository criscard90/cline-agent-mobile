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
    private var connected = false

    private val messageQueue = mutableListOf<BridgeMessage>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(url: String) {
        if (connected) return
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
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
                Log.e(TAG, "Bridge WebSocket failed", t)
                connected = false
                listener?.onDisconnected()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                listener?.onDisconnected()
            }
        })
    }

    private fun handleIncoming(message: BridgeMessage) {
        listener?.onMessageReceived(message)
        when (message.type) {
            BridgeMessage.MessageType.AUTH_SUCCESS ->
                listener?.onBridgeStateChanged(BridgeState(connected = true, authenticated = true))
            BridgeMessage.MessageType.CHAT_STREAM ->
                listener?.onChatStream(message.payload["text"] as? String ?: "")
            else -> Unit
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
}
