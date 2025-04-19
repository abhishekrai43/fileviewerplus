package com.arapps.fileviewplus.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arapps.fileviewplus.MainActivity
import com.arapps.fileviewplus.R

class NoteReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val noteId = inputData.getString("note_id") ?: return Result.failure()
        val noteContent = inputData.getString("note_content") ?: "Reminder"

        val channelId = "note_reminder_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Note Reminders"
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        // ✅ Intent to open MainActivity (or deep link to vault screen if desired)
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vault") // optional
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(), // unique per note
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder_mdpi)
            .setContentTitle("Vault Reminder")
            .setContentText(noteContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(noteId.hashCode(), notification)

        return Result.success()
    }
}
