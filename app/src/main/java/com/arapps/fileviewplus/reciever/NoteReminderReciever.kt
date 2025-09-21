// File: com/arapps/fileviewplus/receiver/NoteReminderReceiver.kt
package com.arapps.fileviewplus.receiver

import RepeatType
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.service.ReminderForegroundService
import com.arapps.fileviewplus.utils.ReminderScheduler

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("note_id") ?: return
        val noteContent = intent.getStringExtra("note_content") ?: return
        val repeatStr = intent.getStringExtra("repeat") ?: RepeatType.NEVER.name

        val serviceIntent = Intent(context, ReminderForegroundService::class.java).apply {
            putExtra("note_id", noteId)
            putExtra("note_content", noteContent)
        }

        // Use ContextCompat to start foreground service in a backwards-compatible way
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Fallback: if service can't be started, you may want to handle notification directly here
            e.printStackTrace()
        }

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
            }
        }
    }
}
