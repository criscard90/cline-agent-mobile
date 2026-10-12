package com.clineagentmobile.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.clineagentmobile.ClineAgentMobileApp
import com.clineagentmobile.model.ActionCard
import com.clineagentmobile.model.BridgeMessage
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.DiffFile
import com.clineagentmobile.model.Message
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BridgeService : Service() {

    private var bridgeClient: BridgeWebSocketClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false

    private val encryptedPrefs: EncryptedPrefsManager by lazy {
        (applicationContext as? ClineAgentMobileApp)?.encryptedPrefs
            ?: EncryptedPrefsManager.getInstance(applicationContext)
    }

    private val bridgeListener = object : BridgeListener {
        override fun onBridgeStateChanged(state: BridgeState) {
            Log.d(TAG, "Bridge state changed: ${state.status}")
        }

        override fun onMessageReceived(message: BridgeMessage) {
            Log.d(TAG, "Message received: ${message.type}")
        }

        override fun onConnected() {
            Log.d(TAG, "Bridge connected")
        }

        override fun onDisconnected() {
            Log.d(TAG, "Bridge disconnected")
        }

        override fun onChatStream(text: String) = Unit
        override fun onChatMessage(message: Message) = Unit
        override fun onActionCard(actionCard: ActionCard) = Unit
        override fun onActionApproved(actionId: String) = Unit
        override fun onActionDenied(actionId: String) = Unit
        override fun onDiffUpdated(diffFile: DiffFile) = Unit
        override fun onCommandReceived(command: String) = Unit
        override fun onCodespacesUpdated(codespaces: List<Codespace>) = Unit
        override fun onCodespacesError(error: String) {
            Log.e(TAG, "Codespaces error: $error")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BridgeService created")
        encryptedPrefs.init()
        coroutineScope.launch { connectToBridge() }
    }

    private suspend fun connectToBridge() {
        try {
            val token = encryptedPrefs.getOAuthToken()?.accessToken
            val bridgeUrl = encryptedPrefs.getBridgeUrl()
            if (bridgeUrl.isNullOrEmpty()) {
                Log.w(TAG, "No bridge URL configured")
                isRunning = false
                return
            }
            bridgeClient = BridgeWebSocketClient(listener = bridgeListener)
            bridgeClient?.connect(bridgeUrl)
            if (!token.isNullOrBlank()) {
                bridgeClient?.authenticate(token)
            }
            isRunning = true
            Log.d(TAG, "Bridge service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start bridge service", e)
            isRunning = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BridgeService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BridgeService destroyed")
        coroutineScope.launch {
            bridgeClient?.disconnect()
            bridgeClient = null
            isRunning = false
        }
        coroutineScope.cancel()
    }

    fun sendCommand(command: String) {
        bridgeClient?.send(BridgeMessage.command(command))
    }

    fun sendChatStop() {
        bridgeClient?.send(BridgeMessage.chatStop())
    }

    fun approveAction(actionId: String) {
        bridgeClient?.send(BridgeMessage.actionApprove(actionId))
    }

    fun denyAction(actionId: String) {
        bridgeClient?.send(BridgeMessage.actionDeny(actionId))
    }

    fun getBridgeStatus(): BridgeState {
        val client = bridgeClient
        return BridgeState(connected = client?.isConnectedNow() == true)
    }

    fun isRunning(): Boolean = isRunning

    companion object {
        private const val TAG = "BridgeService"
    }
}

