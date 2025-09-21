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

            Log.d(TAG, "Created PendingIntent for noteId=$noteId requestCode=$requestCode pendingIntent=$pendingIntent")

            try {
                // Try the exact API first
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(TAG, "Scheduled exact AlarmManager alarm for noteId=$noteId at $triggerAtMillis via setExactAndAllowWhileIdle")
                return
            } catch (se: SecurityException) {
                Log.w(TAG, "setExactAndAllowWhileIdle SecurityException for noteId=$noteId; trying setAndAllowWhileIdle (inexact)", se)
                try {
                    // Try a less-restricted inexact alarm API as a best-effort fallback
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.d(TAG, "Scheduled inexact AlarmManager alarm for noteId=$noteId at $triggerAtMillis via setAndAllowWhileIdle (fallback)")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "setAndAllowWhileIdle failed for noteId=$noteId; will fall back to WorkManager", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AlarmManager.setExactAndAllowWhileIdle failed for noteId=$noteId; will fall back to WorkManager", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AlarmManager scheduling failed for noteId=$noteId, falling back to WorkManager", e)
        }

        // Fallback: schedule with WorkManager (less exact, may be deferred by OS)
        Log.d(TAG, "Scheduling WorkManager fallback for noteId=$noteId in $delay ms")
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
