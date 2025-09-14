// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FilteredFileListScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileflowplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * FilteredFileListScreen - production-grade listing for filtered file sets.
 *
 * Responsibilities:
 *  - Display a list of matching files
 *  - Allow open/delete actions with robust SAF fallback
 *  - Keep UI in-sync with deletions across the app via broadcast
 *  - Remove stale rows when the underlying file is gone
 *
 * Notes:
 *  - This file assumes DeletionManager.deleteFile(context, node) returns one of:
 *      * DeleteResult.Deleted
 *      * DeleteResult.NeedUserGrant (includes suggestedUriToOpen: Uri?)
 *      * DeleteResult.Failed (includes reason)
 *  - Broadcast IntentActions.ACTION_FILE_DELETED is used app-wide to notify lists/aggregates.
 */

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredFileListScreen(
    files: List<File>,
    title: String,
    onBack: () -> Unit,
    onOpenViewer: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // reactive local copy of files
    val fileState = remember { mutableStateListOf<File>().apply { addAll(files) } }

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
                        fileState.removeAll { it.path == pending }
                        broadcastFileDeleted(context, pending)
                        tryRefreshMediaStore(context, pending)
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "Deleted ${File(pending).name}", Toast.LENGTH_SHORT).show()
                        }
                        pendingSafGrantForPath = null
                    }
                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                        // maybe different folder is required; keep pending and open suggested
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
                    fileState.removeAll { it.path == deletedPath || it.absolutePath == deletedPath }
                    // If we removed something and list is now empty, navigate back so parent screens can refresh aggregates
                    if (fileState.isEmpty()) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (fileState.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files found", style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items = fileState, key = { _, f -> f.path }) { _, file ->
                    // on composition, ensure file still exists. If not, remove it immediately so UI/counters stay consistent.
                    LaunchedEffect(file.path) {
                        if (!existsAtPath(context, file.path)) {
                            fileState.removeAll { it.path == file.path }
                            broadcastFileDeleted(context, file.path)
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!existsAtPath(context, file.path)) {
                                        fileState.removeAll { it.path == file.path }
                                        broadcastFileDeleted(context, file.path)
                                        Toast.makeText(context, "${file.name} not available. Removed from list.", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    onOpenViewer(file)
                                }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilePreviewThumbnail(file = File(file.path))
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${file.length() / 1024} KB", style = MaterialTheme.typography.labelMedium)
                                }

                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Open") },
                                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                if (!existsAtPath(context, file.path)) {
                                                    fileState.removeAll { it.path == file.path }
                                                    broadcastFileDeleted(context, file.path)
                                                    Toast.makeText(context, "${file.name} not available. Removed from list.", Toast.LENGTH_SHORT).show()
                                                    return@DropdownMenuItem
                                                }
                                                onOpenViewer(file)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                val pathToDelete = file.path
                                                val nameToShow = file.name

                                                coroutineScope.launch {
                                                    val node = try { FileNode.fromFile(file) } catch (_: Throwable) { null }
                                                    if (node == null) {
                                                        Toast.makeText(context, "Unable to delete: internal error", Toast.LENGTH_LONG).show()
                                                        return@launch
                                                    }

                                                    when (val res = DeletionManager.deleteFile(context, node)) {
                                                        is DeletionManager.DeleteResult.Deleted -> {
                                                            fileState.removeAll { it.path == pathToDelete }
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
                        }
                    }
                }
            }
        }
    }
}

/** Broadcast that a file was deleted so other parts of the app can update. */
private fun broadcastFileDeleted(context: Context, path: String) {
    try {
        val intent = Intent(IntentActions.ACTION_FILE_DELETED).apply {
            putExtra(IntentActions.EXTRA_DELETED_PATH, path)
        }
        context.sendBroadcast(intent)
    } catch (t: Throwable) {
        Log.w("FilteredFileList", "broadcastFileDeleted failed: ${t.localizedMessage}")
    }
}

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
