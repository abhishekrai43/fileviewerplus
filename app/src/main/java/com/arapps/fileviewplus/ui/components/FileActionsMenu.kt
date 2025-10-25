package com.arapps.fileviewplus.ui.components

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.core.AppGlobals
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETE_MANUAL_REMIND
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.utils.copyFileToVault
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.viewer.ViewerRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// (using string tokens for pendingAction to keep code robust and avoid type-resolution issues)
private const val ACTION_COPY = "COPY"
private const val ACTION_MOVE = "MOVE"
private const val ACTION_EXPORT = "EXPORT"
private const val ACTION_ZIP = "ZIP"
private const val ACTION_DELETE = "DELETE"

// FileActionsMenu: file-level actions with graceful SAF handling and retry flows
@Composable
fun FileActionsMenu(
    file: FileNode?,
    modifier: Modifier = Modifier,
    onFileDeleted: (FileNode) -> Unit = {},
    /**
     * Called when this component thinks the caller should request a tree permission for a given path.
     * The string is an optional initial Uri (as string) which many devices accept to pre-open the folder.
     *
     * If null, the component will attempt to launch its own SAF picker.
     */
    onGrantClick: ((String?) -> Unit)? = null
) {
    if (file == null) return

    // reference onGrantClick so linter doesn't warn; parent may still use it in other flows
    onGrantClick?.let { /* no-op: kept for API compatibility */ }

    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInvalidFolderDialog by remember { mutableStateOf(false) }
    var lastInvalidUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // state to show the in-app open/intent chooser
    var showViewerChooser by remember { mutableStateOf(false) }

    // New: separate state for Move-to-Vault folder chooser and post-copy delete confirmation
    var showMoveToVaultFolderDialog by remember { mutableStateOf(false) }
    var showDeleteOriginalConfirm by remember { mutableStateOf(false) }
    var lastCopiedFile by remember { mutableStateOf<File?>(null) }
    var vaultFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVaultFolder by remember { mutableStateOf("") }
    var showVaultPinRequiredDialog by remember { mutableStateOf(false) }

    // small progress UI state
    var showOpProgress by remember { mutableStateOf(false) }
    var opLabel by remember { mutableStateOf("") }

    // Pending action state used when we need the user to pick a destination folder (string token)
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<FileNode?>(null) }

    // General-purpose SAF tree picker. After the user selects a folder we attempt the pending action.
    val pickTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Permission not granted. Cannot proceed.", Toast.LENGTH_LONG).show()
            pendingAction = null; pendingFile = null
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val isUsable = withContext(Dispatchers.IO) { isWritableAndNotRestricted(context, uri) }
            if (!isUsable) {
                lastInvalidUri = uri
                showInvalidFolderDialog = true
                return@launch
            }

            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {}

            // Execute pending action
            val pf = pendingFile
            val action = pendingAction
            pendingAction = null
            pendingFile = null
            if (pf == null || action == null) return@launch

            // show progress
            withContext(Dispatchers.Main) {
                showOpProgress = true
                opLabel = when (action) {
                    ACTION_COPY -> "Copying..."
                    ACTION_MOVE -> "Moving..."
                    ACTION_EXPORT -> "Exporting..."
                    ACTION_ZIP -> "Creating zip..."
                    ACTION_DELETE -> "Deleting..."
                    else -> "Working..."
                }
            }

            val res = try {
                when (action) {
                    ACTION_COPY -> performCopyToUri(context, pf, uri)
                    ACTION_MOVE -> performMoveToUri(context, pf, uri)
                    ACTION_EXPORT -> performExportToUri(context, pf, uri)
                    ACTION_ZIP -> performZipToUri(context, pf, uri)
                    ACTION_DELETE -> attemptDeleteWithFallbacks(context, pf)
                    else -> DeletionResult.Failed("No action")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showOpProgress = false
                    opLabel = ""
                }
            }

            withContext(Dispatchers.Main) {
                val actionLabel = action.lowercase().replaceFirstChar { ch: Char -> ch.uppercaseChar() }
                when (res) {
                    is DeletionResult.Deleted -> {
                        Toast.makeText(context, "$actionLabel succeeded", Toast.LENGTH_SHORT).show()
                        if (action == ACTION_MOVE || action == ACTION_DELETE) {
                            try { onFileDeleted(pf) } catch (_: Exception) {}
                            try { sendDeletedBroadcast(context, pf.path) } catch (_: Exception) {}
                        }
                    }
                    is DeletionResult.Failed -> Toast.makeText(context, "$actionLabel failed: ${res.reason}", Toast.LENGTH_LONG).show()
                    is DeletionResult.NeedUserGrant -> Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            }
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
                    // Show chooser allowing user to pick internal viewer vs external app and actions
                    showViewerChooser = true
                }
            )

            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val f = File(file.path)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                f
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            withContext(Dispatchers.Main) {
                                context.startActivity(Intent.createChooser(shareIntent, "Share file via"))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    expanded = false
                    pendingAction = ACTION_COPY
                    pendingFile = file
                    pickTreeLauncher.launch(null)
                }
            )

            DropdownMenuItem(
                text = { Text("Move") },
                onClick = {
                    expanded = false
                    pendingAction = ACTION_MOVE
                    pendingFile = file
                    pickTreeLauncher.launch(null)
                }
            )

            DropdownMenuItem(
                text = { Text("Export") },
                onClick = {
                    expanded = false
                    pendingAction = ACTION_EXPORT
                    pendingFile = file
                    pickTreeLauncher.launch(null)
                }
            )

            DropdownMenuItem(
                text = { Text("Zip") },
                onClick = {
                    expanded = false
                    pendingAction = ACTION_ZIP
                    pendingFile = file
                    pickTreeLauncher.launch(null)
                }
            )

            DropdownMenuItem(
                text = { Text("Move to Vault") },
                onClick = {
                    expanded = false
                    val ctx = context
                    // Require PIN to be set before allowing move-to-vault
                    val pin = try { getStoredPin(ctx) } catch (_: Exception) { null }
                    if (pin.isNullOrEmpty()) {
                        showVaultPinRequiredDialog = true
                        return@DropdownMenuItem
                    }
                    // Show folder chooser for vault destination instead of directly copying to root
                    val vr = File(ctx.filesDir, ".vault").apply { mkdirs() }
                    vaultFolders = vr.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                    selectedVaultFolder = vaultFolders.firstOrNull() ?: ""
                    showMoveToVaultFolderDialog = true
                }
            )

            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete") },
                text = { Text("Delete") },
                onClick = {
                    expanded = false
                    showDeleteDialog = true
                }
            )
        }
    }

    // Dialog prompting user to set up a PIN before first vault use
    if (showVaultPinRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showVaultPinRequiredDialog = false },
            title = { Text("Set up Vault PIN") },
            text = { Text("To protect your files, please set a Vault PIN before moving items to the Vault.") },
            confirmButton = {
                TextButton(onClick = {
                    showVaultPinRequiredDialog = false
                    // Navigate to Vault screen where SetupPinDialog will appear
                    coroutineScope.launch { AppGlobals.navigateTo.emit("vault") }
                }) { Text("Open Vault") }
            },
            dismissButton = {
                TextButton(onClick = { showVaultPinRequiredDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: choose vault folder to copy into
    if (showMoveToVaultFolderDialog) {
        val ctx = context
        val vaultRoot = File(ctx.filesDir, ".vault").apply { mkdirs() }
        AlertDialog(
            onDismissRequest = { showMoveToVaultFolderDialog = false },
            title = { Text("Move to Vault") },
            text = {
                Column {
                    if (vaultFolders.isEmpty()) {
                        Text("No folders found in vault. Files will be copied to the vault root. You can create folders from Vault screen.")
                    } else {
                        Text("Choose a destination folder in the vault:")
                        Spacer(modifier = Modifier.height(8.dp))
                        vaultFolders.forEach { name ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { selectedVaultFolder = name }.padding(8.dp)) {
                                androidx.compose.material3.RadioButton(selected = selectedVaultFolder == name, onClick = { selectedVaultFolder = name })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showMoveToVaultFolderDialog = false
                    // perform copy to chosen folder
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val dest = if (selectedVaultFolder.isBlank()) vaultRoot else File(vaultRoot, selectedVaultFolder)
                            if (!dest.exists()) dest.mkdirs()
                            val copied = copyFileToVault(file.path, dest)
                            withContext(Dispatchers.Main) {
                                if (copied != null) {
                                    Toast.makeText(ctx, "Moved to Vault: ${copied.name}", Toast.LENGTH_SHORT).show()
                                    // Fire a success notification that opens Vault when tapped
                                    try { NotificationUtils.showVaultMovedNotification(ctx, copied.name) } catch (_: Exception) {}
                                    lastCopiedFile = copied
                                    // Ask user separately whether to delete the original file
                                    showDeleteOriginalConfirm = true
                                } else {
                                    Toast.makeText(ctx, "Failed to move to Vault", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (t: Throwable) {
                            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Error: ${t.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }) { Text("Move") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMoveToVaultFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Confirmation dialog for original-file deletion after a Move-to-Vault copy
    if (showDeleteOriginalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteOriginalConfirm = false },
            title = { Text("Delete original file?") },
            text = { Text("Do you want to delete the original file after copying it to the vault? This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteOriginalConfirm = false
                    coroutineScope.launch {
                        val res = attemptDeleteWithFallbacks(context, file)
                        when (res) {
                            is DeletionResult.Deleted -> {
                                withContext(Dispatchers.Main) {
                                    onFileDeleted(file)
                                    sendDeletedBroadcast(context, file.path)
                                    Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is DeletionResult.NeedUserGrant -> {
                                withContext(Dispatchers.Main) { Toast.makeText(context, res.message, Toast.LENGTH_LONG).show() }
                                // Ask user to pick a folder for deletion retry
                                pendingAction = ACTION_DELETE
                                pendingFile = file
                                pickTreeLauncher.launch(res.suggestedUriToOpen)
                            }
                            is DeletionResult.Failed -> {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Could not delete: ${res.reason}. You may need to delete it from the original location.", Toast.LENGTH_LONG).show()
                                    notifyManualDeleteRequired(context, file.path)
                                }
                            }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteOriginalConfirm = false }) { Text("Keep Original") }
            }
        )
    }

    // Confirmation dialog for delete (explicit Delete action)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete file") },
            text = { Text("Are you sure you want to permanently delete \"${file.name}\"? This action cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteDialog = false
                    coroutineScope.launch {
                        val res = attemptDeleteWithFallbacks(context, file)
                        when (res) {
                            is DeletionResult.Deleted -> {
                                withContext(Dispatchers.Main) {
                                    onFileDeleted(file)
                                    sendDeletedBroadcast(context, file.path)
                                    Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is DeletionResult.NeedUserGrant -> {
                                withContext(Dispatchers.Main) { Toast.makeText(context, res.message, Toast.LENGTH_LONG).show() }
                                // Ask user to pick a folder for deletion retry
                                pendingAction = ACTION_DELETE
                                pendingFile = file
                                pickTreeLauncher.launch(res.suggestedUriToOpen)
                            }
                            is DeletionResult.Failed -> {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Could not delete: ${res.reason}. You may need to delete it from the original location.", Toast.LENGTH_LONG).show()
                                    notifyManualDeleteRequired(context, file.path)
                                }
                            }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Show dialog if user picks an invalid folder
    if (showInvalidFolderDialog) {
        AlertDialog(
            onDismissRequest = { showInvalidFolderDialog = false },
            title = { Text("Folder Not Usable", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "The folder you selected cannot be used for this operation. This is a limitation imposed by Android for security reasons, not an app issue. Please choose a different folder (not a system or restricted folder).",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(onClick = {
                    showInvalidFolderDialog = false
                    pickTreeLauncher.launch(null)
                }) { Text("Choose Different Folder", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInvalidFolderDialog = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }

    // Progress modal
    if (showOpProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(opLabel) },
            text = { androidx.compose.material3.CircularProgressIndicator() },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // ViewerChooser - a compact modal that offers internal open, external open and actions
    if (showViewerChooser) {
        ViewerChooser(
            file = file,
            onDismiss = { showViewerChooser = false },
            onOpenInternal = {
                try {
                    ViewerRouter.openFile(context.findActivity() ?: context, file, fromVault = false)
                } catch (t: Throwable) { Toast.makeText(context, "Failed to open: ${t.localizedMessage}", Toast.LENGTH_SHORT).show() }
            },
            onOpenExternal = {
                // build external view intent
                try {
                    val f = java.io.File(file.path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", f)
                    val ext = file.extension.lowercase()
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                } catch (t: Throwable) { Toast.makeText(context, "No app found: ${t.localizedMessage}", Toast.LENGTH_SHORT).show() }
            },
            onMoveToVault = { showMoveToVaultFolderDialog = true },
            onZip = {
                pendingAction = ACTION_ZIP; pendingFile = file; pickTreeLauncher.launch(null)
            },
            onDelete = { showDeleteDialog = true }
        )
    }
}

// --- Unified SAF permission/request logic ---
/** Helper: send broadcast to inform other screens that a file was deleted */
private fun sendDeletedBroadcast(context: Context, path: String) {
    val b = Intent(ACTION_FILE_DELETED).apply {
        putExtra(EXTRA_DELETED_PATH, path)
    }
    try {
        context.sendBroadcast(b)
    } catch (_: Exception) {
        // best-effort
    }
}

/** Helper: notify that manual deletion is required (SAF restriction). Do not remove file from model. */
private fun notifyManualDeleteRequired(context: Context, path: String) {
    val b = Intent(ACTION_FILE_DELETED).apply {
        putExtra(EXTRA_DELETED_PATH, path)
        putExtra(EXTRA_DELETE_MANUAL_REMIND, true)
    }
    try {
        context.sendBroadcast(b)
    } catch (_: Exception) {
        // best-effort
    }
}

/** Local sealed result for this file (file-private) */
private sealed class DeletionResult {
    object Deleted : DeletionResult()
    data class NeedUserGrant(val suggestedUriToOpen: Uri?, val message: String) : DeletionResult()
    data class Failed(val reason: String) : DeletionResult()
}

/**
 * Attempt deletion using:
 *  1) MediaStore deletion (images/video/audio)
 *  2) Direct java.io.File.delete()
 *  3) DeletionManager (SAF + persisted tree URI)
 *
 * Returns DeletionResult indicating what happened.
 */
private suspend fun attemptDeleteWithFallbacks(context: Context, file: FileNode): DeletionResult =
    withContext(Dispatchers.IO) {
        var result: DeletionResult = DeletionResult.Failed("Unknown error while deleting")
        try {
            // MediaStore first for user-visible media
            if (isLikelyMediaFile(file.path)) {
                val mediaDeleted = try { attemptMediaStoreDelete(context, file.path) } catch (_: Throwable) { false }
                if (mediaDeleted) {
                    result = DeletionResult.Deleted
                }
            }

            // Direct file delete (only if not already deleted by MediaStore)
            if (result !is DeletionResult.Deleted) {
                val f = File(file.path)
                if (f.exists()) {
                    val directOk = try { f.delete() } catch (_: Throwable) { false }
                    if (directOk) result = DeletionResult.Deleted
                }
            }

            // SAF / persisted tree URIs via DeletionManager (only if still not deleted)
            if (result !is DeletionResult.Deleted) {
                when (val dm = DeletionManager.deleteFile(context, file)) {
                    is DeletionManager.DeleteResult.Deleted -> result = DeletionResult.Deleted
                    is DeletionManager.DeleteResult.NeedUserGrant -> result = DeletionResult.NeedUserGrant(dm.suggestedUriToOpen, dm.message)
                    is DeletionManager.DeleteResult.Failed -> result = DeletionResult.Failed(dm.reason)
                }
            }
        } catch (ex: Exception) {
            result = DeletionResult.Failed(ex.localizedMessage ?: ex.toString())
        }
        return@withContext result
    }

/** Conservative extension-based media detector */
private fun isLikelyMediaFile(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".webm") || lower.endsWith(".mp3") ||
            lower.endsWith(".wav") || lower.endsWith(".3gp") || lower.endsWith(".mov")
}

/**
 * Try to find the file in MediaStore and delete it via ContentResolver.
 * Returns true if it was deleted.
 */
private fun attemptMediaStoreDelete(context: Context, absolutePath: String): Boolean {
    try {
        val cr = context.contentResolver
        // Images
        if (queryAndDeleteFromStore(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
        // Videos
        if (queryAndDeleteFromStore(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
        // Audio
        if (queryAndDeleteFromStore(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, absolutePath)) return true
    } catch (_: Throwable) {
        // ignore — fallback to SAF
    }
    return false
}

/**
 * Query the given collection for an entry matching the absolute path and delete it.
 * Returns true if deletion succeeded.
 */
private fun queryAndDeleteFromStore(cr: android.content.ContentResolver, collection: Uri, absolutePath: String): Boolean {
    var cursor: android.database.Cursor? = null
    var result: Boolean
    try {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
        val sel = "${MediaStore.MediaColumns.DATA} = ?"
        cursor = cr.query(collection, projection, sel, arrayOf(absolutePath), null)
        if (cursor != null && cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val id = cursor.getLong(idIndex)
            val uri = ContentUris.withAppendedId(collection, id)
            try {
                val rows = cr.delete(uri, null, null)
                result = rows > 0
            } catch (se: SecurityException) {
                result = false
            }
        } else {
            result = false
        }
    } catch (_: Exception) {
        result = false
    } finally {
        try { cursor?.close() } catch (_: Exception) {}
    }
    return result
}

// Implement copy/move/export/zip helpers
private suspend fun performCopyToUri(context: Context, file: FileNode, treeUri: Uri): DeletionResult =
    withContext(Dispatchers.IO) {
        try {
            val f = File(file.path)
            if (!f.exists()) return@withContext DeletionResult.Failed("Source file not found")
            val extension = f.extension
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            val targetName = generateUniqueName(context, treeUri, f.name)
            val created = DocumentsContract.createDocument(context.contentResolver, treeUri, mime, targetName)
                ?: return@withContext DeletionResult.Failed("Failed to create destination file")
            context.contentResolver.openOutputStream(created).use { out ->
                FileInputStream(f).use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } > 0) {
                        out?.write(buffer, 0, read)
                    }
                    out?.flush()
                }
            }
            return@withContext DeletionResult.Deleted
        } catch (ex: Exception) {
            return@withContext DeletionResult.Failed(ex.localizedMessage ?: ex.toString())
        }
    }

private suspend fun performMoveToUri(context: Context, file: FileNode, treeUri: Uri): DeletionResult =
    withContext(Dispatchers.IO) {
        val copyRes = performCopyToUri(context, file, treeUri)
        if (copyRes !is DeletionResult.Deleted) return@withContext copyRes
        // After copy, attempt delete original
        val delRes = attemptDeleteWithFallbacks(context, file)
        return@withContext when (delRes) {
            is DeletionResult.Deleted -> DeletionResult.Deleted
            is DeletionResult.NeedUserGrant -> DeletionResult.NeedUserGrant(delRes.suggestedUriToOpen, delRes.message)
            is DeletionResult.Failed -> DeletionResult.Failed("Moved but failed to delete original: ${delRes.reason}")
        }
    }

private suspend fun performExportToUri(context: Context, file: FileNode, treeUri: Uri): DeletionResult =
    withContext(Dispatchers.IO) {
        // Export treated as copy to target folder (explicit SAF destination chosen by user)
        performCopyToUri(context, file, treeUri)
    }

private suspend fun performZipToUri(context: Context, file: FileNode, treeUri: Uri): DeletionResult =
    withContext(Dispatchers.IO) {
        try {
            val f = File(file.path)
            if (!f.exists()) return@withContext DeletionResult.Failed("Source file not found")
            // create temp zip in cache
            val zipName = "${f.nameWithoutExtension}.zip"
            val uniqueZipName = generateUniqueName(context, treeUri, zipName)
            val tempZip = File(context.cacheDir, zipName)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
                val entry = ZipEntry(f.name)
                zos.putNextEntry(entry)
                BufferedInputStream(FileInputStream(f)).use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
                zos.finish()
            }
            // write temp zip to chosen SAF folder
            val mime = "application/zip"
            val created = DocumentsContract.createDocument(context.contentResolver, treeUri, mime, uniqueZipName)
                ?: return@withContext DeletionResult.Failed("Failed to create zip in destination")
            context.contentResolver.openOutputStream(created).use { out ->
                FileInputStream(tempZip).use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } > 0) {
                        out?.write(buffer, 0, read)
                    }
                    out?.flush()
                }
            }
            // cleanup
            try { tempZip.delete() } catch (_: Throwable) {}
            return@withContext DeletionResult.Deleted
        } catch (ex: Exception) {
            return@withContext DeletionResult.Failed(ex.localizedMessage ?: ex.toString())
        }
    }

// helper: find child document by display name under a tree and return document Uri if found
private fun findChildDocumentUri(context: Context, treeUri: Uri, name: String): Uri? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        var cursor: android.database.Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            while (cursor != null && cursor.moveToNext()) {
                val id = cursor.getString(0)
                val display = cursor.getString(1)
                if (display == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        } finally {
            try { cursor?.close() } catch (_: Exception) {}
        }
        null
    } catch (_: Exception) {
        null
    }
}

// helper: generate a unique name by appending (1), (2), ... if a file with same name exists
private fun generateUniqueName(context: Context, treeUri: Uri, name: String): String {
    if (findChildDocumentUri(context, treeUri, name) == null) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1
    while (i < 1000) {
        val candidate = "$base ($i)$ext"
        if (findChildDocumentUri(context, treeUri, candidate) == null) return candidate
        i++
    }
    return "${base}_${System.currentTimeMillis()}$ext"
}

// add this helper to check if a SAF Uri is writable and not a restricted/system folder
private fun isWritableAndNotRestricted(context: Context, uri: Uri): Boolean {
    // Try to create a temp file in the folder
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val isRestricted = docId.startsWith("primary:Android/data") ||
                docId.startsWith("primary:Android/obb") ||
                docId.equals("primary:", ignoreCase = true) ||
                docId.equals("primary:Download", ignoreCase = true) ||
                docId.equals("downloads", ignoreCase = true)
        if (isRestricted) return false
        val testFileName = "__saf_test_${System.currentTimeMillis()}"
        val testFileUri = android.provider.DocumentsContract.createDocument(
            context.contentResolver, uri, "text/plain", testFileName
        )
        if (testFileUri != null) {
            // Clean up
            context.contentResolver.delete(testFileUri, null, null)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}
