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

    private val _currentCommand = MutableLiveData("")
    val currentCommand: LiveData<String> = _currentCommand

    private val _error = MutableLiveData("")
    val error: LiveData<String> = _error

    fun addMessage(message: Message) {
        _messages.value = (_messages.value ?: emptyList()) + message
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
        _currentCommand.value = ""
        _error.value = ""
    }
}

