// File: com/arapps/fileviewplus/receiver/BootReceiver.kt
package com.arapps.fileviewplus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arapps.fileviewplus.data.NoteStore
import com.arapps.fileviewplus.utils.ReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            NoteStore.getAllNotes(context).forEach { note ->
                if (note.reminderAt != null) {
                    ReminderScheduler.scheduleReminder(
                        context = context,
                        noteId = note.id,
                        noteContent = note.content,
                        triggerAtMillis = note.reminderAt,
                        repeat = note.repeat
                    )

                }
            }
        }
    }
}
