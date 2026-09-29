package com.chatty.fr.mms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Écriture des MMS dans le fournisseur Telephony (comme le fait l'appli Messages d'AOSP) :
 * une ligne dans `mms`, les adresses dans `addr`, le contenu dans `part`.
 */
object MmsStore {

    private const val ADDR_FROM = 137
    private const val ADDR_TO = 151
    private const val ADDR_CC = 130

    /** Nos propres numéros (8 derniers chiffres), pour les exclure des destinataires. */
    fun myNumbers(context: Context): Set<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptySet()
        }
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptySet()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().mapNotNull {
                @Suppress("DEPRECATION")
                it.number?.let(::key)?.takeIf { k -> k.length >= 6 }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    fun key(number: String) = number.filter { it.isDigit() }.takeLast(8)

    /** Enregistre un MMS reçu. Renvoie (fil, adresses du fil). */
    fun persistIncoming(context: Context, m: Pdu.Message, subId: Int): Pair<Long, List<String>>? {
        val resolver = context.contentResolver
        // Déjà enregistré ? (l'opérateur renvoie parfois la même notification)
        m.transactionId?.let { tr ->
            resolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), "${Telephony.Mms.TRANSACTION_ID} = ? AND ${Telephony.Mms.MESSAGE_TYPE} = ${Pdu.RETRIEVE_CONF}", arrayOf(tr), null)
                ?.use { if (it.count > 0) return null }
        }
        val mine = myNumbers(context)
        val from = m.from ?: "Inconnu"
        val others = (listOf(from) + m.to + m.cc)
            .filter { it.isNotBlank() && key(it) !in mine }
            .distinctBy { key(it).ifEmpty { it } }
        val recipients = others.ifEmpty { listOf(from) }
        val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())

        val nowSec = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, nowSec)
            put(Telephony.Mms.DATE_SENT, if (m.date > 0) m.date else nowSec)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.RETRIEVE_CONF)
            put(Telephony.Mms.MMS_VERSION, Pdu.VERSION_1_2)
            put(Telephony.Mms.CONTENT_TYPE, m.contentType ?: "application/vnd.wap.multipart.related")
            m.subject?.let { put(Telephony.Mms.SUBJECT, it); put(Telephony.Mms.SUBJECT_CHARSET, Pdu.CHARSET_UTF8) }
            m.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            m.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            put(Telephony.Mms.MESSAGE_SIZE, m.parts.sumOf { it.data.size })
            put(Telephony.Mms.TEXT_ONLY, if (m.parts.all { it.isText || it.isSmil }) 1 else 0)
            if (subId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        insertAddr(context, id, from, ADDR_FROM)
        m.to.forEach { insertAddr(context, id, it, ADDR_TO) }
        m.cc.forEach { insertAddr(context, id, it, ADDR_CC) }
        insertParts(context, id, m.parts)
        return threadId to recipients
    }

    /** Enregistre un MMS à envoyer (boîte d'envoi). */
    fun persistOutgoing(
        context: Context,
        threadId: Long,
        recipients: List<String>,
        text: String?,
        attachments: List<Pdu.Attachment>,
        subId: Int,
    ): Uri? {
        val resolver = context.contentResolver
        val nowSec = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, nowSec)
            put(Telephony.Mms.DATE_SENT, nowSec)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, Pdu.VERSION_1_2)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_SIZE, attachments.sumOf { it.data.size } + (text?.length ?: 0))
            put(Telephony.Mms.TEXT_ONLY, if (attachments.isEmpty()) 1 else 0)
            if (subId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        insertAddr(context, id, "insert-address-token", ADDR_FROM)
        recipients.forEach { insertAddr(context, id, it, ADDR_TO) }
        val parts = attachments.map { Pdu.Part(it.contentType, it.data, name = it.name, contentLocation = it.name) } +
            listOfNotNull(text?.takeIf { it.isNotEmpty() }?.let { Pdu.Part("text/plain", it.toByteArray(), Pdu.CHARSET_UTF8, "text_0.txt") })
        insertParts(context, id, parts)
        return uri
    }

    fun setBox(context: Context, uri: Uri, box: Int) {
        val values = ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    private fun insertAddr(context: Context, msgId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, Pdu.CHARSET_UTF8)
            put(Telephony.Mms.Addr.MSG_ID, msgId)
        }
        runCatching { context.contentResolver.insert(Uri.parse("content://mms/$msgId/addr"), values) }
    }

    private fun insertParts(context: Context, msgId: Long, parts: List<Pdu.Part>) {
        val partUri = Uri.parse("content://mms/$msgId/part")
        parts.forEachIndexed { seq, p ->
            val values = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, msgId)
                put(Telephony.Mms.Part.SEQ, if (p.isSmil) -1 else seq)
                put(Telephony.Mms.Part.CONTENT_TYPE, p.contentType)
                p.name?.let { put(Telephony.Mms.Part.NAME, it); put(Telephony.Mms.Part.FILENAME, it) }
                p.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, it) }
                p.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
                if (p.isText || p.isSmil) {
                    put(Telephony.Mms.Part.CHARSET, Pdu.CHARSET_UTF8)
                    put(Telephony.Mms.Part.TEXT, if (p.isText) p.text() else String(p.data, Charsets.UTF_8))
                }
            }
            val uri = runCatching { context.contentResolver.insert(partUri, values) }.getOrNull() ?: return@forEachIndexed
            if (!p.isText && !p.isSmil) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(p.data) } }
            }
        }
    }
}
