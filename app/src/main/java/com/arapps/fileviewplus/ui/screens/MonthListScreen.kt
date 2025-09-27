package com.arapps.fileviewplus.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arapps.fileviewplus.ui.components.FilePreviewThumbnail
import com.arapps.fileviewplus.viewer.ViewerRouter
import com.arapps.fileviewplus.logic.StorageStats
import com.arapps.fileviewplus.model.FileNode
import com.arapps.fileviewplus.ui.components.FolderActionsMenu
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

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MonthListScreen(
    year: FileNode.Year,
    onSelect: (FileNode.Month) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFlatFiles by rememberSaveable { mutableStateOf(false) }

    // Mutable state so deletions trigger recomposition
    val months = remember { mutableStateListOf<FileNode.Month>().apply { addAll(year.months) } }
    val allMonthFiles = remember { mutableStateListOf<FileNode>().apply { addAll(year.months.flatMap { it.days.flatMap { d -> d.files } }) } }

    // Multi-select state
    val selected = remember { mutableStateListOf<String>() }
    var showSelectionToolbar by remember { mutableStateOf(false) }
    var showEnterPin by remember { mutableStateOf(false) }
    var showChooseVaultFolder by remember { mutableStateOf(false) }
    // single remembered coroutine scope for UI actions
    val uiScope = rememberCoroutineScope()
    val vaultRoot = File(context.filesDir, ".vault").apply { mkdirs() }

    // helper to attempt delete originals after copy
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
                            android.widget.Toast.makeText(context, "Cannot delete automatically: permission needed for $p", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    is DeletionManager.DeleteResult.Failed -> {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Delete failed: ${res.reason}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Helper to recompute flat list from months (call after months change)
    fun rebuildFlatFilesFromMonths(mList: List<FileNode.Month>) {
        val newFlat = mList.flatMap { m -> m.days.flatMap { d -> d.files } }
        allMonthFiles.clear()
        allMonthFiles.addAll(newFlat)
    }

    // Listen for delete broadcasts
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_FILE_DELETED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val path = intent?.getStringExtra(EXTRA_DELETED_PATH) ?: return
                Log.d("MonthListScreen", "Received delete broadcast for: $path")

                // Remove from flat file list if present
                val removed = allMonthFiles.firstOrNull { it.path == path }
                if (removed != null) {
                    allMonthFiles.remove(removed)
                }

                // Walk months -> days -> files and remove the file, dropping empty days and months
                val updatedMonths = months.mapNotNull { month ->
                    // For each day in month, remove the file and keep only non-empty days
                    val updatedDays = month.days.mapNotNull { day ->
                        val newFiles = day.files.filterNot { it.path == path }
                        if (newFiles.isNotEmpty()) day.copy(files = newFiles) else null
                    }
                    // Keep month only if it still has days
                    if (updatedDays.isNotEmpty()) month.copy(days = updatedDays) else null
                }

                // Apply new months -> this will trigger recomposition
                months.clear()
                months.addAll(updatedMonths)

                // Rebuild flat list from updated months (keeps counts consistent)
                rebuildFlatFilesFromMonths(updatedMonths)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            if (showSelectionToolbar && selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected.clear(); showSelectionToolbar = false }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection") } },
                    actions = {
                        IconButton(onClick = { /* share - similar to DayListScreen */ }) { Icon(imageVector = Icons.Filled.Share, contentDescription = "Share") }
                        IconButton(onClick = { showEnterPin = true }) { Icon(imageVector = Icons.Filled.Lock, contentDescription = "Move to Vault") }
                        IconButton(onClick = {
                            uiScope.launch {
                                attemptDeleteOriginals()
                                selected.clear(); showSelectionToolbar = false
                            }
                        }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("📁 ${year.name}") },
                    navigationIcon = { IconButton(onClick = onBack) { Text("⬅") } },
                    actions = {
                        IconButton(onClick = { showFlatFiles = !showFlatFiles }) {
                            Icon(
                                imageVector = if (showFlatFiles) Icons.Filled.Folder else Icons.AutoMirrored.Filled.List,
                                contentDescription = "Toggle view"
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!showFlatFiles) {
                items(months.sortedByDescending { it.name }, key = { it.name }) { month ->
                    val allFiles = month.days.flatMap { it.files }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onSelect(month) }
                            .combinedClickable(onClick = { onSelect(month) }, onLongClick = {
                                selected.clear()
                                selected.addAll(month.days.flatMap { it.files }.map { it.path })
                                showSelectionToolbar = true
                            }),
                        elevation = CardDefaults.cardElevation(3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📁 ${month.name}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val fileCount = allFiles.size
                            val sizeText = StorageStats.formatSize(allFiles.sumOf { it.size })

                            Text(
                                text = "$fileCount files",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Box(modifier = Modifier.align(Alignment.End)) {
                                FolderActionsMenu(folderName = month.name, files = allFiles)
                            }
                        }
                    }
                }
            } else {
                items(allMonthFiles, key = { it.path }) { file ->
                    val isSelected = selected.contains(file.path)
                    // Compose a single modifier and apply conditional border when selected
                    val baseModifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable {
                            if (selected.isNotEmpty()) {
                                if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                showSelectionToolbar = selected.isNotEmpty()
                            } else {
                                ViewerRouter.openFile(context, file, fromVault = false)
                            }
                        }
                        .combinedClickable(onClick = {
                            if (selected.isNotEmpty()) {
                                if (isSelected) selected.remove(file.path) else selected.add(file.path)
                                showSelectionToolbar = selected.isNotEmpty()
                            } else {
                                ViewerRouter.openFile(context, file, fromVault = false)
                            }
                        }, onLongClick = {
                            if (!isSelected) selected.add(file.path)
                            showSelectionToolbar = true
                        })

                    val cardModifier = if (isSelected) baseModifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = MaterialTheme.shapes.medium) else baseModifier

                    Card(
                        modifier = cardModifier,
                        elevation = CardDefaults.cardElevation(3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                         Box(modifier = Modifier.fillMaxSize()) {
                            if (isSelected) {
                                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)))
                            }

                            FilePreviewThumbnail(
                                file = File(file.path),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )

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
                                        text = "${file.size / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
        // (Selection toolbar moved to topBar for visibility)

        if (showEnterPin) {
            EnterPinDialog(onPinEntered = { pin -> if (pin == getStoredPin(context)) { showEnterPin = false; showChooseVaultFolder = true } else android.widget.Toast.makeText(context, "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show() }, onDismiss = { showEnterPin = false }, onForgotPin = {})
        }

        if (showChooseVaultFolder) {
            // simple choose folder dialog, similar to DayListScreen
            val folders = vaultRoot.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            var selectedFolder by remember { mutableStateOf(folders.firstOrNull() ?: "") }
            AlertDialog(onDismissRequest = { showChooseVaultFolder = false }, title = { Text("Select Vault Folder") }, text = {
                Column { folders.forEach { name -> Row(modifier = Modifier.fillMaxWidth().clickable { selectedFolder = name }.padding(8.dp)) { RadioButton(selected = selectedFolder == name, onClick = { selectedFolder = name }); Spacer(Modifier.width(8.dp)); Text(name) } } }
            }, confirmButton = {
                TextButton(onClick = {
                    val dest = if (selectedFolder.isBlank()) vaultRoot else File(vaultRoot, selectedFolder)
                    uiScope.launch {
                        val copied = copyFilesToVaultAsync(context, selected.toList(), dest)
                        withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "${copied.size} file(s) copied to vault", android.widget.Toast.LENGTH_SHORT).show() }
                        // after copy ask to delete originals
                        attemptDeleteOriginals()
                        selected.clear(); showSelectionToolbar = false; showChooseVaultFolder = false
                    }
                }) { Text("Move") }
            }, dismissButton = { TextButton(onClick = { showChooseVaultFolder = false }) { Text("Cancel") } })
        }
     }
 }
