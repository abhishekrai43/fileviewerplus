package com.arapps.fileviewplus.utils

import RepeatType
import android.content.Context
import android.util.Log
import androidx.work.*
import com.arapps.fileviewplus.worker.NoteReminderWorker
import java.util.concurrent.TimeUnit
import java.util.Date

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


        Log.d(TAG, "Scheduled WorkManager task for noteId=$noteId after $delay ms")
    }

    fun cancelReminder(context: Context, noteId: String) {
        Log.d(TAG, "Canceling reminder for noteId=$noteId")
        WorkManager.getInstance(context).cancelUniqueWork("note_reminder_$noteId")
    }
}
