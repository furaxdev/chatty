package com.furaxdev.chatty.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.furaxdev.chatty.MainActivity
import com.furaxdev.chatty.R
import com.furaxdev.chatty.data.ChattyStore
import com.furaxdev.chatty.data.SmsRepository

object Notifications {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_ALERTS = "alerts"
    const val ACTION_REPLY = "com.furaxdev.chatty.REPLY"
    const val ACTION_MARK_READ = "com.furaxdev.chatty.MARK_READ"
    const val EXTRA_THREAD_ID = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val KEY_REPLY = "reply"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nouveaux SMS reçus"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertes d'envoi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Échecs d'envoi"
            }
        )
    }

    // Avant Android 13, aucune permission n'est nécessaire pour les notifications.
    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openIntent(context: Context, threadId: Long, address: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_ADDRESS, address)
        }
        return PendingIntent.getActivity(
            context, threadId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, action: String, threadId: Long, address: String, mutable: Boolean): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_ADDRESS, address)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + threadId).hashCode(), intent, flags)
    }

    fun showIncoming(context: Context, threadId: Long, address: String) {
        if (!canPost(context)) return
        val repo = SmsRepository(context)
        val contact = repo.contact(address)
        val unread = repo.messages(threadId, ChattyStore.get(context))
            .filter { !it.isMine && !it.read }
            .takeLast(6)
        if (unread.isEmpty()) {
            // Réaction reçue : elle est rattachée à un message, on l'annonce simplement.
            val latest = repo.latestIncomingBody(threadId) ?: return
            val r = com.furaxdev.chatty.data.MessageFormat.parseReaction(latest) ?: return
            if (r.removed) return
            val n = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(contact.displayName)
                .setContentText("A réagi ${r.emoji} à « ${r.target} »")
                .setContentIntent(openIntent(context, threadId, address))
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(context).notify(threadId.toInt(), n) } catch (_: SecurityException) {}
            return
        }

        val me = Person.Builder().setName("Moi").build()
        val sender = Person.Builder().setName(contact.displayName).setKey(address).build()
        val style = NotificationCompat.MessagingStyle(me)
        unread.forEach { msg ->
            val text = msg.effect?.let { "${msg.body}  ${it.emoji}" } ?: msg.body
            if (msg.quote != null) style.addMessage("↪ ${msg.quote}", msg.date, sender)
            style.addMessage(text, msg.date, sender)
        }

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "Répondre",
            actionIntent(context, ACTION_REPLY, threadId, address, mutable = true),
        )
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(true)
            .build()
        val readAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "Marquer comme lu",
            actionIntent(context, ACTION_MARK_READ, threadId, address, mutable = false),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D5AFE.toInt())
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openIntent(context, threadId, address))
            .setAutoCancel(true)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun showSendFailed(context: Context) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Échec de l'envoi")
            .setContentText("Un message n'a pas pu être envoyé. Touchez-le dans Chatty pour réessayer.")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(-1, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }
}
