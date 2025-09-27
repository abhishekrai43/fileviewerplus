// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FilteredFileListScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.copyFilesToVaultAsync
import com.arapps.fileviewplus.model.FilterMode
import com.arapps.fileviewplus.ui.components.vault.EnterPinDialog
import com.arapps.fileviewplus.utils.broadcastFileDeleted
import com.arapps.fileviewplus.logic.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Private top-level helpers for this file
private fun normalizePath(p: String): String {
    return when {
        p.startsWith("file://") -> Uri.parse(p).path ?: p
        else -> p
    }
}

private fun existsAtPath(context: Context, pathOrUri: String): Boolean {
    return try {
        when {
            pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://") -> {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }
            else -> {
                val f = File(pathOrUri)
                f.exists()
            }
        }
    } catch (t: Throwable) {
        Log.w("FilteredFileList", "existsAtPath failed for $pathOrUri: ${t.localizedMessage}")
        false
    }
}

private fun tryRefreshMediaStore(context: Context, rawPath: String) {
    try {
        val normalized = normalizePath(rawPath)
        MediaScannerConnection.scanFile(context, arrayOf(normalized), null) { scannedPath, uri ->
            Log.d("DeleteDebug", "scanFile completed for $scannedPath -> $uri")
        }
    } catch (t: Throwable) {
        Log.w("DeleteDebug", "scanFile failed: ${t.localizedMessage}")
    }
}

