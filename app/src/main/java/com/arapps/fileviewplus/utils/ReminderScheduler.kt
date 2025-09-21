package com.arapps.fileviewplus.utils

import RepeatType
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.arapps.fileviewplus.receiver.NoteReminderReceiver
import com.arapps.fileviewplus.worker.NoteReminderWorker
import java.util.concurrent.TimeUnit

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

        Log.d("ReminderScheduler", "Scheduling: $noteId at $triggerAtMillis (delay $delay ms), repeat=$repeat")

        if (delay <= 0) {
            Log.w("ReminderScheduler", "Reminder time already passed. Skipping.")
            return
        }

        // Prefer AlarmManager exact alarm (works even when app is closed/dozed).
        // Fall back to WorkManager when exact alarms aren't allowed.
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, NoteReminderReceiver::class.java).apply {
                putExtra("note_id", noteId)
                putExtra("note_content", noteContent)
                putExtra("repeat", repeat.name)
            }

            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                noteId.hashCode(),
                intent,
                flags
            )

            // Use exact alarm API where possible
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // If the app is allowed to schedule exact alarms, use AlarmManager; otherwise fall back to WorkManager
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d(TAG, "Scheduled exact AlarmManager alarm for noteId=$noteId at $triggerAtMillis")
                    return
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(TAG, "Scheduled exact AlarmManager alarm for noteId=$noteId at $triggerAtMillis")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "AlarmManager scheduling failed, falling back to WorkManager", e)
        }

        // Fallback: schedule with WorkManager (less exact, may be deferred by OS)
        val data = Data.Builder()
            .putString("note_id", noteId)
            .putString("note_content", noteContent)
            .putString("repeat", repeat.name)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NoteReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("note_reminder_$noteId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "note_reminder_$noteId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled WorkManager task for noteId=$noteId after $delay ms (fallback)")
    }

    fun cancelReminder(context: Context, noteId: String) {
        Log.d(TAG, "Canceling reminder for noteId=$noteId")

        // Cancel AlarmManager pending intent
        try {
            val intent = Intent(context, NoteReminderReceiver::class.java)
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, noteId.hashCode(), intent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel AlarmManager pending intent", e)
        }

        // Cancel WorkManager fallback
        WorkManager.getInstance(context).cancelUniqueWork("note_reminder_$noteId")
    }
}
