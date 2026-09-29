package com.furaxdev.chatty.sms

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.furaxdev.chatty.R
import java.util.concurrent.TimeUnit

/** « Me le rappeler » : notification à l'heure choisie pour revenir sur un message. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val threadId = inputData.getLong(KEY_THREAD, -1)
        val address = inputData.getString(KEY_ADDRESS) ?: return Result.failure()
        val who = inputData.getString(KEY_WHO).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val n = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Rappel · $who")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(Notifications.openIntent(applicationContext, threadId, address))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(("reminder$threadId$body").hashCode(), n)
        } catch (_: SecurityException) {
        }
        return Result.success()
    }

    companion object {
        private const val KEY_THREAD = "thread"
        private const val KEY_ADDRESS = "address"
        private const val KEY_WHO = "who"
        private const val KEY_BODY = "body"

        fun schedule(context: Context, threadId: Long, address: String, who: String, body: String, at: Long) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_THREAD to threadId, KEY_ADDRESS to address, KEY_WHO to who, KEY_BODY to body))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
