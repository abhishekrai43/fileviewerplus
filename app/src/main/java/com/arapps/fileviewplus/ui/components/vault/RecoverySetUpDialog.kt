// File: com/arapps/fileviewplus/ui/components/vault/RecoverySetupDialog.kt

package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.utils.storeRecoveryInfo

@Composable
fun RecoverySetupDialog(
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var hint by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    val isValid = hint.isNotBlank() && answer.isNotBlank()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Set Recovery Question") },
        text = {
            Column {
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("Security Hint (e.g. Favorite food)") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        storeRecoveryInfo(context, hint, answer)
                        Toast.makeText(context, "Recovery info saved", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
