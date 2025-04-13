package com.arapps.fileviewplus.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FolderActionsMenu(
    folderName: String,
    files: List<File>,
    modifier: Modifier = Modifier,
    onDeleted: (() -> Unit)? = null // Optional callback
) {
    var expanded by remember { mutableStateOf(false) }
    var zipping by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Zipping Progress Dialog
    if (zipping) {
        AlertDialog(
            onDismissRequest = {}, // Prevent dismiss
            confirmButton = {},
            text = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Zipping folder...")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Folder") },
            text = { Text("Are you sure you want to delete the folder '$folderName'?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    coroutineScope.launch(Dispatchers.IO) {
                        val folder = files.firstOrNull()?.parentFile
                        val deleted = folder?.deleteRecursively() == true
                        withContext(Dispatchers.Main) {
                            if (deleted) {
                                Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                                onDeleted?.invoke()
                            } else {
                                Toast.makeText(context, "Failed to delete folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Zip & Share") },
                onClick = {
                    expanded = false
                    zipping = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val zipFile = ZipUtils.createZip(context, folderName, files)
                            withContext(Dispatchers.Main) {
                                if (zipFile != null) {
                                    ZipUtils.shareZip(context, zipFile)
                                } else {
                                    Toast.makeText(context, "Failed to create ZIP file.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Zipping failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                zipping = false
                            }
                        }
                    }
                }
            )

        }
    }
}
