package com.furaxdev.chatty

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.furaxdev.chatty.data.ChattyStore
import com.furaxdev.chatty.data.Contact
import com.furaxdev.chatty.data.Conversation
import com.furaxdev.chatty.data.Message
import com.furaxdev.chatty.data.ScheduledMessage
import com.furaxdev.chatty.data.SmsRepository
import com.furaxdev.chatty.effects.MessageEffect
import com.furaxdev.chatty.sms.Notifications
import com.furaxdev.chatty.sms.ScheduledSendWorker
import com.furaxdev.chatty.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChattyViewModel(app: Application) : AndroidViewModel(app) {

    val store = ChattyStore.get(app)
    private val repo = SmsRepository(app)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _scheduled = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val scheduled: StateFlow<List<ScheduledMessage>> = _scheduled

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Fil actuellement ouvert (0 = aucun). */
    var openThreadId = 0L
        private set

    private var reloadJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    private val smsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val contactsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            repo.clearContactCache()
            refresh()
        }
    }
    private var observing = false

    init {
        viewModelScope.launch { store.version.drop(1).collect { refresh() } }
    }

    /** À appeler une fois les permissions obtenues. */
    fun start() {
        if (!observing) {
            val resolver = getApplication<Application>().contentResolver
            runCatching {
                resolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, smsObserver)
                resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
                resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
                observing = true
            }
        }
        refresh()
    }

    fun refresh() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val convs = withContext(Dispatchers.IO) { runCatching { repo.conversations(store) }.getOrDefault(emptyList()) }
            _conversations.value = convs
            _loaded.value = true
            if (openThreadId > 0) loadThread(openThreadId)
        }
    }

    private suspend fun loadThread(threadId: Long) {
        val msgs = withContext(Dispatchers.IO) { runCatching { repo.messages(threadId, store) }.getOrDefault(emptyList()) }
        if (openThreadId == threadId) {
            _messages.value = msgs
            _scheduled.value = store.scheduledFor(threadId)
            if (msgs.any { !it.isMine && !it.read }) withContext(Dispatchers.IO) { repo.markThreadRead(threadId) }
        }
    }

    fun openThread(threadId: Long) {
        if (openThreadId != threadId) _messages.value = emptyList()
        openThreadId = threadId
        Notifications.cancel(getApplication(), threadId)
        viewModelScope.launch { loadThread(threadId) }
    }

    fun closeThread() {
        openThreadId = 0L
        _messages.value = emptyList()
    }

    fun contact(address: String): Contact = repo.contact(address)

    suspend fun threadIdFor(address: String): Long = withContext(Dispatchers.IO) { repo.threadIdFor(address) }

    fun send(address: String, text: String, effect: MessageEffect?, threadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            SmsSender.send(getApplication(), address, text, effect, threadId)
        }
    }

    /** Envoi groupé : un SMS individuel par destinataire. */
    fun sendToMany(addresses: List<String>, text: String, effect: MessageEffect?) {
        viewModelScope.launch(Dispatchers.IO) {
            addresses.forEach { SmsSender.send(getApplication(), it, text, effect) }
        }
    }

    fun schedule(address: String, threadId: Long, text: String, effect: MessageEffect?, at: Long) {
        ScheduledSendWorker.schedule(getApplication(), address, threadId, text, effect, at)
    }

    fun cancelScheduled(id: String) = ScheduledSendWorker.cancel(getApplication(), id)
    fun sendScheduledNow(id: String) = viewModelScope.launch(Dispatchers.IO) {
        ScheduledSendWorker.sendNow(getApplication(), id)
    }

    fun retry(messageId: Long) = viewModelScope.launch(Dispatchers.IO) { SmsSender.retry(getApplication(), messageId) }

    fun deleteMessage(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteMessage(id) }

    fun deleteThread(threadId: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteThread(threadId) }

    fun markRead(threadId: Long) = viewModelScope.launch(Dispatchers.IO) { repo.markThreadRead(threadId) }
    fun markUnread(threadId: Long) = viewModelScope.launch(Dispatchers.IO) { repo.markThreadUnread(threadId) }

    suspend fun searchMessages(query: String) = withContext(Dispatchers.IO) { repo.search(query) }
    suspend fun searchContacts(query: String) = withContext(Dispatchers.IO) { repo.searchContacts(query) }

    override fun onCleared() {
        if (observing) {
            getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
            getApplication<Application>().contentResolver.unregisterContentObserver(contactsObserver)
        }
    }
}
