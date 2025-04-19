// File: com/arapps/fileviewplus/ui/components/vault/NoteDialog.kt

package com.arapps.fileviewplus.ui.components.vault

import Note
import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.ui.components.common.DropdownMenuBox
import com.arapps.fileviewplus.utils.ReminderScheduler
import com.arapps.fileviewplus.ui.theme.NotesFont
import java.util.*

@Composable
fun NoteDialog(
    initialNote: Note? = null,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var isPassword by remember { mutableStateOf(initialNote?.isPassword ?: false) }
    var showPassword by remember { mutableStateOf(false) }
    var reminderAt by remember { mutableStateOf(initialNote?.reminderAt) }
    var selectedColor by remember { mutableStateOf(initialNote?.color ?: NoteColor.YELLOW) }
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var repeat by remember { mutableStateOf(initialNote?.repeat ?: RepeatType.NEVER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                if (content.isNotBlank()) {
                    val noteId = initialNote?.id ?: UUID.randomUUID().toString()
                    val updated = initialNote?.copy(
                        content = content,
                        isPassword = isPassword,
                        reminderAt = reminderAt,
                        color = selectedColor
                    ) ?: Note(
                        id = noteId,
                        content = content,
                        isPassword = isPassword,
                        reminderAt = reminderAt,
                        color = selectedColor
                    )

                    reminderAt?.let {
                        ReminderScheduler.scheduleReminder(context, noteId, content, it, repeat)
                    }
                    onSave(updated)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(if (initialNote == null) "New Note" else "Edit Note") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Title Field (optional)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Content Field
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontFamily = NotesFont
                    ),
                    visualTransformation = if (isPassword && !showPassword)
                        PasswordVisualTransformation()
                    else
                        VisualTransformation.None,
                    trailingIcon = {
                        if (isPassword) {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPassword,
                        onCheckedChange = { isPassword = it }
                    )
                    Text("This is a password")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reminder Button and Timestamp
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val notificationGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!notificationGranted) {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            Toast.makeText(context, "Please allow notifications for reminders.", Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = context.getSystemService(AlarmManager::class.java)
                        if (!alarmManager.canScheduleExactAlarms()) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            Toast.makeText(context, "Please allow exact alarm access.", Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                    }

                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val millis = Calendar.getInstance().apply {
                                        set(year, month, day, hour, minute)
                                    }.timeInMillis
                                    reminderAt = millis
                                    ReminderScheduler.scheduleReminder(
                                        context,
                                        initialNote?.id ?: UUID.randomUUID().toString(),
                                        content,
                                        millis,
                                        repeat
                                    )
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(if (reminderAt == null) "Add Reminder" else "Change Reminder")
                }

                if (reminderAt != null) {
                    Text(
                        "Reminder set for: ${Date(reminderAt!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 🔁 Repeat Dropdown
                Text("Repeat Reminder:", style = MaterialTheme.typography.bodySmall)
                DropdownMenuBox(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = "Repeat",
                    options = RepeatType.values().toList()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Pick a color:", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NoteColor.entries.forEach { color ->
                        val colorValue = when (color) {
                            NoteColor.YELLOW -> Color(0xFFF6E122)
                            NoteColor.BLUE -> Color(0xFF90CAF9)
                            NoteColor.GREEN -> Color(0xFFA5D6A7)
                            NoteColor.RED -> Color(0xFFEF9A9A)
                        }

                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { selectedColor = color },
                            shape = CircleShape,
                            color = colorValue,
                            tonalElevation = if (selectedColor == color) 6.dp else 0.dp,
                            shadowElevation = if (selectedColor == color) 3.dp else 0.dp
                        ) {}
                    }
                }
            }

        }
    )
}