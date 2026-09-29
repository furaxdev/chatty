package com.furaxdev.chatty.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.furaxdev.chatty.data.ChattyStore
import com.furaxdev.chatty.data.SmsRepository
import com.furaxdev.chatty.effects.EffectCodec
import com.furaxdev.chatty.effects.MessageEffect
import com.furaxdev.chatty.mms.MmsTransport
import com.furaxdev.chatty.mms.Pdu

object SmsSender {

    const val ACTION_SENT = "com.furaxdev.chatty.SMS_SENT"
    const val ACTION_DELIVERED = "com.furaxdev.chatty.SMS_DELIVERED"
    const val EXTRA_URI = "uri"
    const val EXTRA_PART = "part"
    const val EXTRA_PARTS = "parts"

    private fun smsManager(context: Context, subId: Int = -1): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        if (subId < 0) return base
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base.createForSubscriptionId(subId)
        else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
    }

    /** Découpage du texte (pour afficher « 2 SMS · 45 restants » dans la zone de saisie). */
    fun segments(text: String): IntArray = android.telephony.SmsMessage.calculateLength(text, false)

    /**
     * Enregistre le message dans la boîte d'envoi puis l'envoie.
     * Renvoie l'identifiant du fil de discussion.
     */
    fun send(
        context: Context,
        address: String,
        text: String,
        effect: MessageEffect? = null,
        threadIdHint: Long = 0L,
        withSignature: Boolean = true,
        subId: Int = -1,
    ): Long {
        val repo = SmsRepository(context)
        val store = ChattyStore.get(context)
        val signature = if (withSignature) store.signature.trim() else ""
        val withSig = if (signature.isNotEmpty()) "$text\n$signature" else text
        val body = EffectCodec.encode(withSig, effect)
        if (address.contains(',')) {
            // Conversation de groupe : un seul MMS pour tout le monde.
            val recipients = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val threadId = if (threadIdHint > 0) threadIdHint
            else android.provider.Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
            MmsTransport.send(context, threadId, recipients, body, emptyList(), subId)
            return threadId
        }
        val threadId = if (threadIdHint > 0) threadIdHint else repo.threadIdFor(address)
        val uri = repo.insertOutbox(address, body, threadId, subId) ?: return threadId
        transmit(context, uri, address, body, store.deliveryReports, subId)
        return threadId
    }

    /** Envoie des photos (et un texte éventuel) en MMS, à une personne ou à un groupe. */
    fun sendMedia(
        context: Context,
        address: String,
        text: String?,
        attachments: List<Pdu.Attachment>,
        effect: MessageEffect? = null,
        threadIdHint: Long = 0L,
        subId: Int = -1,
    ): Long {
        val recipients = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val threadId = if (threadIdHint > 0) threadIdHint
        else android.provider.Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
        val body = text?.takeIf { it.isNotBlank() }?.let { EffectCodec.encode(it, effect) }
        MmsTransport.send(context, threadId, recipients, body, attachments, subId)
        return threadId
    }

    /** Renvoie un message en échec. */
    fun retry(context: Context, messageId: Long) {
        if (messageId < 0) return // MMS : pas de renvoi automatique

        val repo = SmsRepository(context)
        val (address, body) = repo.rawMessage(messageId) ?: return
        val uri = android.net.Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
        repo.updateType(uri, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        transmit(context, uri, address, body, ChattyStore.get(context).deliveryReports)
    }

    private fun transmit(context: Context, uri: android.net.Uri, address: String, body: String, reports: Boolean, subId: Int = -1) {
        val manager = smsManager(context, subId)
        val parts = manager.divideMessage(body)
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent>()
        parts.indices.forEach { i ->
            sent += statusIntent(context, ACTION_SENT, uri, i, parts.size)
            if (reports) delivered += statusIntent(context, ACTION_DELIVERED, uri, i, parts.size)
        }
        try {
            manager.sendMultipartTextMessage(address, null, parts, sent, if (reports) delivered else null)
        } catch (e: Exception) {
            SmsRepository(context).updateType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
        }
    }

    private fun statusIntent(context: Context, action: String, uri: android.net.Uri, part: Int, parts: Int): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_URI, uri.toString())
            putExtra(EXTRA_PART, part)
            putExtra(EXTRA_PARTS, parts)
        }
        val requestCode = (uri.toString() + action + part).hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
