package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun ForgotPinDialog(
    onRecover: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot PIN?") },
        text = {
            Text("You can recover your PIN using your secret question.")
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRecover()
            }) {
                Text("Recover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
