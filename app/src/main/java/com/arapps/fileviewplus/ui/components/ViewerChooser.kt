package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode

/**
 * Simple, modular chooser that presents Open-In-App, Open-External and common file actions.
 * FileActionsMenu will show this chooser and pass handlers for each action so logic stays centralized.
 */
@Composable
fun ViewerChooser(
    file: FileNode,
    onDismiss: () -> Unit,
    onOpenInternal: () -> Unit,
    onOpenExternal: () -> Unit,
    onMoveToVault: () -> Unit,
    onZip: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = file.name) },
        text = {
            Column {
                Text("Open or perform actions on this file:")
            }
        },
        confirmButton = {
            Button(onClick = {
                onOpenInternal(); onDismiss()
            }) { Text("Open (In-app)") }
        },
        dismissButton = {
            Row {
                Button(onClick = { onOpenExternal(); onDismiss() }) { Text("Open with external app") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onMoveToVault(); onDismiss() }) { Text("Move to Vault") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onZip(); onDismiss() }) { Text("Zip") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onDelete(); onDismiss() }) { Text("Delete") }
            }
        }
    )
}