/**
 * FilteredFileListScreen - production-grade listing for filtered file sets.
 *
 * Enhanced to show grouped category tiles (IMG/VID/AUDIO/DOC) and per-group flat lists with multi-select.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("NewApi")
@Composable
fun FilteredFileListScreen(
    files: List<File>,
    title: String,
    onBack: () -> Unit,
    onOpenViewer: (File) -> Unit,
    filterMode: FilterMode = FilterMode.NONE
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // reactive local copy of files as FileNode for richer metadata
    val fileNodes = remember { mutableStateListOf<FileNode>().apply { addAll(files.mapNotNull { f -> try { FileNode.fromFile(f) } catch (_: Throwable) { null } }) } }

    // UI state: when null show grouped view, otherwise show files for selected group key
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    // selection state for flat list view
    val selected = remember { mutableStateListOf<String>() }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showEnterPin by remember { mutableStateOf(false) }
    var showChooseVaultFolder by remember { mutableStateOf(false) }
    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }

    // pending path to retry deletion after SAF permission granted
    var pendingSafGrantForPath by remember { mutableStateOf<String?>(null) }

    // A small helper to avoid calling safLauncher.launch(...) inside tricky nested scopes.
    // This is declared first so it can be called later.
    var launchSafPicker: ((Uri?) -> Unit)? by remember { mutableStateOf(null) }

    // SAF launcher declared here (top of composable)
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri == null) {
            Toast.makeText(context, "No folder selected", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        // persist permission
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            Toast.makeText(context, "Access granted", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "Failed to persist permission: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        // retry pending deletion if present
        val pending = pendingSafGrantForPath
        if (pending != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val node = try { FileNode.fromFile(File(pending)) } catch (_: Throwable) { null }
                if (node == null) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Internal error: cannot construct file info to retry", Toast.LENGTH_LONG).show()
                    }
                    pendingSafGrantForPath = null
                    return@launch
                }

                when (val res = DeletionManager.deleteFile(context, node)) {
                    is DeletionManager.DeleteResult.Deleted -> {
                        fileNodes.removeAll { it.path == pending }
                        broadcastFileDeleted(context, pending)
                        tryRefreshMediaStore(context, pending)
                        try {
                            coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "Deleted ${File(pending).name}", Toast.LENGTH_SHORT).show() }
                        } catch (_: Throwable) {}
                        pendingSafGrantForPath = null
                    }
                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                        // Save pending path and open SAF picker using helper that references the launcher
                        pendingSafGrantForPath = pending
                        val suggested = res.suggestedUriToOpen
                        // call via helper to ensure launcher is active on UI thread
                        coroutineScope.launch(Dispatchers.Main) {
                            launchSafPicker?.invoke(suggested)
                        }
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    is DeletionManager.DeleteResult.Failed -> {
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "Unable to delete: ${res.reason}", Toast.LENGTH_LONG).show()
                        }
                        pendingSafGrantForPath = null
                    }
                }
            }
        }
    }

    // Assign the launcher to the helper function reference
    DisposableEffect(Unit) {
        launchSafPicker = { suggestedUri -> safLauncher.launch(suggestedUri) }
        onDispose {
            launchSafPicker = null
        }
    }

    // ---- Keep this screen synced with app-wide deletions ----
    DisposableEffect(Unit) {
        val filter = IntentFilter(IntentActions.ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val deletedPath = intent?.getStringExtra(IntentActions.EXTRA_DELETED_PATH) ?: return
                try {
                    // Remove any file rows matching the deleted absolute path
                    val normalized = normalizePath(deletedPath)
                    fileNodes.removeAll { it.path == deletedPath || it.path == normalized }
                    // If we removed something and list is now empty, navigate back so parent screens can refresh aggregates
                    if (fileNodes.isEmpty()) {
                        onBack()
                    }
                } catch (t: Throwable) {
                    Log.w("FilteredFileList", "Error handling delete broadcast: ${t.localizedMessage}")
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    BackHandler(onBack = onBack)

    // Helper predicates for grouping
    val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr", "opus", "wma")

    fun isAudioFile(fn: FileNode): Boolean {
        try {
            val ext = if (fn.extension.isNotBlank()) fn.extension.trim().lowercase() else fn.name.substringAfterLast('.', "").lowercase().trim()
            if (fn.type == FileNode.FileType.Audio) return true
            if (ext.isNotEmpty() && audioExts.contains(ext)) return true
            val path = fn.path.lowercase()
            if (audioExts.any { path.endsWith("." + it) }) return true
        } catch (_: Exception) {}
        return false
    }

    fun isImageFile(fn: FileNode): Boolean {
        return fn.type == FileNode.FileType.Image || fn.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    }

    fun isVideoFile(fn: FileNode): Boolean {
        return fn.type == FileNode.FileType.Video || fn.extension.lowercase() in setOf("mp4", "mkv", "mov", "avi", "wmv", "webm", "3gp")
    }

    fun isDocumentFile(fn: FileNode): Boolean {
        return fn.type == FileNode.FileType.Document || fn.extension.lowercase() in setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf")
    }

    Scaffold(
        topBar = {
            if (showSelectionToolbar && selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = { selected.clear(); showSelectionToolbar = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection") } },
                    actions = {
                        IconButton(onClick = {
                            val uris = selected.mapNotNull { p ->
                                try { FileProvider.getUriForFile(context, context.packageName + ".provider", File(p)) } catch (_: Exception) { null }
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uris.first())
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share files"))
                            }
                        }) { Icon(imageVector = Icons.Default.Share, contentDescription = "Share") }

                        IconButton(onClick = { showEnterPin = true }) { Icon(imageVector = Icons.Default.Lock, contentDescription = "Move to Vault") }

                        IconButton(onClick = {
                            coroutineScope.launch {
                                selected.toList().forEach { p ->
                                    try {
                                        val node = FileNode.fromFile(File(p))
                                        when (val res = DeletionManager.deleteFile(context, node)) {
                                            DeletionManager.DeleteResult.Deleted -> {
                                                fileNodes.removeAll { it.path == p }
                                                broadcastFileDeleted(context, p)
                                                tryRefreshMediaStore(context, p)
                                            }
                                            is DeletionManager.DeleteResult.NeedUserGrant -> {
                                                pendingSafGrantForPath = p
                                                val suggested = res.suggestedUriToOpen
                                                coroutineScope.launch(Dispatchers.Main) { launchSafPicker?.invoke(suggested) }
                                                coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, res.message, Toast.LENGTH_LONG).show() }
                                            }
                                            is DeletionManager.DeleteResult.Failed -> {
                                                coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                selected.clear(); showSelectionToolbar = false
                            }
                        }) { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (fileNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files found", style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }

            if (selectedGroup == null) {
                // Show grouped tile view: IMG, VID, AUDIO, DOC
                val groups = listOf(
                    Triple("IMG", ::isImageFile, "Images"),
                    Triple("VID", ::isVideoFile, "Videos"),
                    Triple("AUDIO", ::isAudioFile, "Audio"),
                    Triple("DOC", ::isDocumentFile, "Documents")
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groups) { grp ->
                        val key = grp.first
                        val predicate = grp.second
                        val display = grp.third
                        val itemsInGroup = fileNodes.filter { predicate(it) }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clickable { selectedGroup = key },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column { Text(display, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Spacer(Modifier.height(4.dp)); Text("${itemsInGroup.size} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                // show total size in human-friendly units (KB/MB/GB)
                                val sizeBytes = itemsInGroup.sumOf { it.size }
                                Text(text = StorageStats.formatSize(sizeBytes), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }

            } else {
                // Show flat list for selected group
                val predicate = when (selectedGroup) {
                    "IMG" -> ::isImageFile
                    "VID" -> ::isVideoFile
                    "AUDIO" -> ::isAudioFile
                    "DOC" -> ::isDocumentFile
                    else -> { fn: FileNode -> true }
                }

                // Header row to go back to groups
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedGroup = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups") }
                    Text(
                        text = when (selectedGroup) {
                            "IMG" -> "Images"
                            "VID" -> "Videos"
                            "AUDIO" -> "Audio"
                            "DOC" -> "Documents"
                            else -> title
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val groupFiles = fileNodes.filter { predicate(it) }
                    items(groupFiles, key = { it.path }) { fn ->
                        val isSelected = selected.contains(fn.path)
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = {
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(fn.path) else selected.add(fn.path)
                                        showSelectionToolbar = selected.isNotEmpty()
                                    } else {
                                        if (!existsAtPath(context, fn.path)) {
                                            fileNodes.removeAll { it.path == fn.path }
                                            broadcastFileDeleted(context, fn.path)
                                            Toast.makeText(context, "${fn.name} not available. Removed from list.", Toast.LENGTH_SHORT).show()
                                            return@combinedClickable
                                        }
                                        onOpenViewer(File(fn.path))
                                    }
                                }, onLongClick = {
                                    if (!isSelected) selected.add(fn.path)
                                    showSelectionToolbar = true
                                }) ) {

                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilePreviewThumbnail(file = File(fn.path))
                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                fn.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(StorageStats.formatSize(fn.size), style = MaterialTheme.typography.labelMedium)
                                        }

                                        var menuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { menuExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                            }
                                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Open") },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        if (!existsAtPath(context, fn.path)) {
                                                            fileNodes.removeAll { it.path == fn.path }
                                                            broadcastFileDeleted(context, fn.path)
                                                            Toast.makeText(context, "${fn.name} not available. Removed from list.", Toast.LENGTH_SHORT).show()
                                                            return@DropdownMenuItem
                                                        }
                                                        onOpenViewer(File(fn.path))
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        val pathToDelete = fn.path
                                                        val nameToShow = fn.name

                                                        coroutineScope.launch {
                                                            val node = try { FileNode.fromFile(File(pathToDelete)) } catch (_: Throwable) { null }
                                                            if (node == null) {
                                                                Toast.makeText(context, "Unable to delete: internal error", Toast.LENGTH_LONG).show()
                                                                return@launch
                                                            }

                                                            when (val res = DeletionManager.deleteFile(context, node)) {
                                                                is DeletionManager.DeleteResult.Deleted -> {
                                                                    fileNodes.removeAll { it.path == pathToDelete }
                                                                    broadcastFileDeleted(context, pathToDelete)
                                                                    tryRefreshMediaStore(context, pathToDelete)
                                                                    Toast.makeText(context, "Deleted $nameToShow", Toast.LENGTH_SHORT).show()
                                                                }

                                                                is DeletionManager.DeleteResult.NeedUserGrant -> {
                                                                    // Save pending path and open SAF picker using helper that references the launcher
                                                                    pendingSafGrantForPath = pathToDelete
                                                                    val suggested = res.suggestedUriToOpen
                                                                    // IMPORTANT: use helper reference so launcher is invoked from proper scope
                                                                    launchSafPicker?.invoke(suggested)
                                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                                }

                                                                is DeletionManager.DeleteResult.Failed -> {
                                                                    Toast.makeText(context, "Unable to delete: ${res.reason}", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // selection overlay: stronger, theme-aware color for better visibility on light theme
                                    if (isSelected) {
                                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Move to vault flows (reuse existing logic, selected list is used)
    if (showEnterPin) {
        EnterPinDialog(onPinEntered = { pin -> if (pin == getStoredPin(context)) { showEnterPin = false; showChooseVaultFolder = true } else Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show() }, onDismiss = { showEnterPin = false }, onForgotPin = {})
    }

    if (showChooseVaultFolder) {
        val folders = vaultRoot.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        var selectedFolder by remember { mutableStateOf(folders.firstOrNull() ?: "") }
        AlertDialog(onDismissRequest = { showChooseVaultFolder = false }, title = { Text("Select Vault Folder") }, text = {
            Column { folders.forEach { name -> Row(modifier = Modifier.fillMaxWidth().clickable { selectedFolder = name }.padding(8.dp)) { RadioButton(selected = selectedFolder == name, onClick = { selectedFolder = name }); Spacer(Modifier.width(8.dp)); Text(name) } } }
        }, confirmButton = {
            TextButton(onClick = {
                val dest = if (selectedFolder.isBlank()) vaultRoot else File(vaultRoot, selectedFolder)
                coroutineScope.launch {
                    val copied = copyFilesToVaultAsync(context, selected.toList(), dest)
                    withContext(Dispatchers.Main) { Toast.makeText(context, "${copied.size} file(s) copied to vault", Toast.LENGTH_SHORT).show() }
                    selected.clear(); showSelectionToolbar = false; showChooseVaultFolder = false
                }
            }) { Text("Move") }
        }, dismissButton = { TextButton(onClick = { showChooseVaultFolder = false }) { Text("Cancel") } })
    } // end of FilteredFileListScreen composable
} // final file-level closing brace (ensures no unterminated declarations)
