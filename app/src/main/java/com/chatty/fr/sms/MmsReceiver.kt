package com.chatty.fr.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import com.chatty.fr.mms.MmsTransport
import com.chatty.fr.mms.Pdu

/**
 * Notification WAP d'un nouveau MMS : on lit l'adresse de téléchargement puis on
 * demande au service MMS du système de le récupérer (voir [MmsTransport]).
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER") return
        val data = intent.getByteArrayExtra("data") ?: return
        val pdu = Pdu.parse(data) ?: return
        if (pdu.type != Pdu.NOTIFICATION_IND) return
        val location = pdu.contentLocation ?: return
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", -1),
        )
        MmsTransport.download(context, location, pdu.transactionId, subId)
    }
}
