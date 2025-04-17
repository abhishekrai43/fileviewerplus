// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FilteredFileListScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arapps.fileflowplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.ui.components.GrantFullAccessCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredFileListScreen(
    files: List<File>,
    title: String,
    onBack: () -> Unit,
    onOpenViewer: (File) -> Unit
) {
    val context = LocalContext.current
    var requestAccessFor by remember { mutableStateOf<File?>(null) }
    val shownAccessCards = remember { mutableSetOf<String>() }

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

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No files found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = files) { file ->
                        val isProtected = !file.canRead()
                        val parentPath = file.parent ?: "Unknown"

                        if (isProtected && shownAccessCards.add(parentPath)) {
                            GrantFullAccessCard(
                                folderPath = parentPath,
                                onGrantClick = { requestAccessFor = file.parentFile }
                            )
                        }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProtected) {
                                    onOpenViewer(file)
                                },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilePreviewThumbnail(file)

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            file.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${file.length() / 1024} KB",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
