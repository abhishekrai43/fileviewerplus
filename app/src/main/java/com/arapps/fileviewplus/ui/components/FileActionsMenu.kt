package com.arapps.fileviewplus.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.viewer.ViewerRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileActionsMenu(
    file: FileNode?,
    modifier: Modifier = Modifier
) {
    if (file == null) return

    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "File options")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    expanded = false
                    ViewerRouter.openFile(context, file, fromVault = false)
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val success = ZipUtils.shareSingleFile(context, file)
                            if (!success) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Zip & Share") },
                onClick = {
                    expanded = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val zipFile = ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(file))
                            withContext(Dispatchers.Main) {
                                if (zipFile != null) {
                                    ZipUtils.shareZip(context, zipFile)
                                } else {
                                    Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
    }
}