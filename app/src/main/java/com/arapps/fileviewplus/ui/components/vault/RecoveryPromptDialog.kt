package com.arapps.fileviewplus.ui.components.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun RecoveryPromptDialog(
    hint: String,
    onAnswerEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var answer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover PIN") },
        text = {
            Column {
                Text("Hint: $hint")
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Your Answer") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswerEntered(answer) }) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
