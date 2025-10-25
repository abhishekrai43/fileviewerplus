package com.arapps.fileviewplus.utils

import RepeatType
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.arapps.fileviewplus.MainActivity
import com.arapps.fileviewplus.receiver.NoteReminderReceiver
import com.arapps.fileviewplus.worker.NoteReminderWorker
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(
        context: Context,
        noteId: String,
        noteContent: String,
        triggerAtMillis: Long,
        repeat: RepeatType
    ) {
        val now = System.currentTimeMillis()
        val delay = triggerAtMillis - now

        Log.d(TAG, "Scheduling: noteId=$noteId at=$triggerAtMillis (delay=$delay ms), repeat=$repeat")
        Log.d(TAG, "Current time: $now, Target time: $triggerAtMillis")

        if (delay <= 0) {
            Log.w(TAG, "Reminder time already passed for noteId=$noteId. Skipping.")
            return
        }

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, NoteReminderReceiver::class.java).apply {
                putExtra("note_id", noteId)
                putExtra("note_content", noteContent)
                putExtra("repeat", repeat.name)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val requestCode = abs(noteId.hashCode())

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags
            )

            Log.d(TAG, "Created PendingIntent for noteId=$noteId requestCode=$requestCode")

            // Use the most precise alarm method available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check if we can schedule exact alarms
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d(TAG, "Scheduled setExact() alarm for noteId=$noteId at $triggerAtMillis")
                } else {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted!")
                    throw SecurityException("Exact alarm permission not granted")
                }
            } else {
                // Pre-Android S: exact alarms allowed by default
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(TAG, "Scheduled setExact() alarm (pre-S) for noteId=$noteId at $triggerAtMillis")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for noteId=$noteId", e)
            throw e
        }
    }

    fun cancelReminder(context: Context, noteId: String) {
        Log.d(TAG, "Canceling reminder for noteId=$noteId")

        // Cancel AlarmManager pending intent
        try {
            val intent = Intent(context, NoteReminderReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val requestCode = abs(noteId.hashCode())
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Canceled AlarmManager pending intent for noteId=$noteId requestCode=$requestCode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel AlarmManager pending intent for noteId=$noteId", e)
        }

        // Cancel WorkManager fallback
        WorkManager.getInstance(context).cancelUniqueWork("note_reminder_$noteId")
        Log.d(TAG, "Canceled WorkManager work for noteId=$noteId")
    }
}
