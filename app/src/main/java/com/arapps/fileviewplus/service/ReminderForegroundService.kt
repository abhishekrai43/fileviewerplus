// File: com/arapps/fileviewplus/service/ReminderForegroundService.kt
package com.arapps.fileviewplus.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arapps.fileviewplus.R
import com.arapps.fileviewplus.MainActivity

class ReminderForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val noteId = intent?.getStringExtra("note_id") ?: return START_NOT_STICKY
        val noteContent = intent.getStringExtra("note_content") ?: "Reminder"

        Log.d("ReminderForegroundService", "onStartCommand noteId=$noteId content=$noteContent")

        // Use the same channel id as the worker to keep behavior consistent
        val channelId = "note_reminder_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Note Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            chan.description = "Shows note reminders"
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)

            // Log whether notifications are enabled for this app
            val areEnabled = mgr.areNotificationsEnabled()
            Log.d("ReminderForegroundService", "Notifications enabled: $areEnabled")
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("navigate_to", "vault_note:$noteId")
        }
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

        val notificationId = noteId.hashCode()

        // IMPORTANT: If startForegroundService() was called, the service must call startForeground() quickly
        // to avoid a RemoteServiceException. Call startForeground immediately with the notification.
        Log.d("ReminderForegroundService", "Calling startForeground for noteId=$noteId")
        startForeground(notificationId, notification)
        Log.d("ReminderForegroundService", "Foreground notification posted for noteId=$noteId")

        // Also post via NotificationManager so the notification remains if we stop the service.
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(notificationId, notification)
        Log.d("ReminderForegroundService", "NotificationManager.notify called for noteId=$noteId")

        // Detach the foreground state but keep the notification posted. This prevents the system
        // from thinking the service is still foreground when we stop the service.
        try {
            stopForeground(false) // do not remove the notification
            Log.d("ReminderForegroundService", "stopForeground(false) called for noteId=$noteId")
        } catch (e: Exception) {
            Log.w("ReminderForegroundService", "stopForeground failed", e)
        }

        // Stop the service; notification will remain
        stopSelf()
        Log.d("ReminderForegroundService", "stopSelf() called for noteId=$noteId")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
