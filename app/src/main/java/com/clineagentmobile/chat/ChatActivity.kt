package com.clineagentmobile.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clineagentmobile.R
import com.clineagentmobile.bridge.BridgeListener
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.bridge.BridgeState
import com.clineagentmobile.model.ActionCard
import com.clineagentmobile.model.BridgeMessage
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.DiffFile
import com.clineagentmobile.model.Message
import com.clineagentmobile.storage.EncryptedPrefsManager

/**
 * Chat screen: connects to the bridge over WebSocket and streams the Cline
 * agent's response. Input is enabled only once the bridge reports it is
 * connected and authenticated.
 */
class ChatActivity : AppCompatActivity() {

    private val vm: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private lateinit var statusText: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var inputEdit: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var loadingBar: ProgressBar

    private var bridgeService: BridgeService? = null
    private var bridgeConfigured = true

    private val bridgeListener = object : BridgeListener {
        override fun onBridgeStateChanged(state: BridgeState) {
            runOnUiThread { vm.updateBridgeState(state) }
        }
        override fun onMessageReceived(message: BridgeMessage) = Unit
        override fun onConnected() {
            runOnUiThread {
                vm.updateBridgeState(BridgeState(connected = true, status = BridgeState.Status.CONNECTED))
            }
        }
        override fun onDisconnected() {
            runOnUiThread {
                vm.endGeneration()
                vm.updateBridgeState(BridgeState(status = BridgeState.Status.DISCONNECTED))
            }
        }
        override fun onChatStream(text: String) {
            runOnUiThread { vm.appendStream(text) }
        }
        override fun onChatMessage(message: Message) {
            runOnUiThread { vm.addAssistantMessage(message) }
        }
        override fun onActionCard(actionCard: ActionCard) = Unit
        override fun onActionApproved(actionId: String) = Unit
        override fun onActionDenied(actionId: String) = Unit
        override fun onDiffUpdated(diffFile: DiffFile) = Unit
        override fun onCommandReceived(command: String) = Unit
        override fun onCodespacesUpdated(codespaces: List<Codespace>) = Unit
        override fun onCodespacesError(error: String) {
            runOnUiThread { vm.setError(error) }
        }
        override fun onError(error: String) {
            runOnUiThread {
                vm.endGeneration()
                vm.setError(error)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as BridgeService.LocalBinder).getService()
            bridgeService = service
            service.registerListener(bridgeListener)
            vm.updateBridgeState(service.getBridgeStatus())
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bridgeService?.unregisterListener(bridgeListener)
            bridgeService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        statusText = findViewById(R.id.status_text)
        statusProgress = findViewById(R.id.status_progress)
        inputEdit = findViewById(R.id.input_edit_text)
        sendButton = findViewById(R.id.send_button)
        stopButton = findViewById(R.id.stop_button)
        loadingBar = findViewById(R.id.loading_progress_bar)

        adapter = MessageAdapter()
        val list = findViewById<RecyclerView>(R.id.messages_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null

        vm.messages.observe(this) {
            adapter.submit(it)
            if (it.isNotEmpty()) list.scrollToPosition(it.size - 1)
        }
        vm.bridgeState.observe(this) { renderState(it) }
        vm.isGenerating.observe(this) { generating ->
            stopButton.visibility = if (generating) View.VISIBLE else View.GONE
            statusProgress.visibility = if (generating) View.VISIBLE else View.GONE
            loadingBar.visibility = View.GONE
        }
        vm.error.observe(this) { err ->
            if (err.isNotBlank()) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                vm.clearError()
            }
        }

        sendButton.setOnClickListener { sendMessage() }
        stopButton.setOnClickListener {
            bridgeService?.sendChatStop()
            vm.endGeneration()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BridgeService::class.java)
        try { startService(intent) } catch (e: Exception) { }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        try { unbindService(connection) } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        bridgeConfigured = try {
            !EncryptedPrefsManager.getInstance(applicationContext).getBridgeUrl().isNullOrBlank()
        } catch (e: Exception) { false }
        if (!bridgeConfigured) {
            statusText.text = getString(R.string.chat_status_not_configured)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_connecting))
        }
    }

    private fun sendMessage() {
        val text = inputEdit.text.toString().trim()
        if (text.isEmpty()) return
        val service = bridgeService
        if (service == null || !bridgeConfigured) {
            Toast.makeText(this, R.string.chat_bridge_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        inputEdit.setText("")
        vm.addMessage(Message.user(text))
        service.sendChatMessage(text)
    }

    private fun renderState(state: BridgeState) {
        if (!bridgeConfigured) return
        val (textRes, colorRes) = when (state.status) {
            BridgeState.Status.AUTHENTICATED ->
                R.string.chat_status_authenticated to R.color.status_running
            BridgeState.Status.CONNECTED ->
                R.string.chat_status_connected to R.color.status_connected
            BridgeState.Status.CONNECTING, BridgeState.Status.RECONNECTING ->
                R.string.chat_status_connecting to R.color.status_connecting
            BridgeState.Status.ERROR ->
                R.string.chat_status_error to R.color.status_disconnected
            BridgeState.Status.DISCONNECTED ->
                R.string.chat_status_disconnected to R.color.status_disconnected
        }
        statusText.text = getString(textRes)
        statusText.setTextColor(ContextCompat.getColor(this, colorRes))
        val ready = state.connected && state.authenticated
        inputEdit.isEnabled = ready
        sendButton.isEnabled = ready
    }
}
