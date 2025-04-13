package com.arapps.fileviewplus.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FileActionsMenu
import com.arapps.fileviewplus.ui.components.FilePreview
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(day: FileNode.Day, onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\uD83D\uDCC4 ${day.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("\u2B05\uFE0F")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(day.files) { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { openFile(file, context) }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilePreview(file)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(file.name, modifier = Modifier.weight(1f))
                        FileActionsMenu(file)
                    }
                }
            }
        }
    }
}

fun openFile(file: File, context: Context) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val mimeType = context.contentResolver.getType(uri)

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}
