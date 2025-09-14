package com.arapps.fileviewplus.ui.components

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETE_MANUAL_REMIND
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.utils.ZipUtils
import com.arapps.fileviewplus.utils.findActivity
import com.arapps.fileviewplus.viewer.ViewerRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FileActionsMenu (production-grade)
 *
 * - onFileDeleted is invoked only when deletion succeeded.
 * - onGrantClick(path) is called when the component requests the parent to open SAF tree for the given path.
 *   Parent should launch the tree picker (with optional initial Uri) and persist permission.
 * - If SAF prevents deletion and cannot be resolved, we show a clear message and broadcast EXTRA_DELETE_MANUAL_REMIND=true
 *
 * Use this component in file list rows. Keep parent state update (onFileDeleted) to remove items locally.
 */
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

    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // SAF launcher to request access to a tree (fallback if parent didn't handle onGrantClick)
    val pickTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Permission not granted. Cannot delete ${file.name}", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        // Persist permission if possible
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Throwable) {
            // ignore vendor quirks
        }

        // After permission is granted, attempt deletion again automatically
        coroutineScope.launch {
            val res = attemptDeleteWithFallbacks(context, file)
            when (res) {
                is DeletionResult.Deleted -> {
                    withContext(Dispatchers.Main) {
                        // Notify parent and broadcast
                        onFileDeleted(file)
                        sendDeletedBroadcast(context, file.path)
                        Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                is DeletionResult.NeedUserGrant -> {
                    // Still needs grant - inform user
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                }
                is DeletionResult.Failed -> {
                    // Could not delete even after grant - it's likely a manual-delete case
                    withContext(Dispatchers.Main) {
                        // In this case we inform parent (via broadcast) that manual delete is required.
                        notifyManualDeleteRequired(context, file.path)
                    }
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
                    val activity = context.findActivity()
                    ViewerRouter.openFile(activity ?: context, file, fromVault = false)
                }
            )

            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    expanded = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val success = ZipUtils.shareSingleFile(context, file)
                            if (!success) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
            )

            DropdownMenuItem(
                text = { Text("Zip & Share") },
                onClick = {
                    expanded = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val zipFile = ZipUtils.createZip(context, file.name.substringBeforeLast('.'), listOf(file))
                            withContext(Dispatchers.Main) {
                                if (zipFile != null) {
                                    ZipUtils.shareZip(context, zipFile)
                                } else {
                                    Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Zipping failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
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

    // Confirmation dialog for delete
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
                                // Ask the parent to request SAF (preferred) else open picker ourselves.
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                                // If parent provided a handler, ask it to request access. Pass a suggested initialUri if available.
                                if (onGrantClick != null) {
                                    onGrantClick(res.suggestedUriToOpen?.toString())
                                } else {
                                    // fallback: open picker ourselves
                                    try {
                                        pickTreeLauncher.launch(res.suggestedUriToOpen)
                                    } catch (_: Throwable) {
                                        pickTreeLauncher.launch(null)
                                    }
                                }
                            }
                            is DeletionResult.Failed -> {
                                // Deletion failed — likely because SAF can't help; ask user to manually delete and broadcast the hint.
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
}

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

/** Local sealed result for this file */
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
        try {
            // MediaStore first for user-visible media
            if (isLikelyMediaFile(file.path)) {
                val mediaDeleted = try { attemptMediaStoreDelete(context, file.path) } catch (_: Throwable) { false }
                if (mediaDeleted) return@withContext DeletionResult.Deleted
            }

            // Direct file delete
            val f = File(file.path)
            if (f.exists()) {
                val directOk = try { f.delete() } catch (_: Throwable) { false }
                if (directOk) return@withContext DeletionResult.Deleted
            }

            // SAF / persisted tree URIs via DeletionManager
            when (val dm = DeletionManager.deleteFile(context, file)) {
                is DeletionManager.DeleteResult.Deleted -> return@withContext DeletionResult.Deleted
                is DeletionManager.DeleteResult.NeedUserGrant ->
                    return@withContext DeletionResult.NeedUserGrant(dm.suggestedUriToOpen, dm.message)
                is DeletionManager.DeleteResult.Failed ->
                    return@withContext DeletionResult.Failed(dm.reason)
            }
        } catch (ex: Exception) {
            return@withContext DeletionResult.Failed(ex.localizedMessage ?: ex.toString())
        }
        return@withContext DeletionResult.Failed("Unknown error while deleting")
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
    return try {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
        val sel = "${MediaStore.MediaColumns.DATA} = ?"
        cursor = cr.query(collection, projection, sel, arrayOf(absolutePath), null)
        if (cursor != null && cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val id = cursor.getLong(idIndex)
            val uri = ContentUris.withAppendedId(collection, id)
            try {
                val rows = cr.delete(uri, null, null)
                return rows > 0
            } catch (se: SecurityException) {
                return false
            }
        }
        false
    } catch (_: Exception) {
        false
    } finally {
        try { cursor?.close() } catch (_: Exception) {}
    }
}
