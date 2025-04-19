package com.arapps.fileviewplus.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.arapps.fileviewplus.data.NoteStore
import com.arapps.fileviewplus.utils.ReminderScheduler

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Exact alarm permission granted.", Toast.LENGTH_SHORT).show()
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
}
