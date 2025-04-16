package com.arapps.fileviewplus.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
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
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.GrantFullAccessCard
import java.io.File

enum class FileSuggestionCategory(val label: String) {
    OLD("\uD83D\uDCC5 Old Files"),
    LARGE("\uD83D\uDC00 Large Files")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredFileListScreen(
    files: List<FileNode>,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FileSuggestionCategory.OLD) }
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

    BackHandler { onBack() }

    val oldFiles = files.filter {
        System.currentTimeMillis() - it.lastModified > 6 * 30 * 24 * 60 * 60 * 1000L
    }
    val largeFiles = files.filter {
        it.size > 200 * 1024 * 1024
    }

    val currentFiles = when (selectedTab) {
        FileSuggestionCategory.OLD -> oldFiles
        FileSuggestionCategory.LARGE -> largeFiles
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Cleanup", style = MaterialTheme.typography.titleLarge) },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SuggestionChip(
                    category = FileSuggestionCategory.OLD,
                    isSelected = selectedTab == FileSuggestionCategory.OLD,
                    count = oldFiles.size,
                    onClick = { selectedTab = FileSuggestionCategory.OLD }
                )
                SuggestionChip(
                    category = FileSuggestionCategory.LARGE,
                    isSelected = selectedTab == FileSuggestionCategory.LARGE,
                    count = largeFiles.size,
                    onClick = { selectedTab = FileSuggestionCategory.LARGE }
                )
            }

            HorizontalDivider()

            if (currentFiles.isEmpty()) {
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
                    items(items = currentFiles) { file ->
                        val isProtected = SafUtils.isSafProtected(File(file.path))

                        if (isProtected) {
                            GrantFullAccessCard(
                                folderPath = File(file.path).parent ?: "Unknown Folder",
                                onGrantClick = {
                                    requestAccessFor = File(file.path).parentFile
                                }
                            )
                        }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProtected) {
                                    openFileSafely(File(file.path), context)
                                },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${file.size / 1024} KB",
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

@Composable
private fun SuggestionChip(
    category: FileSuggestionCategory,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text("${category.label} ($count)")
        }
    )
}

