package com.clineagentmobile.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.clineagentmobile.ClineAgentMobileApp
import com.clineagentmobile.R
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

    private fun startAsForeground() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bridge_notif_channel),
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(getString(R.string.bridge_notif_title))
                .setContentText(getString(R.string.bridge_notif_text))
                .setOngoing(true)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            Log.d(TAG, "BridgeService in foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile mettere BridgeService in foreground", e)
        }
    }

    private suspend fun connectToBridge() {
        try {
            val token = encryptedPrefs.getOAuthToken()?.accessToken
            val bridgeUrl = encryptedPrefs.getBridgeUrl()
            val bridgeTunnelUrl = encryptedPrefs.getBridgeTunnelUrl()
            if (bridgeUrl.isNullOrEmpty() && bridgeTunnelUrl.isNullOrEmpty()) {
                Log.w(TAG, "No bridge URL configured")
                isRunning = false
                return
            }
            bridgeClient = BridgeWebSocketClient(listener = bridgeListener)
            // Preferisce l'URL tunnel diretto per la connessione WebSocket
            val urlToUse = bridgeTunnelUrl ?: bridgeUrl
            bridgeClient?.connect(urlToUse ?: "")
            if (!token.isNullOrBlank()) {
                bridgeClient?.authenticate(token)
            }
            isRunning = true
            Log.d(TAG, "Bridge service started, url=$urlToUse")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start bridge service", e)
            isRunning = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BridgeService onStartCommand")
        // Prima di tutto: notifica foreground, sennò Android killa il servizio
        // ("Stopping service due to app idle") e la chat resta Disconnesso.
        startAsForeground()
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

    fun sendChatMessage(
        text: String,
        model: String,
        apiKey: String,
        conversationId: String
    ): Boolean {
        if (lastState.status != BridgeState.Status.AUTHENTICATED) return false
        return bridgeClient?.sendIfConnected(
            BridgeMessage.chatMessage(Message.user(text), model, apiKey, conversationId)
        ) ?: false
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
        private const val CHANNEL_ID = "bridge_service"
        private const val NOTIF_ID = 42
    }
}
