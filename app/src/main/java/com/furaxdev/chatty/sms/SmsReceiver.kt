package com.furaxdev.chatty.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.furaxdev.chatty.data.ChattyStore
import com.furaxdev.chatty.data.SmsRepository

/** Reçoit les SMS entrants (livrés uniquement à l'appli SMS par défaut). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra("subscription", -1)
        val pending = goAsync()
        Thread {
            try {
                val repo = SmsRepository(context)
                val store = ChattyStore.get(context)
                // Un SMS long arrive en plusieurs morceaux : on les regroupe par expéditeur.
                parts.groupBy { it.displayOriginatingAddress ?: it.originatingAddress.orEmpty() }
                    .forEach { (address, msgs) ->
                        val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
                        repo.insertInbox(address, body, msgs.first().timestampMillis, subId)
                        val threadId = repo.threadIdFor(address)
                        if (store.isArchived(threadId)) store.setArchived(threadId, false)
                        if (!store.isMuted(threadId)) Notifications.showIncoming(context, threadId, address)
                    }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
