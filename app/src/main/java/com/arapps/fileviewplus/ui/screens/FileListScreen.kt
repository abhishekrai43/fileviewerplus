// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FileListScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.intent.IntentActions
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.AudioMiniPlayer
import com.arapps.fileviewplus.ui.components.FileActionsMenu
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File
import androidx.compose.ui.platform.LocalContext
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.logic.StorageStats

/**
 * FileListScreen
 *
 * Production-ready:
 *  - Accepts top-level Day model (Category/Year/Month/Day).
 *  - Maintains local mutableStateList for fast UI updates.
 *  - Listens to ACTION_FILE_DELETED broadcasts and removes stale rows.
 *  - Provides SAF picker retry support for deletions that require user-grant.
 *  - Notifies parent (onBack) when list becomes empty to allow aggregates to refresh.
 */
@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(day: FileNode.Day, onBack: () -> Unit) {
    val context = LocalContext.current

    // SAF launcher: used when deletion flow requires user to pick a folder (SAF)
    var pendingSafPath by remember { mutableStateOf<String?>(null) }
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri == null) {
            Toast.makeText(context, "Folder selection cancelled", Toast.LENGTH_SHORT).show()
            pendingSafPath = null
            return@rememberLauncherForActivityResult
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            Toast.makeText(context, "Access granted — retrying deletion", Toast.LENGTH_SHORT).show()
            // When SAF granted, broadcast an internal intent so deletion manager / caller can retry.
            // Many delete implementations will reattempt when they see pending state; if not, UI can instruct user.
            pendingSafPath?.let { path ->
                // send a platform broadcast asking deletion reattempt (optional pattern)
                val retryIntent = Intent(IntentActions.ACTION_REQUEST_DELETE_RETRY).apply {
                    putExtra(IntentActions.EXTRA_DELETED_PATH, path)
                }
                context.sendBroadcast(retryIntent)
            }
        } catch (t: Throwable) {
            Toast.makeText(context, "Failed to persist permission: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            pendingSafPath = null
        }
    }

    // Mutable UI list (keeps local quick updates). Backed by day.files initial snapshot.
    val files = remember { mutableStateListOf<FileNode>().apply { addAll(day.files) } }

    // Register broadcast receiver to keep this screen in sync with app-wide deletes.
    DisposableEffect(Unit) {
        val filter = IntentFilter(IntentActions.ACTION_FILE_DELETED)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(IntentActions.EXTRA_DELETED_PATH) ?: return
                val normalizedDeleted = try {
                    File(path).canonicalPath
                } catch (_: Exception) {
                    File(path).absolutePath
                }
                mainHandler.post {
                    val removed = files.firstOrNull { node ->
                        val nodePathNormalized = try {
                            File(node.path).canonicalPath
                        } catch (_: Exception) {
                            File(node.path).absolutePath
                        }
                        nodePathNormalized == normalizedDeleted
                    }
                    if (removed != null) {
                        files.remove(removed)
                        Toast.makeText(context, "Deleted ${removed.name}", Toast.LENGTH_SHORT).show()
                        if (files.isEmpty()) onBack()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // Top-level scaffold & UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Files: ${day.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        // Active inline audio player state for this screen (defined so clicks can set activeAudio)
        val activeAudio = remember { mutableStateOf<FileNode?>(null) }

        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            if (files.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(text = "No files", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "All files in this day have been deleted or moved.", style = MaterialTheme.typography.bodyMedium)
                }
                return@Surface
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(items = files, key = { it.path }) { file ->
                    val activity = context.findActivity()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                // Verify existence before opening viewer or inline player
                                val f = File(file.path)
                                if (!f.exists()) {
                                    files.removeAll { it.path == file.path }
                                    Toast.makeText(context, "${file.name} not found; removed", Toast.LENGTH_SHORT).show()
                                    if (files.isEmpty()) onBack()
                                    return@clickable
                                }
                                // If audio, open inline player instead of full-screen viewer
                                if (isAudioFile(file)) {
                                    activeAudio.value = file
                                } else {
                                    ViewerRouter.openFile(activity ?: context, file, fromVault = false)
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            // Thumbnail (safe: pass java.io.File to thumbnail component)
                            FilePreviewThumbnail(file = File(file.path))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = StorageStats.formatSize(file.size),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            // Show music-note play icon for audio files (small circular background)
                            if (isAudioFile(file)) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(44.dp)
                                        .background(Color.Black.copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(onClick = { activeAudio.value = file }, modifier = Modifier.size(36.dp)) {
                                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            // Actions menu: delete/share/rename. FileActionsMenu must call onFileDeleted when deletion succeeded.
                            FileActionsMenu(
                                file = file,
                                onFileDeleted = { deleted ->
                                    // Keep UI consistent immediately (normalize path)
                                    val normalizedDeleted = try {
                                        File(deleted.path).canonicalPath
                                    } catch (_: Exception) {
                                        File(deleted.path).absolutePath
                                    }
                                    files.removeAll { node ->
                                        val nodePathNormalized = try {
                                            File(node.path).canonicalPath
                                        } catch (_: Exception) {
                                            File(node.path).absolutePath
                                        }
                                        nodePathNormalized == normalizedDeleted
                                    }
                                    Toast.makeText(context, "Deleted ${deleted.name}", Toast.LENGTH_SHORT).show()
                                    if (files.isEmpty()) onBack()
                                },
                                onGrantClick = { pathNeedingGrant ->
                                    // Remember path and launch SAF picker so DeletionManager can succeed on retry
                                    pendingSafPath = pathNeedingGrant
                                    safLauncher.launch(null)
                                }
                            )
                        }
                    }
                }
            }

            // Inline mini player rendered as centered overlay when an audio file is active
            activeAudio.value?.let { node ->
                AudioMiniPlayer(fileNode = node, autoPlay = true, overlay = true, onClose = { activeAudio.value = null })
            }
        }
    }
}

private fun isAudioFile(file: FileNode): Boolean {
    try {
        if (file.type == FileNode.FileType.Audio) return true
        val ext = file.extension
        if (ext.isNotBlank()) {
            val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "amr", "opus", "wma")
            if (audioExts.contains(ext)) return true
        }
        val name = file.name
        if (name.endsWith(".mp3", true) || name.endsWith(".wav", true)) return true
    } catch (_: Exception) {}
    return false
}

/**
 * Helper to find Activity from a Context (keeps parity with the rest of the project).
 * If you already have a similar helper in your utils, you can remove this duplicate.
 */
private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
