// File: app/src/main/java/com/arapps/fileviewplus/ui/screens/FileTypeExplorerScreen.kt
package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.arapps.fileviewplus.viewer.ViewerRouter
import java.io.File

/**
 * FileTypeExplorerScreen (production-ready)
 *
 * - Does NOT show any "Restricted folder" banner by default.
 * - Only requests SAF (document tree) when the user attempts to open/grant access to a protected file.
 * - Keeps a local mutable `displayFiles` list derived from the 'categories' parameter and listens
 *   for ACTION_FILE_DELETED broadcasts. When a deletion event arrives we remove the matching file
 *   from display immediately (so UI/counts update instantly).
 *
 * Notes:
 * - This file is defensive: it will avoid showing stale rows when a file has been removed on-disk.
 * - Keep the deletion broadcast contract: any component that deletes a file should broadcast
 *   ACTION_FILE_DELETED with EXTRA_DELETED_PATH (string).
 */
@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileTypeExplorerScreen(
    categories: List<FileNode.Category>
) {
    val context = LocalContext.current

    // Launcher parent owns for SAF tree requests when user taps a protected file
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

    // Flatten the incoming categories -> file list (this is the canonical source passed from parent)
    val canonicalFiles = remember(categories) {
        categories
            .flatMap { it.years }
            .flatMap { it.months }
            .flatMap { it.days }
            .flatMap { it.files }
    }

    // Local display list (mutable state). We initialize from canonicalFiles; this lets us remove rows
    // instantly on delete broadcast even if top-level model is lagging for any reason.
    val displayFiles = remember { mutableStateListOf<FileNode>() }
    LaunchedEffect(canonicalFiles) {
        displayFiles.clear()
        displayFiles.addAll(canonicalFiles)
    }

    // Listen for deletion broadcasts so we can remove rows immediately.
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                // Remove any matching file(s) from display
                val removed = displayFiles.removeAll { it.path == path }
                if (removed) {
                    // Optional: brief toast to indicate UI updated
                    Toast.makeText(context, "Removed deleted file from view", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // --- Filtering / grouping state ---
    var selectedType by remember { mutableStateOf<FileCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGrouped = remember(displayFiles, selectedType, searchQuery) {
        displayFiles.filter { file ->
            val matchesType = selectedType == null || getFileCategory(file.name) == selectedType
            val matchesQuery = file.name.contains(searchQuery, ignoreCase = true)
            matchesType && matchesQuery
        }.groupBy { getFileCategory(it.name) }
    }

    // --- UI ---
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files...") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FileCategory.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(type.label, maxLines = 1) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Grouped list; each category header shows only when there are items
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filteredGrouped.isEmpty()) {
                    item {
                        Text("No files found", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    filteredGrouped.forEach { (type, files) ->
                        if (files.isNotEmpty()) {
                            item {
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }

                            items(files, key = { it.path }) { file ->
                                val isProtected = remember(file.path) { SafUtils.isSafProtected(file) }

                                FileRow(
                                    file = file,
                                    category = type,
                                    isProtected = isProtected,
                                    onGrantClick = {
                                        // Only ask for SAF when user explicitly taps a protected file
                                        // We launch the tree picker; if you want to preselect a subfolder you can pass a Uri string.
                                        grantLauncher.launch(Uri.parse(file.path.substringBeforeLast('/')))
                                    },
                                    onOpenClick = {
                                        val activity = context.findActivity()
                                        // Guard against missing files on disk
                                        val f = File(file.path)
                                        if (!f.exists()) {
                                            // Remove from UI immediately
                                            displayFiles.removeAll { it.path == file.path }
                                            Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
                                            return@FileRow
                                        }
                                        ViewerRouter.openFile(activity ?: context, file, fromVault = false)
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

@Composable
private fun FileRow(
    file: FileNode,
    category: FileCategory,
    isProtected: Boolean,
    onGrantClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isProtected) onGrantClick() else onOpenClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Thumbnail(file = file, category = category)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1
                )

                Text(
                    File(file.path).absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )

                Text(
                    Formatter.formatFileSize(LocalContext.current, file.size),
                    style = MaterialTheme.typography.labelSmall
                )

                if (isProtected) {
                    Text(
                        "Protected. Tap to grant access.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (isProtected) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Protected",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(file: FileNode, category: FileCategory) {
    val path = file.path
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current)
            .data(File(path))
            .crossfade(true)
            .build()
    )

    when (category) {
        FileCategory.IMAGE, FileCategory.VIDEO -> {
            Image(
                painter = painter,
                contentDescription = file.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        FileCategory.AUDIO -> {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Audio",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        FileCategory.DOCUMENT -> {
            Icon(
                imageVector = when {
                    file.name.endsWith(".pdf", true) -> Icons.Default.PictureAsPdf
                    else -> Icons.Default.Description
                },
                contentDescription = "Document",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        FileCategory.OTHER -> {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = "File",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun getFileCategory(name: String): FileCategory {
    val lower = name.lowercase()
    return when {
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp").any { lower.endsWith(it) } -> FileCategory.IMAGE
        listOf(".mp4", ".mkv", ".avi", ".mov").any { lower.endsWith(it) } -> FileCategory.VIDEO
        listOf(".mp3", ".wav", ".ogg", ".m4a").any { lower.endsWith(it) } -> FileCategory.AUDIO
        listOf(".pdf", ".txt", ".doc", ".docx", ".ppt", ".pptx").any { lower.endsWith(it) } -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }
}

private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}


