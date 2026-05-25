package com.pascal.claudemobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pascal.claudemobile.data.ClaudeRepository
import com.pascal.claudemobile.data.Message
import com.pascal.claudemobile.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ClaudeRepository(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val serverUrl: String get() = repository.serverUrl
    val apiKey: String get() = repository.apiKey
    fun saveSettings(url: String, key: String) = repository.saveSettings(url, key)

    private var isNewSession = true
    private var streamingId: String? = null

    fun sendMessage(text: String) {
        if (_isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.update { true }
            _error.update { null }

            val userMsg = Message(role = Role.USER, content = text)
            val assistantMsg = Message(role = Role.ASSISTANT, content = "", isStreaming = true)
            streamingId = assistantMsg.id
            _messages.update { it + userMsg + assistantMsg }

            repository.sendMessage(
                text = text,
                isNewSession = isNewSession,
                onChunk = { chunk ->
                    val id = streamingId ?: return@sendMessage
                    _messages.update { msgs ->
                        msgs.map { if (it.id == id) it.copy(content = it.content + chunk) else it }
                    }
                },
                onDone = {
                    val id = streamingId ?: return@sendMessage
                    _messages.update { msgs ->
                        msgs.map { if (it.id == id) it.copy(isStreaming = false) else it }
                    }
                    isNewSession = false
                    _isLoading.update { false }
                },
                onError = { err ->
                    val id = streamingId ?: return@sendMessage
                    _messages.update { msgs ->
                        msgs.map { if (it.id == id) it.copy(content = "Error: $err", isStreaming = false) else it }
                    }
                    _error.update { err }
                    _isLoading.update { false }
                },
            )
        }
    }

    fun newChat() {
        _messages.update { emptyList() }
        _error.update { null }
        _isLoading.update { false }
        isNewSession = true
        streamingId = null
    }

    fun clearError() = _error.update { null }
}
