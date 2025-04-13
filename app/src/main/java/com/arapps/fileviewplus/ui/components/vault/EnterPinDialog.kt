package com.arapps.fileviewplus.ui.components.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun EnterPinDialog(
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    onForgotPin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val isValid = pin.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Vault PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPinEntered(pin) }, enabled = isValid) {
                Text("Unlock")
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onForgotPin) {
                    Text("Forgot PIN?")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
