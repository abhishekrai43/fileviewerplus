package com.arapps.fileviewplus.ui.components.vault

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun ShowRenameDialog(
    context: Context,
    file: File,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(file.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${file.name}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val newFile = File(file.parentFile, name)
                if (file.renameTo(newFile)) {
                    onSuccess()
                } else {
                    Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

