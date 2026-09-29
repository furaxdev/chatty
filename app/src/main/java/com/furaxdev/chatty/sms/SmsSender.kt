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

object SmsSender {

    const val ACTION_SENT = "com.furaxdev.chatty.SMS_SENT"
    const val ACTION_DELIVERED = "com.furaxdev.chatty.SMS_DELIVERED"
    const val EXTRA_URI = "uri"
    const val EXTRA_PART = "part"
    const val EXTRA_PARTS = "parts"

    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** Découpage du texte (pour afficher « 2 SMS · 45 restants » dans la zone de saisie). */
    fun segments(text: String): IntArray = android.telephony.SmsMessage.calculateLength(text, false)

    /**
     * Enregistre le message dans la boîte d'envoi puis l'envoie.
     * Renvoie l'identifiant du fil de discussion.
     */
    fun send(context: Context, address: String, text: String, effect: MessageEffect? = null, threadIdHint: Long = 0L): Long {
        val repo = SmsRepository(context)
        val store = ChattyStore.get(context)
        val signature = store.signature.trim()
        val withSig = if (signature.isNotEmpty()) "$text\n$signature" else text
        val body = EffectCodec.encode(withSig, effect)
        val threadId = if (threadIdHint > 0) threadIdHint else repo.threadIdFor(address)
        val uri = repo.insertOutbox(address, body, threadId) ?: return threadId
        transmit(context, uri, address, body, store.deliveryReports)
        return threadId
    }

    /** Renvoie un message en échec. */
    fun retry(context: Context, messageId: Long) {
        val repo = SmsRepository(context)
        val (address, body) = repo.rawMessage(messageId) ?: return
        val uri = android.net.Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
        repo.updateType(uri, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        transmit(context, uri, address, body, ChattyStore.get(context).deliveryReports)
    }

    private fun transmit(context: Context, uri: android.net.Uri, address: String, body: String, reports: Boolean) {
        val manager = smsManager(context)
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
