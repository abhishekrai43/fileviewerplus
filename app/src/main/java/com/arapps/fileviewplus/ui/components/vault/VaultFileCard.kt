package com.arapps.fileviewplus.ui.components.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.screens.FileCategory
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File

@Composable
fun VaultFileCard(file: File, onFileChanged: () -> Unit) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                ViewerRouter.openFile(context, file.toFileNode(), fromVault = true)
            }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    StorageStats.formatSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
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
                        onClick = { /* trigger rename flow outside */ menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            file.delete()
                            onFileChanged()
                        }
                    )
                }
            }
        }
    }
}


fun File.toFileNode(): FileNode {
    return FileNode(
        name = name,
        path = absolutePath,
        type = FileNode.FileType.fromExtension(extension),
        size = length(),
        lastModified = lastModified()
    )
}


private fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "com.arapps.fileviewplus.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}
