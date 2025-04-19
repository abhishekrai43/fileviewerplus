// File: com.arapps.fileviewplus.ui.components.vault.SetupPinDialog.kt

package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.utils.storePinRecovery

@Composable
fun SetupPinDialog(
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    val pin = remember { mutableStateOf("") }
    val hint = remember { mutableStateOf("") }
    val answer = remember { mutableStateOf("") }

    val isValid = pin.value.length == 4 && hint.value.isNotBlank() && answer.value.isNotBlank()

    VaultTextFieldDialog(
        title = "Set Vault PIN",
        fields = listOf(
            "4-digit PIN" to pin,
            "Recovery Hint" to hint,
            "Your Answer" to answer
        ),
        confirmEnabled = isValid,
        onConfirm = {
            storePinRecovery(context, pin.value, hint.value, answer.value)
            Toast.makeText(context, "PIN and recovery saved", Toast.LENGTH_SHORT).show()
            onPinSet(pin.value)  // 👈 closes dialog
        },
        onCancel = onCancel
    )
}
