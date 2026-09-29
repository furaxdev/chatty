package com.furaxdev.chatty.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.furaxdev.chatty.effects.EffectCodec
import java.util.concurrent.ConcurrentHashMap

/**
 * Accès au fournisseur SMS d'Android. En tant qu'appli SMS par défaut, Chatty est
 * responsable d'écrire elle-même les messages envoyés et reçus.
 */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val contactCache = ConcurrentHashMap<String, Contact>()

    fun conversations(store: ChattyStore): List<Conversation> {
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
        )
        val latest = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        resolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
            while (c.moveToNext()) {
                val threadId = c.getLong(0)
                val type = c.getInt(4)
                if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                if (type == Telephony.Sms.MESSAGE_TYPE_INBOX && c.getInt(5) == 0) {
                    unread[threadId] = (unread[threadId] ?: 0) + 1
                }
                if (threadId in latest) continue
                val address = c.getString(1) ?: continue
                val (decoded, effect) = EffectCodec.decode(c.getString(2).orEmpty())
                val body = MessageFormat.decodeReply(decoded).second
                latest[threadId] = Conversation(
                    threadId = threadId,
                    address = address,
                    contact = contact(address),
                    snippet = body,
                    snippetEffect = effect,
                    date = c.getLong(3),
                    unreadCount = 0,
                    lastIsMine = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                )
            }
        }
        return latest.values.map {
            it.copy(
                unreadCount = unread[it.threadId] ?: 0,
                pinned = store.isPinned(it.threadId),
                archived = store.isArchived(it.threadId),
                muted = store.isMuted(it.threadId),
            )
        }
    }

    fun messages(threadId: Long, store: ChattyStore): List<Message> {
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
            Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID,
        )
        val out = ArrayList<Message>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, projection,
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                val id = c.getLong(0)
                val (decoded, effect) = EffectCodec.decode(c.getString(2).orEmpty())
                val (quote, body) = MessageFormat.decodeReply(decoded)
                val status = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                    else -> if (c.getInt(6) == Telephony.Sms.STATUS_COMPLETE) MessageStatus.DELIVERED else MessageStatus.SENT
                }
                out += Message(
                    id = id,
                    threadId = threadId,
                    address = c.getString(1).orEmpty(),
                    body = body,
                    effect = effect,
                    date = c.getLong(3),
                    isMine = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = status,
                    read = c.getInt(5) != 0,
                    reactions = listOfNotNull(store.reaction(id)?.let { Reaction(it, true) }),
                    starred = store.isStarred(id),
                    quote = quote,
                    subId = c.getInt(7),
                )
            }
        }
        return attachReactions(out)
    }

    /**
     * Les réactions reçues par SMS (« A réagi avec ❤️ à « … » », Tapback iPhone…) sont
     * rattachées au message visé au lieu d'apparaître comme des messages à part.
     */
    private fun attachReactions(raw: List<Message>): List<Message> {
        val visible = ArrayList<Message>(raw.size)
        for (m in raw) {
            val r = MessageFormat.parseReaction(m.body)
            if (r != null) {
                val idx = visible.indexOfLast { MessageFormat.matches(r.target, it.body) }
                if (idx >= 0) {
                    val target = visible[idx]
                    // Une seule réaction par personne et par message.
                    val others = target.reactions.filterNot { it.mine == m.isMine }
                    val mineBefore = target.reactions.filter { it.mine == m.isMine }
                    val updated = when {
                        !r.removed -> others + Reaction(r.emoji, m.isMine)
                        else -> others + mineBefore.filterNot { it.emoji == r.emoji }
                    }
                    visible[idx] = target.copy(reactions = updated)
                    continue
                }
            }
            visible += m
        }
        return visible
    }

    /** Messages par identifiant (favoris). */
    fun messagesByIds(ids: Collection<Long>): List<Message> {
        if (ids.isEmpty()) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
        )
        val out = ArrayList<Message>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, projection,
            "${Telephony.Sms._ID} IN (${ids.joinToString(",")})", null, "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val (decoded, effect) = EffectCodec.decode(c.getString(3).orEmpty())
                val (quote, body) = MessageFormat.decodeReply(decoded)
                val mine = c.getInt(5) != Telephony.Sms.MESSAGE_TYPE_INBOX
                out += Message(
                    id = c.getLong(0), threadId = c.getLong(1), address = c.getString(2).orEmpty(),
                    body = body, effect = effect, date = c.getLong(4), isMine = mine,
                    status = if (mine) MessageStatus.SENT else MessageStatus.RECEIVED, read = true,
                    starred = true, quote = quote,
                )
            }
        }
        return out
    }

    /** Recherche plein texte dans tous les messages. */
    fun search(query: String, limit: Int = 50): List<Message> {
        if (query.isBlank()) return emptyList()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
        )
        val out = ArrayList<Message>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, projection,
            "${Telephony.Sms.BODY} LIKE ?", arrayOf("%$query%"),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val (decoded, effect) = EffectCodec.decode(c.getString(3).orEmpty())
                val body = MessageFormat.decodeReply(decoded).second
                val mine = c.getInt(5) != Telephony.Sms.MESSAGE_TYPE_INBOX
                out += Message(
                    id = c.getLong(0), threadId = c.getLong(1), address = c.getString(2).orEmpty(),
                    body = body, effect = effect, date = c.getLong(4), isMine = mine,
                    status = if (mine) MessageStatus.SENT else MessageStatus.RECEIVED, read = true,
                )
            }
        }
        return out
    }

    fun latestIncomingBody(threadId: Long): String? {
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC",
        )?.use { c -> if (c.moveToFirst()) return EffectCodec.decode(c.getString(0).orEmpty()).first }
        return null
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(context, address)

    fun markThreadRead(threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            resolver.update(
                Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()),
            )
        }
    }

    fun markThreadUnread(threadId: Long) {
        // Marque le dernier message reçu comme non lu.
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { c ->
            if (c.moveToFirst()) {
                val values = ContentValues().apply { put(Telephony.Sms.READ, 0) }
                runCatching { resolver.update(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, c.getLong(0).toString()), values, null, null) }
            }
        }
    }

    fun deleteThread(threadId: Long) {
        runCatching {
            resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        }
    }

    fun deleteMessage(id: Long) {
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null) }
    }

    fun insertInbox(address: String, body: String, date: Long, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching { resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    fun insertOutbox(address: String, body: String, threadId: Long, subId: Int = -1): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching { resolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull()
    }

    fun updateType(uri: Uri, type: Int) {
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
        runCatching { resolver.update(uri, values, null, null) }
    }

    fun updateStatus(uri: Uri, status: Int) {
        val values = ContentValues().apply { put(Telephony.Sms.STATUS, status) }
        runCatching { resolver.update(uri, values, null, null) }
    }

    /** Relit un message (pour renvoyer un SMS en échec). */
    fun rawMessage(id: Long): Pair<String, String>? {
        resolver.query(
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()),
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) return c.getString(0).orEmpty() to c.getString(1).orEmpty() }
        return null
    }

    fun contact(address: String): Contact {
        val base = contactCache.getOrPut(address) { lookupContact(address) }
        val nick = ChattyStore.get(context).nickname(address)
        return if (nick != null) base.copy(name = nick) else base
    }

    fun clearContactCache() = contactCache.clear()

    private fun lookupContact(address: String): Contact {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            resolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) Contact(c.getString(0), address, c.getString(1)) else null
            }
        }.getOrNull() ?: Contact(null, address)
    }

    /** Contacts avec numéro, filtrés par nom ou numéro. */
    fun searchContacts(query: String, limit: Int = 60): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        val selection = if (query.isBlank()) null
        else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                selection, args, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(1) ?: continue
                    val key = number.filter { it.isDigit() || it == '+' }
                    if (!seen.add(key)) continue
                    out += Contact(c.getString(0), number, c.getString(2))
                }
            }
        }
        return out
    }
}
