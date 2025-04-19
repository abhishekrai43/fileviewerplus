// File: com/arapps/fileviewplus/receiver/NoteReminderReceiver.kt
package com.arapps.fileviewplus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arapps.fileviewplus.service.ReminderForegroundService

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("note_id") ?: return
        val noteContent = intent.getStringExtra("note_content") ?: return

        val serviceIntent = Intent(context, ReminderForegroundService::class.java).apply {
            putExtra("note_id", noteId)
            putExtra("note_content", noteContent)
        }

        context.startForegroundService(serviceIntent)
    }
}
