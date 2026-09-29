package com.furaxdev.chatty.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Obligatoire pour obtenir le rôle d'appli SMS par défaut.
 * Chatty se concentre sur les SMS : les MMS ne sont pas encore téléchargés.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER") return
    }
}
