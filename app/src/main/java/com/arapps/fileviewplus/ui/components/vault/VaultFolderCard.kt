package com.arapps.fileviewplus.ui.components.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun VaultFolderCard(
    folder: File,
    onOpen: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onZipAndShare: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${folder.listFiles()?.size ?: 0} items",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            onRenameRequest()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteConfirmed()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Zip & Share") },
                        onClick = {
                            menuExpanded = false
                            onZipAndShare()
                        }
                    )
                }
            }
        }
    }
}
