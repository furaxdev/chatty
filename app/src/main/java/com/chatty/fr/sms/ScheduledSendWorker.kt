package com.chatty.fr.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chatty.fr.data.ChattyStore
import com.chatty.fr.data.ScheduledMessage
import com.chatty.fr.effects.MessageEffect
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Envoie un message programmé à l'heure choisie. */
class ScheduledSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val store = ChattyStore.get(applicationContext)
        val msg = store.scheduled().firstOrNull { it.id == id } ?: return Result.success()
        val effect = msg.effectName?.let { name -> MessageEffect.entries.firstOrNull { it.name == name } }
        SmsSender.send(applicationContext, msg.address, msg.body, effect, msg.threadId)
        store.removeScheduled(id)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "id"

        fun schedule(context: Context, address: String, threadId: Long, body: String, effect: MessageEffect?, sendAt: Long) {
            val id = UUID.randomUUID().toString()
            ChattyStore.get(context).addScheduled(ScheduledMessage(id, address, threadId, body, effect?.name, sendAt))
            val delay = (sendAt - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to id))
                .addTag(id)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context, id: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(id)
            ChattyStore.get(context).removeScheduled(id)
        }

        fun sendNow(context: Context, id: String) {
            val msg = ChattyStore.get(context).scheduled().firstOrNull { it.id == id } ?: return
            cancel(context, id)
            val effect = msg.effectName?.let { name -> MessageEffect.entries.firstOrNull { it.name == name } }
            SmsSender.send(context, msg.address, msg.body, effect, msg.threadId)
        }
    }
}
