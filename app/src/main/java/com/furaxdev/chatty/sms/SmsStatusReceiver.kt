package com.furaxdev.chatty.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.furaxdev.chatty.data.SmsRepository

/** Met à jour le message selon le résultat d'envoi / l'accusé de réception. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(SmsSender.EXTRA_URI)?.let(Uri::parse) ?: return
        val isLastPart = intent.getIntExtra(SmsSender.EXTRA_PART, 0) == intent.getIntExtra(SmsSender.EXTRA_PARTS, 1) - 1
        val repo = SmsRepository(context)
        when (intent.action) {
            SmsSender.ACTION_SENT -> {
                if (resultCode != Activity.RESULT_OK) {
                    repo.updateType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    Notifications.showSendFailed(context)
                } else if (isLastPart) {
                    repo.updateType(uri, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
            }
            SmsSender.ACTION_DELIVERED -> if (isLastPart) {
                repo.updateStatus(uri, Telephony.Sms.STATUS_COMPLETE)
            }
        }
    }
}
