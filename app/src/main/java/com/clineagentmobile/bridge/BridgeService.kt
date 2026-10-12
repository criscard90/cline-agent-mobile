package com.clineagentmobile.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
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
import java.util.concurrent.CopyOnWriteArraySet

class BridgeService : Service() {

    private var bridgeClient: BridgeWebSocketClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false

    @Volatile private var lastState: BridgeState = BridgeState()

    private val encryptedPrefs: EncryptedPrefsManager by lazy {
        (applicationContext as? ClineAgentMobileApp)?.encryptedPrefs
            ?: EncryptedPrefsManager.getInstance(applicationContext)
    }

    private val listeners = CopyOnWriteArraySet<BridgeListener>()

    fun registerListener(listener: BridgeListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: BridgeListener) {
        listeners.remove(listener)
    }

    private val bridgeListener = object : BridgeListener {
        override fun onBridgeStateChanged(state: BridgeState) {
            Log.d(TAG, "Bridge state changed: ${state.status}")
            lastState = state
            listeners.forEach { it.onBridgeStateChanged(state) }
        }

        override fun onMessageReceived(message: BridgeMessage) {
            listeners.forEach { it.onMessageReceived(message) }
        }

        override fun onConnected() {
            Log.d(TAG, "Bridge connected")
            lastState = lastState.copy(connected = true)
            listeners.forEach { it.onConnected() }
        }

        override fun onDisconnected() {
            Log.d(TAG, "Bridge disconnected")
            lastState = lastState.copy(connected = false, status = BridgeState.Status.DISCONNECTED)
            listeners.forEach { it.onDisconnected() }
        }

        override fun onChatStream(text: String) {
            listeners.forEach { it.onChatStream(text) }
        }

        override fun onChatMessage(message: Message) {
            listeners.forEach { it.onChatMessage(message) }
        }

        override fun onActionCard(actionCard: ActionCard) {
            listeners.forEach { it.onActionCard(actionCard) }
        }

        override fun onActionApproved(actionId: String) {
            listeners.forEach { it.onActionApproved(actionId) }
        }

        override fun onActionDenied(actionId: String) {
            listeners.forEach { it.onActionDenied(actionId) }
        }

        override fun onDiffUpdated(diffFile: DiffFile) {
            listeners.forEach { it.onDiffUpdated(diffFile) }
        }

        override fun onCommandReceived(command: String) {
            listeners.forEach { it.onCommandReceived(command) }
        }

        override fun onCodespacesUpdated(codespaces: List<Codespace>) {
            listeners.forEach { it.onCodespacesUpdated(codespaces) }
        }

        override fun onCodespacesError(error: String) {
            Log.e(TAG, "Codespaces error: $error")
            listeners.forEach { it.onCodespacesError(error) }
        }

        override fun onError(error: String) {
            Log.e(TAG, "Bridge error: $error")
            listeners.forEach { it.onError(error) }
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

    inner class LocalBinder : Binder() {
        fun getService(): BridgeService = this@BridgeService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BridgeService destroyed")
        bridgeClient?.disconnect()
        bridgeClient = null
        isRunning = false
        coroutineScope.cancel()
    }

    fun sendCommand(command: String) {
        bridgeClient?.send(BridgeMessage.command(command))
    }

    fun sendChatMessage(text: String) {
        bridgeClient?.send(BridgeMessage.chatMessage(Message.user(text)))
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

    fun getBridgeStatus(): BridgeState = lastState

    fun isRunning(): Boolean = isRunning

    companion object {
        private const val TAG = "BridgeService"
    }
}

