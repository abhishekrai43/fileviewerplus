package com.arapps.fileviewplus.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FileActionsMenu
import com.arapps.fileviewplus.ui.components.FilePreview
import com.arapps.fileviewplus.ui.components.GrantFullAccessCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(day: FileNode.Day, onBack: () -> Unit) {
    val context = LocalContext.current
    var requestAccessFor by remember { mutableStateOf<File?>(null) }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            Toast.makeText(context, "Access granted", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(requestAccessFor) {
        requestAccessFor?.let {
            safLauncher.launch(Uri.fromFile(it))
            requestAccessFor = null
        }
    }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(day.files) { file ->
                val fileObj = File(file.path)
                val isProtected = SafUtils.isSafProtected(fileObj)

                if (isProtected) {
                    GrantFullAccessCard(
                        folderPath = fileObj.parent ?: "Unknown Folder",
                        onGrantClick = { requestAccessFor = fileObj.parentFile }
                    )
                }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isProtected) {
                            openFileSafely(fileObj, context)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilePreview(file)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "${file.size / 1024} KB",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        FileActionsMenu(file)
                    }
                }
            }
        }
    }
}

fun openFileSafely(file: File, context: Context) {
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
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show()
    }
}