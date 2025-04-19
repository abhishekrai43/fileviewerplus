// File: com/arapps/fileviewplus/service/ReminderForegroundService.kt
package com.arapps.fileviewplus.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arapps.fileviewplus.R
import com.arapps.fileviewplus.MainActivity

class ReminderForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val noteId = intent?.getStringExtra("note_id") ?: return START_NOT_STICKY
        val noteContent = intent.getStringExtra("note_content") ?: "Reminder"

        val channelId = "reminder_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            chan.description = "Shows note reminders"
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Note Reminder")
            .setContentText(noteContent)
            .setSmallIcon(R.drawable.ic_notification_reminder_mdpi)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        startForeground(noteId.hashCode(), notification)

        // Stop after short delay
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
