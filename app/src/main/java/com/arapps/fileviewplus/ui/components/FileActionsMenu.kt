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
import com.arapps.fileviewplus.utils.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileActionsMenu(
    file: File?,
    modifier: Modifier = Modifier
) {
    if (file == null) return

    var expanded by remember { mutableStateOf(false) }
    var requestFileAccess by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, it)
            if (doc?.delete() == true) {
                Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Delete failed or file not accessible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(requestFileAccess) {
        if (requestFileAccess) {
            filePickerLauncher.launch(arrayOf("*/*"))
            requestFileAccess = false
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
                    openFile(context, file)
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    shareFile(context, file)
                }
            )
            DropdownMenuItem(
                text = { Text("Zip & Share") },
                onClick = {
                    expanded = false
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val zipFile = ZipUtils.createZip(context, file.nameWithoutExtension, listOf(file))
                            if (zipFile != null) {
                                ZipUtils.shareZip(context, zipFile)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )

        }
    }
}

private fun openFile(context: Context, file: File) {
    val uri = getUriForFile(context, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

private fun shareFile(context: Context, file: File) {
    val uri = getUriForFile(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = context.contentResolver.getType(uri) ?: "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun getUriForFile(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
