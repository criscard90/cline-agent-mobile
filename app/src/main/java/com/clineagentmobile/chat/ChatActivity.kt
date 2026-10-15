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
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.clineagentmobile.ai.OpenRouterCatalog
import com.clineagentmobile.ai.OpenRouterModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clineagentmobile.R
import com.clineagentmobile.bridge.BridgeListener
import com.clineagentmobile.bridge.BridgeService
import com.clineagentmobile.bridge.BridgeState
import com.clineagentmobile.model.ActionCard
import com.clineagentmobile.model.BridgeMessage
import com.clineagentmobile.model.ChatConversation
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.DiffFile
import com.clineagentmobile.model.Message
import com.clineagentmobile.storage.EncryptedPrefsManager
import kotlinx.coroutines.launch

/**
 * Chat screen: connects to the bridge over WebSocket and streams the Cline
 * agent's response. Input is enabled only once the bridge reports it is
 * connected and authenticated.
 */
class ChatActivity : AppCompatActivity() {

    private val vm: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private lateinit var statusText: TextView
    private lateinit var inputEdit: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var generationIndicator: View
    private lateinit var modelSelectButton: Button
    private lateinit var conversationSelectButton: Button
    private lateinit var newChatButton: Button

    private var bridgeService: BridgeService? = null
    private var bridgeConfigured = true
    private var activeBridgeUrl = ""
    private lateinit var activeConversation: ChatConversation
    private var selectedModel = ""
    private var availableModels: List<OpenRouterModel> = emptyList()
    private lateinit var prefs: EncryptedPrefsManager

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
                vm.setError(
                    if (error.contains("spawn cline ENOENT", ignoreCase = true)) {
                        getString(R.string.chat_cline_missing)
                    } else {
                        error
                    }
                )
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
        inputEdit = findViewById(R.id.input_edit_text)
        sendButton = findViewById(R.id.send_button)
        stopButton = findViewById(R.id.stop_button)
        generationIndicator = findViewById(R.id.generation_indicator)
        modelSelectButton = findViewById(R.id.model_select_button)
        conversationSelectButton = findViewById(R.id.conversation_select_button)
        newChatButton = findViewById(R.id.new_chat_button)
        prefs = EncryptedPrefsManager.getInstance(applicationContext)
        activeBridgeUrl = configuredBridgeUrl()
        activeConversation = prefs.getOrCreateActiveChat(activeBridgeUrl)
        vm.restoreMessages(activeConversation.messages)
        selectedModel = prefs.getPreferredModel()
        updateModelButton()
        updateConversationButton()

        adapter = MessageAdapter()
        val list = findViewById<RecyclerView>(R.id.messages_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null

        vm.messages.observe(this) {
            adapter.submit(it)
            val updatedConversation = activeConversation.copy(
                title = activeConversation.titleFor(it),
                messages = it,
                updatedAt = System.currentTimeMillis()
            )
            activeConversation = updatedConversation
            prefs.saveChatConversation(updatedConversation)
            updateConversationButton()
            if (it.isNotEmpty()) list.scrollToPosition(it.size - 1)
        }
        vm.bridgeState.observe(this) { renderState(it) }
        vm.isGenerating.observe(this) { generating ->
            stopButton.visibility = if (generating) View.VISIBLE else View.GONE
            generationIndicator.visibility = if (generating) View.VISIBLE else View.GONE
            conversationSelectButton.isEnabled = !generating
            newChatButton.isEnabled = !generating
        }
        vm.error.observe(this) { err ->
            if (err.isNotBlank()) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                vm.clearError()
            }
        }

        sendButton.setOnClickListener { sendMessage() }
        modelSelectButton.setOnClickListener { chooseModel() }
        conversationSelectButton.setOnClickListener { chooseConversation() }
        newChatButton.setOnClickListener { startNewChat() }
        stopButton.setOnClickListener {
            bridgeService?.sendChatStop()
            vm.endGeneration()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BridgeService::class.java)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            try { startService(intent) } catch (e2: Exception) { }
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        try { unbindService(connection) } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        val configuredUrl = configuredBridgeUrl()
        bridgeConfigured = configuredUrl.isNotBlank()
        if (configuredUrl != activeBridgeUrl) {
            activateConversation(configuredUrl, prefs.getOrCreateActiveChat(configuredUrl))
        }
        if (!bridgeConfigured) {
            statusText.text = getString(R.string.chat_status_not_configured)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_connecting))
        }
    }

    private fun configuredBridgeUrl(): String = prefs.getBridgeUrl()?.trim().orEmpty()

    private fun activateConversation(bridgeUrl: String, conversation: ChatConversation) {
        activeBridgeUrl = bridgeUrl
        activeConversation = conversation
        updateConversationButton()
        vm.replaceMessages(conversation.messages)
    }

    private fun chooseConversation() {
        val conversations = prefs.getChatConversations(activeBridgeUrl)
        if (conversations.isEmpty()) return

        val labels = conversations.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_history_dialog_title)
            .setItems(labels) { _, index ->
                val selected = conversations[index]
                prefs.setActiveChat(activeBridgeUrl, selected.id)
                activateConversation(activeBridgeUrl, selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startNewChat() {
        val conversation = prefs.createChatConversation(activeBridgeUrl)
        activateConversation(activeBridgeUrl, conversation)
    }

    private fun updateConversationButton() {
        conversationSelectButton.text =
            "${activeConversation.title}  v"
    }

    private fun ChatConversation.titleFor(messages: List<Message>): String {
        if (title != ChatConversation.DEFAULT_TITLE) return title
        val firstUserMessage = messages.firstOrNull { it.role == Message.Role.USER }
            ?.text
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
        return when {
            firstUserMessage.isBlank() -> title
            firstUserMessage.length <= 40 -> firstUserMessage
            else -> "${firstUserMessage.take(37)}..."
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
        val apiKey = prefs.getOpenRouterKey()
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, R.string.chat_model_missing_key, Toast.LENGTH_LONG).show()
            return
        }
        if (!selectedModel.endsWith(":free")) {
            chooseModel()
            return
        }
        if (!service.sendChatMessage(text, selectedModel, apiKey, activeConversation.id)) {
            Toast.makeText(this, R.string.chat_bridge_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        inputEdit.setText("")
        vm.addMessage(Message.user(text))
        vm.startGeneration()
    }

    private fun chooseModel() {
        modelSelectButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val models = OpenRouterCatalog.fetchFreeToolModels()
                availableModels = models
                if (models.isEmpty()) {
                    Toast.makeText(this@ChatActivity, R.string.chat_model_no_free_models, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val labels = models.map { "${it.name} · ${it.id}" }.toTypedArray()
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle(R.string.chat_model_dialog_title)
                    .setItems(labels) { _, index ->
                        selectedModel = models[index].id
                        prefs.savePreferredModel(selectedModel)
                        updateModelButton()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatActivity,
                    getString(R.string.chat_model_load_error, e.localizedMessage ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                modelSelectButton.isEnabled = true
            }
        }
    }

    private fun updateModelButton() {
        val selected = availableModels.firstOrNull { it.id == selectedModel }
        val label = selected?.let { "${it.name} · ${it.id}" }
            ?: selectedModel.takeIf { it.isNotBlank() }
            ?: getString(R.string.chat_model_loading)
        modelSelectButton.text = getString(R.string.chat_model, label)
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
