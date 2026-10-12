package com.clineagentmobile.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.clineagentmobile.bridge.BridgeState
import com.clineagentmobile.model.ActionCard
import com.clineagentmobile.model.Codespace
import com.clineagentmobile.model.Message

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _actionCards = MutableLiveData<List<ActionCard>>(emptyList())
    val actionCards: LiveData<List<ActionCard>> = _actionCards

    private val _codespaces = MutableLiveData<List<Codespace>>(emptyList())
    val codespaces: LiveData<List<Codespace>> = _codespaces

    private val _bridgeState = MutableLiveData(BridgeState())
    val bridgeState: LiveData<BridgeState> = _bridgeState

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private var streamingId: String? = null

    private val _currentCommand = MutableLiveData("")
    val currentCommand: LiveData<String> = _currentCommand

    private val _error = MutableLiveData("")
    val error: LiveData<String> = _error

    fun addMessage(message: Message) {
        _messages.value = (_messages.value ?: emptyList()) + message
    }

    /** Start (or extend) the in-progress assistant bubble while Cline streams. */
    fun appendStream(delta: String) {
        val list = (_messages.value ?: emptyList()).toMutableList()
        if (streamingId == null) {
            val m = Message.assistant("")
            streamingId = m.id
            list.add(m)
            _isGenerating.value = true
        }
        val idx = list.indexOfLast { it.id == streamingId }
        if (idx >= 0) {
            val old = list[idx]
            list[idx] = old.copy(text = old.text + delta)
        }
        _messages.value = list
    }

    /** Finalize the streaming bubble with the authoritative final text (if any). */
    fun finalizeStream(finalText: String? = null) {
        val id = streamingId ?: return
        streamingId = null
        _isGenerating.value = false
        val list = (_messages.value ?: emptyList()).toMutableList()
        val idx = list.indexOfLast { it.id == id }
        if (idx >= 0) {
            val old = list[idx]
            list[idx] = old.copy(text = finalText ?: old.text)
            _messages.value = list
        }
    }

    /** Add a completed assistant message, replacing any in-progress stream bubble. */
    fun addAssistantMessage(message: Message) {
        if (streamingId != null) {
            finalizeStream(message.text)
        } else {
            _isGenerating.value = false
            addMessage(message)
        }
    }

    fun endGeneration() {
        _isGenerating.value = false
        streamingId = null
    }

    fun addActionCard(actionCard: ActionCard) {
        _actionCards.value = (_actionCards.value ?: emptyList()) + actionCard
    }

    fun removeActionCard(actionId: String) {
        _actionCards.value = (_actionCards.value ?: emptyList()).filterNot { it.id == actionId }
    }

    fun updateBridgeState(state: BridgeState) {
        _bridgeState.value = state
    }

    fun addCodespaces(codespaces: List<Codespace>) {
        _codespaces.value = codespaces
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setCurrentCommand(command: String) {
        _currentCommand.value = command
    }

    fun setError(error: String) {
        _error.value = error
    }

    fun clearError() {
        _error.value = ""
    }

    fun reset() {
        _messages.value = emptyList()
        _actionCards.value = emptyList()
        _codespaces.value = emptyList()
        _bridgeState.value = BridgeState()
        _isLoading.value = false
        _isGenerating.value = false
        streamingId = null
        _currentCommand.value = ""
        _error.value = ""
    }
}

