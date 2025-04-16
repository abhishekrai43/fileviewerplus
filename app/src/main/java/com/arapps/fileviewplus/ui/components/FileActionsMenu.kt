package com.arapps.fileviewplus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileActionsMenu(
    file: FileNode?,
    modifier: Modifier = Modifier
) {
    if (file == null) return

    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val realFile = remember(file.path) { File(file.path) }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, it)
            val deleted = doc?.delete() ?: false
            val msg = if (deleted) "File deleted successfully" else "Delete failed or file not accessible"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "File options")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    expanded = false
                    openFile(context, realFile)
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    shareFile(context, realFile)
                }
            )
            DropdownMenuItem(
                text = { Text("Zip & Share") },
                onClick = {
                    expanded = false
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val zipFile = ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(realFile))
                            zipFile?.let {
                                ZipUtils.shareZip(context, it)
                            } ?: Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Zipping failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

private fun openFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = context.contentResolver.getType(uri) ?: "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
    }
}