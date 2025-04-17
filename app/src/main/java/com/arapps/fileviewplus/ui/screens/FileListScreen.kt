package com.arapps.fileviewplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FileActionsMenu
import com.arapps.fileviewplus.ui.components.FilePreview
import com.arapps.fileviewplus.ui.components.GrantFullAccessCard
import com.arapps.fileviewplus.utils.SafUtils
import com.arapps.fileviewplus.viewer.ViewerRouter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(day: FileNode.Day, onBack: () -> Unit) {
    val context = LocalContext.current
    var requestAccessFor by remember { mutableStateOf<String?>(null) }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            Toast.makeText(context, "Access granted", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(requestAccessFor) {
        requestAccessFor?.let {
            safLauncher.launch(Uri.parse(it))
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
                val isProtected = remember(file.path) { SafUtils.isSafProtected(file) }

                if (isProtected) {
                    GrantFullAccessCard(
                        folderPath = file.path.substringBeforeLast('/'),
                        onGrantClick = {
                            requestAccessFor = file.path
                        }
                    )
                }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isProtected) {
                            ViewerRouter.openFile(context, file, fromVault = false)
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
