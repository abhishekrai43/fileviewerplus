// File: com.arapps.fileviewplus.ui.components.vault.RecoverPinDialog.kt

package com.arapps.fileviewplus.ui.components.vault

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.utils.RecoveryData
import com.arapps.fileviewplus.utils.getStoredRecovery

@Composable
fun RecoverPinDialog(
    onRecovered: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val recoveryState = remember { mutableStateOf<RecoveryData?>(null) }
    val recovery = recoveryState.value

    val userAnswer = remember { mutableStateOf("") }

    // 🔁 Fetch once
    LaunchedEffect(Unit) {
        val stored = getStoredRecovery(context)
        if (stored == null) {
            Toast.makeText(context, "No recovery info found", Toast.LENGTH_SHORT).show()
            onDismiss()
        } else {
            recoveryState.value = stored
        }
    }

    // ❌ Don't render until data is ready
    if (recovery == null) return

    VaultTextFieldDialog(
        title = "Recover PIN",
        fields = listOf(recovery.hint to userAnswer),
        confirmButtonText = "Submit",
        confirmEnabled = userAnswer.value.equals(recovery.answer, ignoreCase = true),
        onConfirm = {
            Toast.makeText(context, "Correct! Your PIN is ${recovery.pin}", Toast.LENGTH_LONG).show()
            onRecovered()
        },
        onCancel = onDismiss
    )
}
