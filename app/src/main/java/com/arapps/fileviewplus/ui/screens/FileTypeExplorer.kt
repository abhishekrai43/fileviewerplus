// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FileTypeExplorerScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.SafUtils
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.copyFilesToVaultAsync
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.viewer.ViewerRouter
import com.arapps.fileviewplus.ui.components.vault.EnterPinDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun FileTypeExplorerScreen(categories: List<FileNode.Category>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SAF grant launcher
    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                Toast.makeText(context, "Access granted!", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "Failed to persist permission: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    // Flatten categories into a mutable display list
    val displayFiles = remember { mutableStateListOf<FileNode>() }
    LaunchedEffect(categories) {
        displayFiles.clear()
        displayFiles.addAll(categories.flatMap { it.years }.flatMap { it.months }.flatMap { it.days }.flatMap { it.files })
    }

    // Listen for deletions
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                displayFiles.removeAll { it.path == path }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    // UI state
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<FileCategory?>(null) }

    // Multi-select
    val selected = remember { mutableStateListOf<String>() }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showEnterPin by remember { mutableStateOf(false) }
    var showChooseVaultFolder by remember { mutableStateOf(false) }
    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }

    val grouped = remember(displayFiles, searchQuery, selectedType) {
        displayFiles.filter { f ->
            val matchesType = selectedType == null || fileCategoryForName(f.name) == selectedType
            val matchesQuery = f.name.contains(searchQuery, ignoreCase = true)
            matchesType && matchesQuery
        }.groupBy { fileCategoryForName(it.name) }
    }

    Scaffold(topBar = {
        if (showSelectionToolbar && selected.isNotEmpty()) {
            TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selected.clear(); showSelectionToolbar = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Share first selected (keep simple)
                        val uris = selected.mapNotNull { p ->
                            try { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", File(p)) } catch (_: Exception) { null }
                        }
                        if (uris.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uris.first())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share files"))
                        }
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share") }

                    IconButton(onClick = { showEnterPin = true }) { Icon(Icons.Filled.Lock, contentDescription = "Move to Vault") }

                    IconButton(onClick = {
                        scope.launch {
                            selected.toList().forEach { path ->
                                try {
                                    val node = FileNode.fromFile(File(path))
                                    when (val res = DeletionManager.deleteFile(context, node)) {
                                        DeletionManager.DeleteResult.Deleted -> { /* broadcast will clean UI */ }
                                        is DeletionManager.DeleteResult.NeedUserGrant -> withContext(Dispatchers.Main) { Toast.makeText(context, "Cannot delete automatically: permission needed for $path", Toast.LENGTH_LONG).show() }
                                        is DeletionManager.DeleteResult.Failed -> withContext(Dispatchers.Main) { Toast.makeText(context, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show() }
                                    }
                                } catch (_: Exception) {}
                            }
                            selected.clear(); showSelectionToolbar = false
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            )
        }
    }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Filter chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileCategory.entries.forEach { ft ->
                    FilterChip(selected = selectedType == ft, onClick = { selectedType = if (selectedType == ft) null else ft }, label = { Text(ft.label) })
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (grouped.isEmpty()) item { Text("No files", modifier = Modifier.padding(16.dp)) }

                grouped.forEach { (type, files) ->
                    if (files.isNotEmpty()) {
                        item {
                            Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        items(files, key = { it.path }) { file ->
                            val isProtected = remember(file.path) { SafUtils.isSafProtected(file) }
                            val isSelected = selected.contains(file.path)

                            FileRow(
                                file = file,
                                category = type,
                                isProtected = isProtected,
                                isSelected = isSelected,
                                onSelectToggle = {
                                    if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                    showSelectionToolbar = selected.isNotEmpty()
                                },
                                onGrantClick = { grantLauncher.launch(Uri.parse(file.path.substringBeforeLast('/'))) },
                                onOpenClick = {
                                    // If selection mode active, toggle selection instead of opening
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                        showSelectionToolbar = selected.isNotEmpty()
                                        return@FileRow
                                    }
                                    val f = File(file.path)
                                    if (!f.exists()) {
                                        displayFiles.removeAll { it.path == file.path }
                                        Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
                                        return@FileRow
                                    }
                                    ViewerRouter.openFile(context.findActivity() ?: context, file, fromVault = false)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showEnterPin) {
            EnterPinDialog(onPinEntered = { pin ->
                if (pin == getStoredPin(context)) {
                    showEnterPin = false
                    showChooseVaultFolder = true
                } else {
                    Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }, onDismiss = { showEnterPin = false }, onForgotPin = {})
        }

        if (showChooseVaultFolder) {
            var selectedFolder by remember { mutableStateOf("") }
            val folders = vaultRoot.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            if (selectedFolder.isEmpty()) selectedFolder = folders.firstOrNull() ?: ""

            AlertDialog(onDismissRequest = { showChooseVaultFolder = false }, confirmButton = {
                TextButton(onClick = {
                    val dest = if (selectedFolder.isBlank()) vaultRoot else File(vaultRoot, selectedFolder)
                    scope.launch {
                        val copied = copyFilesToVaultAsync(context, selected.toList(), dest)
                        withContext(Dispatchers.Main) { Toast.makeText(context, "${copied.size} file(s) copied to vault", Toast.LENGTH_SHORT).show() }
                        // attempt delete originals
                        selected.toList().forEach { p -> try { DeletionManager.deleteFile(context, FileNode.fromFile(File(p))) } catch (_: Exception) {} }
                        displayFiles.removeAll { node -> selected.contains(node.path) }
                        selected.clear(); showSelectionToolbar = false; showChooseVaultFolder = false
                    }
                }) { Text("Move") }
            }, dismissButton = {
                TextButton(onClick = { showChooseVaultFolder = false }) { Text("Cancel") }
            }, title = { Text("Select Vault Folder") }, text = {
                Column {
                    folders.forEach { name ->
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { selectedFolder = name }, onLongClick = {})
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedFolder == name, onClick = { selectedFolder = name })
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: FileNode,
    category: FileCategory,
    isProtected: Boolean,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onGrantClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(onClick = { onOpenClick() }, onLongClick = { onSelectToggle() }),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Thumbnail(file = file, category = category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(File(file.path).absolutePath, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                Text(Formatter.formatFileSize(LocalContext.current, file.size), style = MaterialTheme.typography.labelSmall)
                if (isProtected) Text("Protected. Tap to grant access.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            if (isProtected) Icon(Icons.Filled.Lock, contentDescription = "Protected", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun Thumbnail(file: FileNode, category: FileCategory) {
    val painter = rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(File(file.path)).crossfade(true).build())
    when (category) {
        FileCategory.IMAGE, FileCategory.VIDEO -> Image(painter = painter, contentDescription = file.name, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        FileCategory.AUDIO -> Icon(Icons.Filled.MusicNote, contentDescription = "Audio", modifier = Modifier.size(40.dp).clip(CircleShape), tint = MaterialTheme.colorScheme.primary)
        FileCategory.DOCUMENT -> Icon(if (file.name.endsWith(".pdf", true)) Icons.Filled.PictureAsPdf else Icons.Filled.Description, contentDescription = "Doc", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        FileCategory.OTHER -> Icon(Icons.Filled.InsertDriveFile, contentDescription = "File", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
    }
}

fun fileCategoryForName(name: String): FileCategory {
    val lower = name.lowercase()
    return when {
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp").any { lower.endsWith(it) } -> FileCategory.IMAGE
        listOf(".mp4", ".mkv", ".avi", ".mov").any { lower.endsWith(it) } -> FileCategory.VIDEO
        listOf(".mp3", ".wav", ".ogg", ".m4a").any { lower.endsWith(it) } -> FileCategory.AUDIO
        // Documents: keep only pdf, doc, docx and txt per request
        listOf(".pdf", ".doc", ".docx", ".txt").any { lower.endsWith(it) } -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }
}

fun getMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "")
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}
