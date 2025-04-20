// File: com/arapps/fileviewplus/ui/components/vault/VaultBackupPickerDialog.kt

package com.arapps.fileviewplus.ui.components.vault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import java.io.File

enum class VaultBackupMode {
    BACKUP, RESTORE
}

@Composable
fun VaultBackupPickerDialog(
    context: Context,
    vaultItems: List<File>,
    mode: VaultBackupMode,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<File>) -> Unit
) {
    val checkedMap = remember { mutableStateMapOf<File, Boolean>() }
    val titleText = when (mode) {
        VaultBackupMode.BACKUP -> "Back Up Vault Items"
        VaultBackupMode.RESTORE -> "Restore Vault Items"
    }
    val confirmText = when (mode) {
        VaultBackupMode.BACKUP -> "Back Up"
        VaultBackupMode.RESTORE -> "Restore"
    }

    LaunchedEffect(Unit) {
        vaultItems.forEach { checkedMap[it] = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {},
        text = {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                modifier = Modifier.clip(MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        titleText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            vaultItems.forEach { checkedMap[it] = true }
                        }) { Text("Select All") }

                        TextButton(onClick = {
                            vaultItems.forEach { checkedMap[it] = false }
                        }) { Text("Clear All") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .fillMaxWidth()
                    ) {
                        items(vaultItems) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    file.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Checkbox(
                                    checked = checkedMap[file] == true,
                                    onCheckedChange = { checkedMap[file] = it }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val selected = checkedMap.filterValues { it }.keys.toList()
                                onConfirm(selected)
                            }
                        ) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    )
}
