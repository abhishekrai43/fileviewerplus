package com.arapps.fileviewplus.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import com.arapps.fileviewplus.ui.components.AudioMiniPlayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
import com.arapps.fileviewplus.viewer.ViewerRouter
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.utils.getStoredPin
import com.arapps.fileviewplus.utils.copyFilesToVaultAsync
import com.arapps.fileviewplus.utils.DeletionManager
import com.arapps.fileviewplus.ui.components.vault.EnterPinDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.arapps.fileviewplus.intent.IntentActions.ACTION_FILE_DELETED
import com.arapps.fileviewplus.intent.IntentActions.EXTRA_DELETED_PATH
import androidx.compose.foundation.ExperimentalFoundationApi
import com.arapps.fileviewplus.logic.StorageStats


@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DayListScreen(
    month: FileNode.Month,
    onSelect: (FileNode.Day) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }

    // Keep mutable state lists so recomposition happens on delete
    val days = remember { mutableStateListOf<FileNode.Day>().apply { addAll(month.days) } }
    val allDayFiles =
        remember { mutableStateListOf<FileNode>().apply { addAll(month.days.flatMap { it.files }) } }

    // Multi-select state
    val selected = remember { mutableStateListOf<String>() }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showEnterPin by remember { mutableStateOf(false) }
    var showChooseVaultFolder by remember { mutableStateOf(false) }
    var chosenVaultFolder by remember { mutableStateOf<File?>(null) }

    // single remembered coroutine scope for UI actions
    val uiScope = rememberCoroutineScope()

    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }

    // helper to attempt delete originals after copy (now suspend so callers can invoke from coroutines)
    suspend fun attemptDeleteOriginals() {
        selected.toList().forEach { p ->
            try {
                val node = FileNode.fromFile(File(p))
                when (val res = DeletionManager.deleteFile(context, node)) {
                    DeletionManager.DeleteResult.Deleted -> {
                        // removed via broadcast elsewhere
                    }
                    is DeletionManager.DeleteResult.NeedUserGrant -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Cannot delete automatically: permission needed for $p", Toast.LENGTH_LONG).show()
                        }
                    }
                    is DeletionManager.DeleteResult.Failed -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Delete failed: ${res.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // NOTE: launching the full ImageViewerActivity on non-audio file clicks; audio opens inline player

    // Listen for file deletion broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                // Remove from flat list
                val removedFile = allDayFiles.firstOrNull { it.path == path }
                if (removedFile != null) {
                    allDayFiles.remove(removedFile)
                }
                // Remove from days list (recompute each day.files minus deleted file)
                val updated = days.map { d ->
                    d.copy(files = d.files.filterNot { it.path == path })
                }
                days.clear()
                days.addAll(updated)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Scaffold(
        topBar = {
            if (showSelectionToolbar && selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected.clear(); showSelectionToolbar = false }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection") } },
                    actions = {
                        IconButton(onClick = {
                            // Share selected
                            val uris = selected.mapNotNull { p ->
                                try { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", File(p)) } catch (_: Exception) { null }
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uris.first())
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share files"))
                            }
                        }) { Icon(imageVector = Icons.Filled.Share, contentDescription = "Share") }

                        IconButton(onClick = { showEnterPin = true }) { Icon(imageVector = Icons.Filled.Lock, contentDescription = "Move to Vault") }

                        IconButton(onClick = {
                            uiScope.launch {
                                selected.toList().forEach { p ->
                                    try {
                                        val node = FileNode.fromFile(File(p))
                                        DeletionManager.deleteFile(context, node)
                                    } catch (_: Exception) {}
                                }
                                // clear selection and refresh
                                selected.clear(); showSelectionToolbar = false
                            }
                        }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = month.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                Text(
                                    text = "${allDayFiles.size} files",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Premium segmented-style toggle using FilterChips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            FilterChip(
                                selected = !showFlatFiles,
                                onClick = { showFlatFiles = false },
                                label = { Text("Grouped") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )

                            FilterChip(
                                selected = showFlatFiles,
                                onClick = { showFlatFiles = true },
                                label = { Text("All Files") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->

        // Active inline audio player for this screen
        val activeAudio = remember { mutableStateOf<FileNode?>(null) }

        // Premium empty state
        if (days.all { it.files.isEmpty() } && allDayFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding), contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No files found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Files will appear here when available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        // Animate content change between grouped and flat views
        Crossfade(
            targetState = showFlatFiles,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        ) { flat ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .animateContentSize(animationSpec = tween(350)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!flat) {
                    items(days.sortedByDescending { it.name }, key = { it.name }) { day ->
                        val allFiles = day.files

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .combinedClickable(onClick = { onSelect(day) }, onLongClick = {
                                    // Select all files of this day into selection
                                    val paths = day.files.map { it.path }
                                    selected.clear()
                                    selected.addAll(paths)
                                    showSelectionToolbar = true
                                })
                                .animateContentSize(tween(300, easing = FastOutSlowInEasing)),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = day.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        tonalElevation = 2.dp
                                    ) {
                                        Text(
                                            text = "${allFiles.size} files",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            )
                                        )
                                    }
                                    FolderActionsMenu(folderName = day.name, files = allFiles)
                                }
                            }
                        }
                    }
                } else {
                    items(allDayFiles, key = { it.path }) { file ->
                        val isSelected = selected.contains(file.path)
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .combinedClickable(onClick = {
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                        showSelectionToolbar = selected.isNotEmpty()
                                    } else {
                                        if (!isAudioFile(file)) ViewerRouter.openFile(context, file, fromVault = false)
                                    }
                                }, onLongClick = {
                                    // start selection mode
                                    if (!isSelected) selected.add(file.path)
                                    showSelectionToolbar = true
                                }),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            // show selection overlay
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isSelected) {
                                    Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)))
                                }
                                 // rounded cropped thumbnail
                                 FilePreviewThumbnail(
                                     file = File(file.path),
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .height(160.dp)
                                         .clip(RoundedCornerShape(12.dp))
                                 )

                                // gradient overlay + caption
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                                ),
                                            )
                                    )

                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = StorageStats.formatSize(file.size),
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                                        )
                                    }
                                }

                                // If this file looks like audio, show a music-note icon to open inline player
                                if (isAudioFile(file)) {
                                    // Make the music icon clearly visible by placing it inside a semi-opaque circular background
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(44.dp)
                                            .background(Color.Black.copy(alpha = 0.45f), shape = androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = { activeAudio.value = file }, modifier = Modifier.size(36.dp)) {
                                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = "Play", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
            // Inline mini player rendered when an audio file is active
            activeAudio.value?.let { node ->
                AudioMiniPlayer(fileNode = node, autoPlay = true, onClose = { activeAudio.value = null })
            }
         }

         // Enter PIN dialog flow for Move to Vault
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
             // simple dialog to choose vault folder
             val folders = vaultRoot.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
             var selectedFolder by remember { mutableStateOf(folders.firstOrNull() ?: "") }
             AlertDialog(onDismissRequest = { showChooseVaultFolder = false }, title = { Text("Select Vault Folder") }, text = {
                 Column {
                     folders.forEach { name ->
                         Row(modifier = Modifier.fillMaxWidth().clickable { selectedFolder = name }.padding(8.dp)) {
                             RadioButton(selected = selectedFolder == name, onClick = { selectedFolder = name })
                             Spacer(Modifier.width(8.dp))
                             Text(name)
                         }
                     }
                 }
             }, confirmButton = {
                 TextButton(onClick = {
                     chosenVaultFolder = if (selectedFolder.isBlank()) vaultRoot else File(vaultRoot, selectedFolder)
                     // perform copy
                     uiScope.launch {
                         val copied = copyFilesToVaultAsync(context, selected.toList(), chosenVaultFolder ?: vaultRoot)
                         Toast.makeText(context, "${copied.size} file(s) copied to vault", Toast.LENGTH_SHORT).show()
                         // after copy ask to delete originals
                         attemptDeleteOriginals()
                         selected.clear(); showSelectionToolbar = false; showChooseVaultFolder = false
                     }
                 }) { Text("Move") }
             }, dismissButton = { TextButton(onClick = { showChooseVaultFolder = false }) { Text("Cancel") } })
         }
     }
 }

 private fun isAudioFile(file: FileNode): Boolean {
     // Robust check using FileNode.FileType and extension. Fall back to name checks if needed.
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
