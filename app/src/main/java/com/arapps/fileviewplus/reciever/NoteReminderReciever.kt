// File: com/arapps/fileviewplus/receiver/NoteReminderReceiver.kt
package com.arapps.fileviewplus.receiver

import RepeatType
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arapps.fileviewplus.MainActivity
import com.arapps.fileviewplus.R
import com.arapps.fileviewplus.utils.ReminderScheduler
import kotlin.math.abs

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent?.getStringExtra("note_id") ?: return
        val noteContent = intent.getStringExtra("note_content") ?: return
        val repeatStr = intent.getStringExtra("repeat") ?: RepeatType.NEVER.name

        Log.d("NoteReminderReceiver", "Received alarm for noteId=$noteId content=$noteContent repeat=$repeatStr")

        // Post notification directly from the BroadcastReceiver (avoids starting a foreground service)
        val channelId = "note_reminder_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Note Reminders", NotificationManager.IMPORTANCE_HIGH)
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vault_note:$noteId")
        }

        val requestCode = abs(noteId.hashCode())
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, requestCode, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder_mdpi)
            .setContentTitle("Vault Reminder")
            .setContentText(noteContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr?.notify(requestCode, notification)
        Log.d("NoteReminderReceiver", "Posted notification for noteId=$noteId from BroadcastReceiver (requestCode=$requestCode)")

        // Reschedule if repeating
        val repeat = try {
            RepeatType.valueOf(repeatStr)
        } catch (e: Exception) {
            RepeatType.NEVER
        }

        if (repeat != RepeatType.NEVER) {
            val interval = when (repeat) {
                RepeatType.DAILY -> 24 * 60 * 60 * 1000L
                RepeatType.WEEKLY -> 7 * 24 * 60 * 60 * 1000L
                else -> 0L
            }

            if (interval > 0L) {
                val nextTrigger = System.currentTimeMillis() + interval
                ReminderScheduler.scheduleReminder(
                    context = context,
                    noteId = noteId,
                    noteContent = noteContent,
                    triggerAtMillis = nextTrigger,
                    repeat = repeat
                )
                Log.d("NoteReminderReceiver", "Rescheduled repeating reminder for noteId=$noteId next=$nextTrigger")
            }
        }
    }
}
