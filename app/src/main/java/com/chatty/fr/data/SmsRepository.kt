package com.chatty.fr.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.chatty.fr.effects.EffectCodec
import java.util.concurrent.ConcurrentHashMap

/**
 * Accès au fournisseur SMS d'Android. En tant qu'appli SMS par défaut, Chatty est
 * responsable d'écrire elle-même les messages envoyés et reçus.
 */
class SmsRepository(private val context: Context) {

    companion object {
        private const val MMS_SEND_REQ = 128
        private const val MMS_RETRIEVE_CONF = 132
    }

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
        mergeMmsThreads(latest, unread)
        return latest.values.sortedByDescending { it.date }.map {
            it.copy(
                unreadCount = unread[it.threadId] ?: 0,
                pinned = store.isPinned(it.threadId),
                archived = store.isArchived(it.threadId),
                muted = store.isMuted(it.threadId),
            )
        }
    }

    private class ThreadInfo(val id: Long, val addresses: List<String>, val snippet: String?, val date: Long, val count: Int)

    /** Fils de discussion du système (SMS + MMS), avec leurs destinataires. */
    private fun threads(): List<ThreadInfo>? = runCatching {
        val canonical = HashMap<Long, String>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) canonical[c.getLong(0)] = c.getString(1).orEmpty()
        }
        val uri = Telephony.MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon().appendQueryParameter("simple", "true").build()
        resolver.query(
            uri,
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS, Telephony.Threads.SNIPPET, Telephony.Threads.DATE, Telephony.Threads.MESSAGE_COUNT),
            null, null, "${Telephony.Threads.DATE} DESC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val ids = c.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    add(ThreadInfo(c.getLong(0), ids.mapNotNull { canonical[it] }.filter { it.isNotBlank() }, c.getString(2), c.getLong(3), c.getInt(4)))
                }
            }
        }
    }.getOrNull()

    /** Ajoute les conversations dont le dernier message est un MMS (photos, groupes). */
    private fun mergeMmsThreads(latest: LinkedHashMap<Long, Conversation>, unread: HashMap<Long, Int>) {
        val mmsLatest = HashMap<Long, Pair<Long, Int>>() // fil -> (date ms, boîte)
        runCatching {
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
                "${Telephony.Mms.MESSAGE_TYPE} IN (${MMS_SEND_REQ}, ${MMS_RETRIEVE_CONF})", null, "${Telephony.Mms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getLong(0)
                    if (c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX && c.getInt(3) == 0) unread[t] = (unread[t] ?: 0) + 1
                    if (t !in mmsLatest) mmsLatest[t] = c.getLong(1) * 1000 to c.getInt(2)
                }
            }
        }
        if (mmsLatest.isEmpty()) return
        val infos = threads()?.associateBy { it.id } ?: return
        for ((threadId, mms) in mmsLatest) {
            val info = infos[threadId] ?: continue
            val sms = latest[threadId]
            val group = info.addresses.size > 1
            if (sms != null && sms.date >= mms.first && !group) continue
            val address = if (group) info.addresses.joinToString(",") else info.addresses.firstOrNull() ?: sms?.address ?: continue
            val newer = sms == null || mms.first >= sms.date
            val (decoded, effect) = EffectCodec.decode(info.snippet.orEmpty())
            val text = MessageFormat.decodeReply(decoded).second
            latest[threadId] = Conversation(
                threadId = threadId,
                address = address,
                contact = contact(address),
                snippet = if (newer) text.ifBlank { "📷 Photo" } else sms!!.snippet,
                snippetEffect = if (newer) effect else sms!!.snippetEffect,
                date = maxOf(mms.first, sms?.date ?: 0),
                unreadCount = 0,
                lastIsMine = if (newer) mms.second != Telephony.Mms.MESSAGE_BOX_INBOX else sms!!.lastIsMine,
            )
        }
    }

    /** Messages MMS d'un fil (identifiants négatifs pour ne pas croiser ceux des SMS). */
    private fun mmsMessages(threadId: Long, store: ChattyStore): List<Message> {
        data class Row(val id: Long, val date: Long, val box: Int, val read: Boolean, val sub: Int, val subject: String?)
        val rows = ArrayList<Row>()
        runCatching {
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ, Telephony.Mms.SUBSCRIPTION_ID, Telephony.Mms.SUBJECT),
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.MESSAGE_TYPE} IN (${MMS_SEND_REQ}, ${MMS_RETRIEVE_CONF})",
                arrayOf(threadId.toString()), "${Telephony.Mms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) rows += Row(c.getLong(0), c.getLong(1) * 1000, c.getInt(2), c.getInt(3) != 0, c.getInt(4), c.getString(5))
            }
        }
        if (rows.isEmpty()) return emptyList()

        val texts = HashMap<Long, StringBuilder>()
        val attachments = HashMap<Long, MutableList<Attachment>>()
        runCatching {
            resolver.query(
                Uri.parse("content://mms/part"),
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
                "${Telephony.Mms.Part.MSG_ID} IN (${rows.joinToString(",") { it.id.toString() }})", null,
                "${Telephony.Mms.Part.SEQ} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(1)
                    val ct = c.getString(2).orEmpty().lowercase()
                    when {
                        ct == "text/plain" -> texts.getOrPut(mid) { StringBuilder() }.apply {
                            if (isNotEmpty()) append('\n')
                            append(c.getString(3).orEmpty())
                        }
                        ct.startsWith("image/") || ct.startsWith("video/") || ct.startsWith("audio/") ->
                            attachments.getOrPut(mid) { ArrayList() } += Attachment("content://mms/part/${c.getLong(0)}", ct)
                        ct == "text/x-vcard" || ct == "text/vcard" ->
                            attachments.getOrPut(mid) { ArrayList() } += Attachment("content://mms/part/${c.getLong(0)}", ct)
                    }
                }
            }
        }

        return rows.map { r ->
            val raw = texts[r.id]?.toString().orEmpty().ifEmpty { r.subject.orEmpty() }
            val (decoded, effect) = EffectCodec.decode(raw)
            val (quote, body) = MessageFormat.decodeReply(decoded)
            val mine = r.box != Telephony.Mms.MESSAGE_BOX_INBOX
            val id = -r.id
            Message(
                id = id,
                threadId = threadId,
                address = if (mine) "" else mmsSender(r.id).orEmpty(),
                body = body,
                effect = effect,
                date = r.date,
                isMine = mine,
                status = when (r.box) {
                    Telephony.Mms.MESSAGE_BOX_INBOX -> MessageStatus.RECEIVED
                    Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
                    Telephony.Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
                    else -> MessageStatus.SENT
                },
                read = r.read,
                reactions = listOfNotNull(store.reaction(id)?.let { Reaction(it, true) }),
                starred = store.isStarred(id),
                quote = quote,
                subId = r.sub,
                attachments = attachments[r.id].orEmpty(),
                isMms = true,
            )
        }
    }

    private fun mmsSender(mmsId: Long): String? = runCatching {
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = 137", null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

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
        val all = (out + mmsMessages(threadId, store)).sortedBy { it.date }
        return attachReactions(all)
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

    /** Supprime les codes de vérification reçus il y a plus de 24 h. Renvoie le nombre supprimé. */
    fun deleteOldOtps(): Int {
        val limit = System.currentTimeMillis() - 24 * 3_600_000L
        val ids = ArrayList<Long>()
        runCatching {
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY),
                "${Telephony.Sms.DATE} < ?", arrayOf(limit.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) if (MessageFormat.findOtp(c.getString(1).orEmpty()) != null) ids += c.getLong(0)
            }
        }
        ids.forEach(::deleteMessage)
        return ids.size
    }

    fun threadIdFor(address: String): Long =
        if (address.contains(',')) Telephony.Threads.getOrCreateThreadId(context, address.split(',').map { it.trim() }.toSet())
        else Telephony.Threads.getOrCreateThreadId(context, address)

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
        val mmsValues = ContentValues().apply {
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
        }
        runCatching {
            resolver.update(
                Telephony.Mms.CONTENT_URI, mmsValues,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()),
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
        // Supprime SMS et MMS du fil.
        val ok = runCatching {
            resolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null)
        }.isSuccess
        if (!ok) runCatching {
            resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        }
    }

    /** Les MMS ont des identifiants négatifs dans Chatty. */
    fun deleteMessage(id: Long) {
        val uri = if (id < 0) Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, (-id).toString())
        else Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
        runCatching { resolver.delete(uri, null, null) }
    }

    /** Destinataires d'un fil (plusieurs pour une conversation de groupe). */
    fun recipients(threadId: Long): List<String> =
        threads()?.firstOrNull { it.id == threadId }?.addresses.orEmpty()

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
        if (address.contains(',')) {
            // Conversation de groupe : « Alice, Bob et 2 autres »
            val members = address.split(',').map { contact(it.trim()) }
            val names = members.map { it.name?.substringBefore(' ') ?: it.number }
            val label = if (names.size <= 3) names.joinToString(", ") else names.take(2).joinToString(", ") + " et ${names.size - 2} autres"
            return Contact(ChattyStore.get(context).nickname(address) ?: label, address, null)
        }
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
