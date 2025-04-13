package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.runtime.*

import androidx.compose.ui.platform.LocalContext

import com.arapps.fileviewplus.utils.storePinRecovery

@Composable
fun SetupPinDialog(
    onPinSet: (String) -> Unit, // ✅ Now receives the actual PIN value
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
            onPinSet(pin.value) // ✅ Pass the PIN to parent
            Toast.makeText(context, "PIN and recovery saved", Toast.LENGTH_SHORT).show()
        },
        onCancel = onCancel
    )
}
