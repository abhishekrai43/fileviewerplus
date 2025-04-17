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
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File

@Composable
fun VaultFileCard(file: File, onFileChanged: () -> Unit) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val fileNode = file.toFileNode()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .clickable { ViewerRouter.openFile(context, fileNode, fromVault = true) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(file.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }

            VaultFileOptionsDropdown(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onRename = {
                    val renamed = File(file.parent, "${file.name}_renamed")
                    if (file.renameTo(renamed)) {
                        Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                        onFileChanged()
                    }
                },
                onDelete = {
                    if (file.deleteRecursively()) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        onFileChanged()
                    }
                },
                onZipShare = {
                    val zipFile = ZipUtils.createZip(context, file.name, listOf(fileNode))
                    if (zipFile != null) ZipUtils.shareZip(context, zipFile)
                    else Toast.makeText(context, "Zip failed", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

fun File.toFileNode(): FileNode {
    return FileNode(
        name = name,
        path = absolutePath,
        type = FileNode.FileType.OTHER, // or classify based on extension
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
