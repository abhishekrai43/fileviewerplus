package com.arapps.fileviewplus.ui.components.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.dp

@Composable
fun VaultTextFieldDialog(
    title: String,
    fields: List<Pair<String, MutableState<String>>>,
    confirmButtonText: String = "Save",
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, state) in fields) {
                    OutlinedTextField(
                        value = state.value,
                        onValueChange = { state.value = it },
                        label = { Text(label) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
