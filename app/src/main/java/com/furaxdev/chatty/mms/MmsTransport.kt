package com.furaxdev.chatty.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.furaxdev.chatty.data.ChattyStore
import com.furaxdev.chatty.sms.Notifications
import java.io.File

/** Envoi et téléchargement des MMS via le service MMS du système. */
object MmsTransport {

    const val ACTION_SENT = "com.furaxdev.chatty.MMS_SENT"
    const val ACTION_DOWNLOADED = "com.furaxdev.chatty.MMS_DOWNLOADED"
    const val ACTION_ACK = "com.furaxdev.chatty.MMS_ACK"
    private const val EXTRA_FILE = "file"
    private const val EXTRA_URI = "uri"
    private const val EXTRA_SUB = "sub"
    private const val EXTRA_TR = "tr"

    private fun authority(context: Context) = "${context.packageName}.mmsfiles"

    private fun manager(context: Context, subId: Int): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        if (subId < 0) return base
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base.createForSubscriptionId(subId)
        else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
    }

    private fun newFile(context: Context, prefix: String): File {
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        return File(dir, "$prefix-${System.nanoTime()}.pdu")
    }

    private fun result(context: Context, action: String, file: File, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MmsResultReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_FILE, file.absolutePath)
            extras()
        }
        // MUTABLE : le système ajoute le résultat (PDU de réponse) dans l'intent.
        return PendingIntent.getBroadcast(
            context, file.name.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** Taille maximale d'un MMS pour l'opérateur (300 Ko par défaut). */
    fun maxSize(context: Context, subId: Int = -1): Int = runCatching {
        manager(context, subId).carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024)
    }.getOrDefault(300 * 1024).coerceAtLeast(100 * 1024)

    /** Envoie un MMS (message de groupe et/ou pièces jointes). */
    fun send(
        context: Context,
        threadId: Long,
        recipients: List<String>,
        text: String?,
        attachments: List<Pdu.Attachment>,
        subId: Int = -1,
    ) {
        val uri = MmsStore.persistOutgoing(context, threadId, recipients, text, attachments, subId)
        val file = newFile(context, "send")
        file.writeBytes(Pdu.buildSendReq(recipients, text, attachments))
        val contentUri = FileProvider.getUriForFile(context, authority(context), file)
        val pi = result(context, ACTION_SENT, file) { putExtra(EXTRA_URI, uri?.toString()) }
        try {
            manager(context, subId).sendMultimediaMessage(context, contentUri, null, null, pi)
        } catch (e: Exception) {
            uri?.let { MmsStore.setBox(context, it, Telephony.Mms.MESSAGE_BOX_FAILED) }
            file.delete()
        }
    }

    /** Lance le téléchargement d'un MMS annoncé par une notification WAP. */
    fun download(context: Context, location: String, transactionId: String?, subId: Int) {
        val file = newFile(context, "recv")
        file.createNewFile()
        val contentUri = FileProvider.getUriForFile(context, authority(context), file)
        val pi = result(context, ACTION_DOWNLOADED, file) {
            putExtra(EXTRA_SUB, subId)
            putExtra(EXTRA_TR, transactionId)
        }
        try {
            manager(context, subId).downloadMultimediaMessage(context, location, contentUri, null, pi)
        } catch (e: Exception) {
            file.delete()
            Notifications.showMmsFailed(context)
        }
    }

    /** Indique à l'opérateur que le MMS a bien été récupéré. */
    private fun acknowledge(context: Context, transactionId: String, subId: Int) {
        val file = newFile(context, "ack")
        file.writeBytes(Pdu.buildNotifyRespInd(transactionId))
        val contentUri = FileProvider.getUriForFile(context, authority(context), file)
        runCatching { manager(context, subId).sendMultimediaMessage(context, contentUri, null, null, result(context, ACTION_ACK, file) {}) }
            .onFailure { file.delete() }
    }

    class MmsResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val file = intent.getStringExtra(EXTRA_FILE)?.let(::File)
            val ok = resultCode == Activity.RESULT_OK
            val pending = goAsync()
            Thread {
                try {
                    when (intent.action) {
                        ACTION_SENT -> {
                            intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)?.let { uri ->
                                MmsStore.setBox(context, uri, if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
                            }
                            if (!ok) Notifications.showSendFailed(context)
                        }
                        ACTION_DOWNLOADED -> {
                            val subId = intent.getIntExtra(EXTRA_SUB, -1)
                            val bytes = file?.takeIf { ok && it.exists() }?.readBytes()
                            val pdu = bytes?.let(Pdu::parse)
                            if (pdu != null && pdu.type == Pdu.RETRIEVE_CONF) {
                                val saved = MmsStore.persistIncoming(context, pdu, subId)
                                if (saved != null) {
                                    val (threadId, recipients) = saved
                                    val store = ChattyStore.get(context)
                                    if (store.isArchived(threadId)) store.setArchived(threadId, false)
                                    if (!store.isMuted(threadId)) Notifications.showIncoming(context, threadId, recipients.joinToString(","))
                                }
                                val tr = intent.getStringExtra(EXTRA_TR) ?: pdu.transactionId
                                if (tr != null) acknowledge(context, tr, subId)
                            } else {
                                Notifications.showMmsFailed(context)
                            }
                        }
                    }
                } finally {
                    file?.delete()
                    pending.finish()
                }
            }.start()
        }
    }
}
