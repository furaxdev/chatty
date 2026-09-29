package com.furaxdev.chatty.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.furaxdev.chatty.data.SmsRepository

/** Actions depuis la notification : réponse rapide et « Marquer comme lu ». */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD_ID, -1)
        val address = intent.getStringExtra(Notifications.EXTRA_ADDRESS) ?: return
        val pending = goAsync()
        Thread {
            try {
                val repo = SmsRepository(context)
                when (intent.action) {
                    Notifications.ACTION_REPLY -> {
                        val reply = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(Notifications.KEY_REPLY)?.toString()
                        if (!reply.isNullOrBlank()) {
                            SmsSender.send(context, address, reply, threadIdHint = threadId)
                            repo.markThreadRead(threadId)
                        }
                    }
                    Notifications.ACTION_MARK_READ -> repo.markThreadRead(threadId)
                }
                Notifications.cancel(context, threadId)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
