package com.pascal.claudemobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pascal.claudemobile.data.ClaudeRepository
import com.pascal.claudemobile.data.LocalSessionStore
import com.pascal.claudemobile.data.Logger
import com.pascal.claudemobile.data.Message
import com.pascal.claudemobile.data.Role
import com.pascal.claudemobile.data.SessionListEntry
import com.pascal.claudemobile.data.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ClaudeRepository(application)
    private val localStore = LocalSessionStore(application)

    private val _tabs = MutableStateFlow<List<Tab>>(listOf(Tab(title = Tab.DEFAULT_TITLE)))
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionListEntry>>(emptyList())
    val sessions: StateFlow<List<SessionListEntry>> = _sessions.asStateFlow()

    val serverUrl: String get() = repository.serverUrl
    val apiKey: String get() = repository.apiKey
    fun saveSettings(url: String, key: String) = repository.saveSettings(url, key)

    val activeTab: Tab? get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    init {
        _sessions.update { localStore.listEntries() }
        viewModelScope.launch(Dispatchers.IO) {
            val health = repository.checkHealth()
            Logger.log("HEALTH", health)
        }
        syncRecentSessions(limit = 10)
    }

    fun newTab() {
        val tab = Tab(title = Tab.DEFAULT_TITLE)
        _tabs.update { it + tab }
        _activeTabId.update { tab.id }
    }

    fun closeTab(tabId: String) {
        val current = _tabs.value
        if (current.size <= 1) {
            _tabs.update { listOf(Tab(title = Tab.DEFAULT_TITLE)) }
            _activeTabId.update { _tabs.value.first().id }
            return
        }
        val idx = current.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val remaining = current.filterNot { it.id == tabId }
        _tabs.update { remaining }
        if (_activeTabId.value == tabId) {
            _activeTabId.update { remaining[idx.coerceAtMost(remaining.lastIndex)].id }
        }
    }

    fun switchTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) _activeTabId.update { tabId }
    }

    fun clearError() = _error.update { null }

    fun sendMessage(text: String) {
        val tab = activeTab ?: return
        if (tab.isLoading) return

        val streamingMsg = Message(role = Role.ASSISTANT, content = "", isStreaming = true)
        val userMsg = Message(role = Role.USER, content = text)

        updateTab(tab.id) { t ->
            val title = if (t.title == Tab.DEFAULT_TITLE) Tab.titleFrom(text) else t.title
            t.copy(
                title = title,
                messages = t.messages + userMsg + streamingMsg,
                isLoading = true,
            )
        }

        val streamingId = streamingMsg.id
        val tabId = tab.id

        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(
                text = text,
                sessionId = _tabs.value.firstOrNull { it.id == tabId }?.sessionId,
                onSession = { sid ->
                    updateTab(tabId) { it.copy(sessionId = sid) }
                },
                onChunk = { chunk ->
                    updateTab(tabId) { t ->
                        t.copy(messages = t.messages.map {
                            if (it.id == streamingId) it.copy(content = it.content + chunk) else it
                        })
                    }
                },
                onDone = {
                    updateTab(tabId) { t ->
                        t.copy(
                            isLoading = false,
                            messages = t.messages.map {
                                if (it.id == streamingId) it.copy(isStreaming = false) else it
                            },
                        )
                    }
                    persistActiveTab(tabId)
                },
                onError = { err ->
                    _error.update { err }
                    updateTab(tabId) { t ->
                        t.copy(
                            isLoading = false,
                            messages = t.messages.map {
                                if (it.id == streamingId) it.copy(content = "Error: $err", isStreaming = false) else it
                            },
                        )
                    }
                },
            )
        }
    }

    /** Manual refresh from the history drawer — same path as startup sync. */
    fun refreshSessions() = syncRecentSessions(limit = 10)

    /**
     * Pull the N most-recently-modified sessions from the proxy and cache them
     * locally, then re-emit the merged (local ∪ remote) list. Local-only sessions
     * stay visible when the proxy is unreachable.
     */
    private fun syncRecentSessions(limit: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val remote = repository.listSessions()
            if (remote.isEmpty()) {
                _sessions.update { localStore.listEntries() }
                return@launch
            }
            remote.asSequence().take(limit).forEach { entry ->
                val localTs = localStore.localModified(entry.id)
                if (localTs == null || entry.modified > localTs) {
                    val msgs = repository.getSessionMessages(entry.id)
                    if (msgs.isNotEmpty()) localStore.save(entry, msgs)
                }
            }
            _sessions.update { localStore.listEntries() }
            Logger.log("SYNC", "Synced top $limit sessions; local count=${_sessions.value.size}")
        }
    }

    fun openSession(entry: SessionListEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _tabs.value.firstOrNull { it.sessionId == entry.id }
            if (existing != null) {
                _activeTabId.update { existing.id }
                return@launch
            }
            val cached = localStore.loadMessages(entry.id)
            val msgs = cached.ifEmpty { repository.getSessionMessages(entry.id) }
            val tab = Tab(
                title = Tab.titleFrom(entry.title),
                messages = msgs,
                sessionId = entry.id,
            )
            _tabs.update { it + tab }
            _activeTabId.update { tab.id }
        }
    }

    /** Delete locally cached sessions older than [days] days. */
    fun purgeLocalOlderThan(days: Int, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = (System.currentTimeMillis() / 1000L) - days * 86_400L
            val deleted = localStore.purgeOlderThan(cutoff)
            _sessions.update { localStore.listEntries() }
            Logger.log("PURGE", "Deleted $deleted local sessions older than $days days")
            onResult(deleted)
        }
    }

    private fun persistActiveTab(tabId: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val sid = tab.sessionId ?: return
        val entry = SessionListEntry(
            id = sid,
            title = if (tab.title == Tab.DEFAULT_TITLE) "(untitled)" else tab.title,
            modified = System.currentTimeMillis() / 1000L,
            turns = tab.messages.size,
        )
        localStore.save(entry, tab.messages)
        _sessions.update { localStore.listEntries() }
    }

    private fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
    }
}
