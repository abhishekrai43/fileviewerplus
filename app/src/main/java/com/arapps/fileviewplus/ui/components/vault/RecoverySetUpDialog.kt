import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecoverySetupDialog(
    onSet: (hint: String, answer: String) -> Unit,
    onCancel: () -> Unit
) {
    var hint by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

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
            TextButton(onClick = {
                if (hint.isNotBlank() && answer.isNotBlank()) onSet(hint, answer)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
